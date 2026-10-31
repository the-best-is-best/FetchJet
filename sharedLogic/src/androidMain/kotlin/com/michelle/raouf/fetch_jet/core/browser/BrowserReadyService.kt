package com.michelle.raouf.fetch_jet.core.browser

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

/**
 * "Keep ready for the browser": a quiet foreground service whose only job is to stop Android
 * from freezing FetchJet in the background, so the Firefox extension can ask the app
 * (127.0.0.1:28450) for video qualities without the user opening it first. It holds no wake
 * lock: when the phone sleeps, nothing runs.
 */
class BrowserReadyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            BrowserIntegration.setKeepReadyForBrowser(false)
            return START_NOT_STICKY
        }
        createChannel()
        val turnOff = PendingIntent.getService(
            this, 0,
            Intent(this, BrowserReadyService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("FetchJet is ready for your browser")
            .setContentText("Video qualities show right in Firefox")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(0, "Turn off", turnOff)
            .build()
        val type =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (_: Exception) {
            // Started while the app was not allowed to (Android 12+ background start): give up quietly.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Browser extension",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shown while FetchJet stays ready for the browser extension"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "fetchjet_browser_ready"
        private const val NOTIFICATION_ID = 7302
        private const val ACTION_TURN_OFF = "com.michelle.raouf.fetch_jet.BROWSER_READY_OFF"

        fun start(context: Context) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, BrowserReadyService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BrowserReadyService::class.java))
        }
    }
}
