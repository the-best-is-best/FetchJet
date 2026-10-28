package com.michelle.raouf.fetch_jet.core.notification

expect class DownloadNotificationManager() {
    fun showOrUpdateNotification(
        taskId: String,
        fileName: String,
        progressPercent: Int,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    )

    fun cancelNotification(taskId: String)
}