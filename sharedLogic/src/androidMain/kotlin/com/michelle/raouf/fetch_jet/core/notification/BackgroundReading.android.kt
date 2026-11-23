package com.michelle.raouf.fetch_jet.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

actual object BackgroundReading {
    private val jobs = LinkedHashMap<String, Pair<String, String>>()

    private fun context(): Context? = DownloadNotificationManager.appContext ?: PathResolver.appContext

    actual fun begin(key: String, title: String, text: String) {
        val ctx = context() ?: return
        val first = synchronized(jobs) {
            val empty = jobs.isEmpty()
            jobs[key] = title to text
            empty
        }
        if (first) ReadingForegroundService.start(ctx) else ReadingForegroundService.refresh(ctx)
    }

    actual fun progress(key: String, text: String) {
        val ctx = context() ?: return
        val known = synchronized(jobs) {
            val job = jobs[key] ?: return@synchronized false
            jobs[key] = job.first to text
            true
        }
        if (known) ReadingForegroundService.refresh(ctx)
    }

    actual fun end(key: String) {
        val ctx = context() ?: return
        val last = synchronized(jobs) {
            if (jobs.remove(key) == null) return
            jobs.isEmpty()
        }
        if (last) ReadingForegroundService.stop(ctx) else ReadingForegroundService.refresh(ctx)
    }

    internal fun current(): Pair<String, String>? = synchronized(jobs) { jobs.values.lastOrNull() }
}

/**
 * Keeps FetchJet out of the system's freezer while it reads a playlist's videos: without it
 * Samsung froze the app and killed yt-dlp seconds after the user left it.
 */
class ReadingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (title, text) = BackgroundReading.current() ?: ("FetchJet" to "")
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build(this, title, text), type)
        } catch (_: Exception) {
            starting = false
            stopSelf()
            return START_NOT_STICKY
        }
        starting = false
        if (stopWanted || BackgroundReading.current() == null) {
            stopWanted = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "fetchjet_reading"
        private const val NOTIFICATION_ID = 7303

        @Volatile
        private var starting = false

        @Volatile
        private var stopWanted = false

        fun start(context: Context) {
            stopWanted = false
            starting = true
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, ReadingForegroundService::class.java)
                )
            } catch (_: Exception) {
                starting = false
            }
        }

        fun refresh(context: Context) {
            val (title, text) = BackgroundReading.current() ?: return
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, build(context, title, text))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            if (starting) {
                stopWanted = true
                return
            }
            try {
                context.stopService(Intent(context, ReadingForegroundService::class.java))
            } catch (_: Exception) {
            }
        }

        private fun build(context: Context, title: String, text: String): android.app.Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, LocalizationManager.Strings.sysReadingChannelName, NotificationManager.IMPORTANCE_LOW)
                            .apply { setShowBadge(false) }
                    )
                }
            }
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 3, it, PendingIntent.FLAG_IMMUTABLE)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
