package com.michelle.raouf.fetch_jet.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JvmTaskProgress(
    val taskId: String,
    val fileName: String,
    val progressPercent: Int,
    val speedText: String
)

actual class DownloadNotificationManager actual constructor() {

    companion object {
        private val _activeTasks = MutableStateFlow<Map<String, JvmTaskProgress>>(emptyMap())
        val activeTasks: StateFlow<Map<String, JvmTaskProgress>> = _activeTasks.asStateFlow()
    }

    actual fun showOrUpdateNotification(
        taskId: String,
        fileName: String,
        progressPercent: Int,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        if (progressPercent >= 100 || speedText.equals("Completed", ignoreCase = true)) {
            _activeTasks.update { it - taskId }
        } else {
            val name = fileName.ifBlank { "Downloading File" }
            _activeTasks.update {
                it + (taskId to JvmTaskProgress(
                    taskId,
                    name,
                    progressPercent.coerceIn(0, 100),
                    speedText
                ))
            }
        }
    }

    actual fun cancelNotification(taskId: String) {
        _activeTasks.update { it - taskId }
    }
}