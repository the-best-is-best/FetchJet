package com.michelle.raouf.fetch_jet.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class LiveTaskItem(
    val taskId: String,
    val fileName: String,
    val progressPercent: Int,
    val speedText: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false
)

actual class DownloadNotificationManager actual constructor() {

    companion object {
        const val CHANNEL_ID = "fetchjet_downloads_channel"
        const val OLD_CHANNEL_ID = "fetch_jet_downloads_channel"
        const val CHANNEL_NAME = "FetchJet Downloads"
        const val FOREGROUND_NOTIFICATION_ID = 99999

        // Deterministic ordered list of task IDs: new tasks append to the end.
        // Positions NEVER change or shuffle during download progress!
        private val taskOrderList = CopyOnWriteArrayList<String>()
        private val taskDataMap = ConcurrentHashMap<String, LiveTaskItem>()

        private var lastUpdateTime = 0L

        @Volatile
        var appContext: Context? = null
            set(value) {
                val appCtx = value?.applicationContext
                field = appCtx
                if (appCtx != null) {
                    createNotificationChannel(appCtx)
                }
            }

        fun setContext(ctx: Context) {
            appContext = ctx.applicationContext
        }

        private fun getEffectiveContext(): Context? {
            val ctx = appContext ?: PathResolver.appContext ?: return null
            createNotificationChannel(ctx)
            return ctx
        }

        @Volatile
        private var channelCreated = false

        private fun createNotificationChannel(ctx: Context) {
            // Called on every progress tick: creating (and deleting the old) channel each time
            // is a pair of Binder calls to system_server per update. Do it once.
            if (channelCreated) return
            channelCreated = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return

                try {
                    notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)
                } catch (_: Throwable) {
                }

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows real-time Live Activity download updates"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun buildLiveActivityNotification(ctx: Context): Notification {
            createNotificationChannel(ctx)

            val launchIntent =
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            val flag = PendingIntent.FLAG_UPDATE_CURRENT or (
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    )
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(ctx, FOREGROUND_NOTIFICATION_ID, launchIntent, flag)
            } else null

            // Fallback for versions that don't support Live Activity (< Android 8.0)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setContentTitle("FetchJet Downloader")
                    .setContentText("التطبيق يعمل في الخلفية")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            }

            // Snapshot tasks in their strict, non-changing display order
            val tasksSnapshot = taskOrderList.mapNotNull { taskDataMap[it] }
            val activeTasks = tasksSnapshot.filter { !it.isCompleted }

            // Aggregate metrics
            var totalSpeedBps = 0.0
            var totalDownloaded = 0L
            var totalBytes = 0L
            for (t in tasksSnapshot) {
                if (!t.isCompleted) {
                    totalSpeedBps += parseSpeedToBps(t.speedText)
                }
                totalDownloaded += t.downloadedBytes
                totalBytes += t.totalBytes
            }
            val totalSpeedStr =
                if (totalSpeedBps > 0) "⚡ ${formatSpeed(totalSpeedBps.toLong())}" else "⚡ 0 B/s"
            val overallPercent = if (totalBytes > 0) {
                ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                val percents = tasksSnapshot.map { it.progressPercent }
                if (percents.isNotEmpty()) percents.sum() / percents.size else 0
            }

            val pkg = ctx.packageName
            val res = ctx.resources
            val collapsedLayoutId =
                res.getIdentifier("notification_live_activity_collapsed", "layout", pkg)
            val expandedLayoutId =
                res.getIdentifier("notification_live_activity_expanded", "layout", pkg)

            if (collapsedLayoutId != 0 && expandedLayoutId != 0) {
                try {
                    val collapsed = RemoteViews(pkg, collapsedLayoutId)
                    val expanded = RemoteViews(pkg, expandedLayoutId)

                    val topTask =
                        tasksSnapshot.firstOrNull { !it.isCompleted } ?: tasksSnapshot.firstOrNull()
                    val topTaskName = topTask?.fileName ?: "Downloads"

                    // --- Populate Collapsed View ---
                    val colTitleId = res.getIdentifier("live_collapsed_title", "id", pkg)
                    val colSpeedId = res.getIdentifier("live_collapsed_speed", "id", pkg)
                    val colProgressId = res.getIdentifier("live_collapsed_progress", "id", pkg)
                    val colSubtitleId = res.getIdentifier("live_collapsed_subtitle", "id", pkg)

                    collapsed.setTextViewText(
                        colTitleId,
                        if (activeTasks.size <= 1) topTaskName else "${activeTasks.size} files downloading"
                    )
                    collapsed.setTextViewText(colSpeedId, totalSpeedStr)
                    collapsed.setProgressBar(colProgressId, 100, overallPercent, false)
                    collapsed.setTextViewText(
                        colSubtitleId,
                        "$overallPercent% • ${activeTasks.size} active"
                    )

                    // --- Populate Expanded View ---
                    val expTitleId = res.getIdentifier("live_expanded_title", "id", pkg)
                    val expCountId = res.getIdentifier("live_expanded_count", "id", pkg)
                    val expSpeedId = res.getIdentifier("live_expanded_speed", "id", pkg)
                    val expOverallProgId = res.getIdentifier("live_overall_progress", "id", pkg)

                    expanded.setTextViewText(expTitleId, "FetchJet Live Activity")
                    expanded.setTextViewText(expCountId, "${activeTasks.size} active")
                    expanded.setTextViewText(expSpeedId, totalSpeedStr)
                    expanded.setProgressBar(expOverallProgId, 100, overallPercent, false)

                    // Render tasks in fixed order up to 3 slots
                    val maxSlots = 3
                    for (i in 1..maxSlots) {
                        val layoutId = res.getIdentifier("task${i}_layout", "id", pkg)
                        val nameId = res.getIdentifier("task${i}_name", "id", pkg)
                        val percentId = res.getIdentifier("task${i}_percent", "id", pkg)
                        val progId = res.getIdentifier("task${i}_progress", "id", pkg)
                        val detailsId = res.getIdentifier("task${i}_details", "id", pkg)

                        val taskItem = tasksSnapshot.getOrNull(i - 1)
                        if (taskItem != null) {
                            expanded.setViewVisibility(layoutId, View.VISIBLE)
                            expanded.setTextViewText(nameId, taskItem.fileName)
                            if (taskItem.isCompleted) {
                                expanded.setTextViewText(percentId, "✓ 100%")
                                expanded.setProgressBar(progId, 100, 100, false)
                                expanded.setTextViewText(
                                    detailsId,
                                    "Download complete • ${formatBytes(taskItem.totalBytes)}"
                                )
                            } else {
                                expanded.setTextViewText(percentId, "${taskItem.progressPercent}%")
                                expanded.setProgressBar(
                                    progId,
                                    100,
                                    taskItem.progressPercent.coerceIn(0, 100),
                                    false
                                )
                                val sizeStr = if (taskItem.totalBytes > 0) {
                                    "${formatBytes(taskItem.downloadedBytes)} / ${
                                        formatBytes(
                                            taskItem.totalBytes
                                        )
                                    } • ${taskItem.speedText}"
                                } else {
                                    "${formatBytes(taskItem.downloadedBytes)} • ${taskItem.speedText}"
                                }
                                expanded.setTextViewText(detailsId, sizeStr)
                            }
                        } else {
                            expanded.setViewVisibility(layoutId, View.GONE)
                        }
                    }

                    // More text
                    val moreTextId = res.getIdentifier("live_more_text", "id", pkg)
                    if (tasksSnapshot.size > maxSlots) {
                        expanded.setViewVisibility(moreTextId, View.VISIBLE)
                        expanded.setTextViewText(
                            moreTextId,
                            "+ ${tasksSnapshot.size - maxSlots} more downloads in queue"
                        )
                    } else {
                        expanded.setViewVisibility(moreTextId, View.GONE)
                    }

                    return NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setCustomContentView(collapsed)
                        .setCustomBigContentView(expanded)
                        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                } catch (_: Throwable) {
                    // RemoteViews inflation failed, fall back to BigTextStyle
                }
            }

            // BigTextStyle fallback: unified list in fixed order
            val summaryLines = tasksSnapshot.take(5).mapIndexed { idx, t ->
                val status =
                    if (t.isCompleted) "✓ Complete" else "${t.progressPercent}% (${t.speedText})"
                "${idx + 1}. ${t.fileName}: $status"
            }.joinToString("\n")

            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle("FetchJet Live Downloads ($totalSpeedStr)")
                .setContentText("${activeTasks.size} active downloads • $overallPercent%")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("FetchJet Live Downloads ($totalSpeedStr)")
                        .bigText(summaryLines)
                )
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun parseSpeedToBps(speedStr: String): Double {
            val clean = speedStr.trim().lowercase()
            val parts = clean.split(" ")
            val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
            val unit = parts.getOrNull(1) ?: ""
            return when {
                unit.startsWith("gb") -> num * 1024.0 * 1024.0 * 1024.0
                unit.startsWith("mb") -> num * 1024.0 * 1024.0
                unit.startsWith("kb") -> num * 1024.0
                else -> num
            }
        }

        private fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 B/s"
            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
            var value = bytesPerSec.toDouble()
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < units.size - 1) {
                value /= 1024.0
                unitIndex++
            }
            val formatted = (value * 10).toLong() / 10.0
            return "$formatted ${units[unitIndex]}"
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < units.size - 1) {
                value /= 1024.0
                unitIndex++
            }
            val formatted = (value * 10).toLong() / 10.0
            return "$formatted ${units[unitIndex]}"
        }
    }

    fun setContext(ctx: Context) {
        Companion.setContext(ctx)
    }

    @SuppressLint("MissingPermission")
    actual fun showOrUpdateNotification(
        taskId: String,
        fileName: String,
        progressPercent: Int,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val ctx = getEffectiveContext() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notificationManager = NotificationManagerCompat.from(ctx)
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        val isCompleted = progressPercent >= 100 || speedText.equals("Completed", ignoreCase = true)

        // Strict deterministic insertion: tasks added to the end and NEVER reordered!
        if (!taskOrderList.contains(taskId)) {
            taskOrderList.add(taskId)
        }

        taskDataMap[taskId] = LiveTaskItem(
            taskId = taskId,
            fileName = fileName.ifBlank { "Download Task" },
            progressPercent = progressPercent,
            speedText = speedText,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            isCompleted = isCompleted
        )

        val activeCount = taskOrderList.mapNotNull { taskDataMap[it] }.count { !it.isCompleted }

        // If all tasks are completed or list empty, stop foreground service and dismiss
        if (activeCount == 0) {
            taskOrderList.clear()
            taskDataMap.clear()
            DownloadForegroundService.stop(ctx)
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
            return
        }

        // Start Foreground Service if not already running
        if (!DownloadForegroundService.isRunning) {
            DownloadForegroundService.start(ctx)
        }

        // Throttle notification updates to at most once per 500ms
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 500L) {
            return
        }
        lastUpdateTime = now

        try {
            val notification = buildLiveActivityNotification(ctx)
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    actual fun cancelNotification(taskId: String) {
        val ctx = getEffectiveContext() ?: return
        taskOrderList.remove(taskId)
        taskDataMap.remove(taskId)

        val notificationManager = NotificationManagerCompat.from(ctx)
        val activeCount = taskOrderList.mapNotNull { taskDataMap[it] }.count { !it.isCompleted }

        if (activeCount == 0 || taskOrderList.isEmpty()) {
            taskOrderList.clear()
            taskDataMap.clear()
            DownloadForegroundService.stop(ctx)
            try {
                notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
            } catch (_: Throwable) {
            }
        } else {
            try {
                val notification = buildLiveActivityNotification(ctx)
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Throwable) {
            }
        }
    }
}