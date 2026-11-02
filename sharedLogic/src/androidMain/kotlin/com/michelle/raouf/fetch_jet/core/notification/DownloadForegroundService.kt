package com.michelle.raouf.fetch_jet.core.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.michelle.raouf.fetch_jet.core.i18n.start.start

class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        com.michelle.raouf.fetch_jet.core.filesystem.PathResolver.appContext = applicationContext
        com.tbib.kmp_localization.LocalizationManager.start()
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
            // LOW_LATENCY only takes effect while the app is in the foreground with the screen
            // on, so it did nothing for background downloads; HIGH_PERF keeps Wi-Fi at full
            // throughput (no power-save mode) while the service runs.
            @Suppress("DEPRECATION")
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
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
        awaitingForeground = false
        if (action == ACTION_STOP || stopRequested) {
            stopRequested = false
            stopForegroundService()
            return START_NOT_STICKY
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
        awaitingForeground = false
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

        @Volatile
        private var awaitingForeground = false

        @Volatile
        private var stopRequested = false

        fun start(context: Context) {
            stopRequested = false
            awaitingForeground = true
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
                awaitingForeground = false
            }
        }

        fun stop(context: Context) {
            isRunning = false
            if (awaitingForeground) {
                stopRequested = true
                return
            }
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