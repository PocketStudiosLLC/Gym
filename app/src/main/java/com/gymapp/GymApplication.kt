package com.gymapp

import android.app.Application
import android.content.Context
import com.gymapp.backup.BackupManager
import com.gymapp.data.FoodRepository
import com.gymapp.data.GymDatabase
import com.gymapp.data.GymRepository
import com.gymapp.data.PhotoStore
import com.gymapp.data.SettingsRepository
import com.gymapp.timer.StopwatchController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Simple manual dependency container (no DI framework needed at this size). */
class AppContainer(context: Context) {
    /** Lives as long as the process, so writes aren't cancelled when a screen closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val db = GymDatabase.create(context)
    val settings = SettingsRepository(context)
    val photos = PhotoStore(context)
    val stopwatch = StopwatchController(context, appScope)
    val repository = GymRepository(db) { date -> stopwatch.onSetLogged(date) }
    val food = FoodRepository(db, photos)
    val backup = BackupManager(context, db, photos, settings)

    /** Entry ids that are expanded on the Day screen; kept here so pager pages remember them. */
    val expandedEntries = MutableStateFlow<Set<Long>>(emptySet())

    fun expand(entryId: Long) {
        expandedEntries.value = expandedEntries.value + entryId
    }
}

class GymApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.stopwatch.restore()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as GymApplication).container
