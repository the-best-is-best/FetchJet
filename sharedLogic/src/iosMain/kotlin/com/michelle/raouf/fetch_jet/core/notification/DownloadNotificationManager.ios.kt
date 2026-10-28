package com.michelle.raouf.fetch_jet.core.notification

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

interface IosDownloadNotificationListener {
    fun onUpdateProgress(
        taskId: String,
        fileName: String,
        engineType: String,
        progress: Double,
        progressPercent: Int,
        speedText: String,
        downloadedBytesStr: String,
        totalBytesStr: String,
        statusText: String
    )

    /** [completed] = finished normally (keep "Completed" visible briefly) vs paused/removed. */
    fun onEnd(taskId: String, completed: Boolean)
}

actual class DownloadNotificationManager actual constructor() {

    companion object {
        var listener: IosDownloadNotificationListener? = null
    }

    init {
        requestAuthorization()
    }

    private fun requestAuthorization() {
        val options =
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { _, _ -> }
    }

    actual fun showOrUpdateNotification(
        taskId: String,
        fileName: String,
        progressPercent: Int,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val name = fileName.ifBlank { "Downloading File" }
        val downloadedStr = formatBytes(downloadedBytes)
        val totalStr = if (totalBytes > 0) formatBytes(totalBytes) else "Unknown"
        val statusText = if (progressPercent >= 100) "Completed" else "Downloading"

        // 1. Notify Live Activity (Dynamic Island & Lock Screen banner)
        listener?.onUpdateProgress(
            taskId = taskId,
            fileName = name,
            engineType = "FetchJet",
            progress = progressPercent / 100.0,
            progressPercent = progressPercent,
            speedText = speedText,
            downloadedBytesStr = downloadedStr,
            totalBytesStr = totalStr,
            statusText = statusText
        )

        // 2. Finished: the Live Activity must go away by itself (iOS never auto-dismisses it).
        if (progressPercent >= 100) {
            listener?.onEnd(taskId, completed = true)
        }
        // 3. Local system notification (Deliver with sound & alert when completed)
        if (progressPercent >= 100) {
            val content = UNMutableNotificationContent().apply {
                setTitle("Download Complete")
                setSubtitle(name)
                setBody("Finished downloading • $downloadedStr")
                setSound(UNNotificationSound.defaultSound())
            }

            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = "${taskId}_completed",
                content = content,
                trigger = null
            )

            UNUserNotificationCenter.currentNotificationCenter()
                .addNotificationRequest(request) { _ -> }
        }
    }

    actual fun cancelNotification(taskId: String) {
        listener?.onEnd(taskId, completed = false)
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(taskId, "${taskId}_completed"))
        UNUserNotificationCenter.currentNotificationCenter()
            .removeDeliveredNotificationsWithIdentifiers(listOf(taskId, "${taskId}_completed"))
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