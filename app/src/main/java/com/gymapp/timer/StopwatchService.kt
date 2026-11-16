package com.gymapp.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.gymapp.GymApplication
import com.gymapp.MainActivity
import com.gymapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Keeps the stopwatch alive in the background and shows it in an ongoing notification. */
class StopwatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val controller get() = (application as GymApplication).container.stopwatch

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workout stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        scope.launch {
            controller.state.drop(1).collect { s ->
                if (s == null) stopSelf() else showNotification(s)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> controller.stop()
        }
        val s = controller.state.value
        // startForeground must always be called after startForegroundService.
        showNotification(s ?: StopwatchState(System.currentTimeMillis(), 0, true))
        if (s == null && intent?.action != ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    private fun showNotification(s: StopwatchState) {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (s.paused) "Workout stopwatch · paused" else "Workout stopwatch")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (s.paused) {
            builder.setContentText(formatElapsed(s.elapsedMs()))
                .setUsesChronometer(false)
                .addAction(0, "Resume", action(ACTION_RESUME))
        } else {
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - s.elapsedMs())
                .setShowWhen(true)
                .addAction(0, "Pause", action(ACTION_PAUSE))
        }
        builder.addAction(0, "Stop", action(ACTION_STOP))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), type)
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, name.hashCode(),
        Intent(this, StopwatchService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "stopwatch"
        const val NOTIFICATION_ID = 1
        const val ACTION_REFRESH = "com.gymapp.timer.REFRESH"
        const val ACTION_PAUSE = "com.gymapp.timer.PAUSE"
        const val ACTION_RESUME = "com.gymapp.timer.RESUME"
        const val ACTION_STOP = "com.gymapp.timer.STOP"
    }
}

fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
