package com.gymapp.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** A running (or paused) stopwatch. Times are wall-clock millis so they survive process death. */
data class StopwatchState(
    val startedAt: Long,
    val accumulatedMs: Long,
    val paused: Boolean,
) {
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        accumulatedMs + if (paused) 0 else (now - startedAt).coerceAtLeast(0)
}

private val Context.timerStore by preferencesDataStore(name = "timer")

/**
 * Single source of truth for the workout stopwatch. The UI observes [state];
 * [StopwatchService] mirrors it in an ongoing notification.
 */
class StopwatchController(private val context: Context, private val scope: CoroutineScope) {
    private val runningKey = booleanPreferencesKey("running")
    private val startedKey = longPreferencesKey("started_at")
    private val accKey = longPreferencesKey("accumulated")
    private val pausedKey = booleanPreferencesKey("paused")
    private val autoDateKey = longPreferencesKey("auto_started_for")

    private val _state = MutableStateFlow<StopwatchState?>(null)
    val state: StateFlow<StopwatchState?> = _state.asStateFlow()

    private val mutex = Mutex()

    /** Restores a stopwatch that was running before the process died. */
    fun restore() {
        scope.launch {
            val p = context.timerStore.data.first()
            if (p[runningKey] == true) {
                _state.value = StopwatchState(
                    startedAt = p[startedKey] ?: System.currentTimeMillis(),
                    accumulatedMs = p[accKey] ?: 0,
                    paused = p[pausedKey] ?: false,
                )
                syncService()
            }
        }
    }

    fun start() = update { if (it == null) StopwatchState(System.currentTimeMillis(), 0, false) else it }

    fun pause() = update { s ->
        if (s == null || s.paused) s else StopwatchState(System.currentTimeMillis(), s.elapsedMs(), true)
    }

    fun resume() = update { s ->
        if (s == null || !s.paused) s else s.copy(startedAt = System.currentTimeMillis(), paused = false)
    }

    fun stop() = update { null }

    /** Starts the stopwatch the first time a set is logged on today's page (once per day). */
    suspend fun onSetLogged(date: LocalDate) {
        val today = LocalDate.now()
        if (date != today) return
        mutex.withLock {
            val p = context.timerStore.data.first()
            if (p[autoDateKey] == today.toEpochDay()) return
            context.timerStore.edit { it[autoDateKey] = today.toEpochDay() }
            if (_state.value == null) {
                val s = StopwatchState(System.currentTimeMillis(), 0, false)
                _state.value = s
                persist(s)
            }
        }
        syncService()
    }

    private fun update(transform: (StopwatchState?) -> StopwatchState?) {
        scope.launch {
            mutex.withLock {
                val next = transform(_state.value)
                _state.value = next
                persist(next)
            }
            syncService()
        }
    }

    private suspend fun persist(s: StopwatchState?) {
        context.timerStore.edit { p ->
            if (s == null) {
                p[runningKey] = false
                p.remove(startedKey)
                p.remove(accKey)
                p.remove(pausedKey)
            } else {
                p[runningKey] = true
                p[startedKey] = s.startedAt
                p[accKey] = s.accumulatedMs
                p[pausedKey] = s.paused
            }
        }
    }

    private fun syncService() {
        val intent = Intent(context, StopwatchService::class.java).setAction(StopwatchService.ACTION_REFRESH)
        if (_state.value != null) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            context.stopService(Intent(context, StopwatchService::class.java))
        }
    }
}
