package com.michelle.raouf.fetch_jet.core.notification

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        acquireLocks()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FetchJet::DownloadWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24-hour safety timeout
            }
        } catch (_: Throwable) {
        }

        try {
            val wifiManager =
                applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                },
                "FetchJet::WifiLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {
        }
        wakeLock = null

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {
        }
        wifiLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val notification = DownloadNotificationManager.buildLiveActivityNotification(this)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
            isRunning = true
        } catch (_: Throwable) {
        }

        return START_STICKY
    }

    private fun stopForegroundService() {
        isRunning = false
        releaseLocks()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        }
        stopSelf()
    }

    /**
     * Android 15+ caps a dataSync foreground service at ~6 h per day. When the budget is
     * used up the system calls this; if we do not stop within a few seconds the app is
     * killed with a ForegroundServiceDidNotStopInTimeException.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForegroundService()
    }

    override fun onDestroy() {
        isRunning = false
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID =
            DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID
        const val ACTION_START = "com.michelle.raouf.fetch_jet.action.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP = "com.michelle.raouf.fetch_jet.action.STOP_DOWNLOAD_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isRunning = true
            } catch (_: Throwable) {
            }
        }

        fun updateNotification(context: Context) {
            if (!isRunning) return
            try {
                val notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return
                val notification =
                    DownloadNotificationManager.buildLiveActivityNotification(context)
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            isRunning = false
            // startService(ACTION_STOP) throws IllegalStateException when the app is in the
            // background on API 26+, so the service (and its wake/wifi locks) never stopped.
            // stopService() is allowed from the background.
            try {
                context.stopService(Intent(context, DownloadForegroundService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}