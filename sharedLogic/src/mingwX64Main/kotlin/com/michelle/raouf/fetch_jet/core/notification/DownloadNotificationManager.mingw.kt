package com.michelle.raouf.fetch_jet.core.notification

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DesktopTaskProgress(
    val taskId: String,
    val fileName: String,
    val progressPercent: Int,
    val speedText: String
)

actual class DownloadNotificationManager actual constructor() {
    companion object {
        private val _activeTasks = MutableStateFlow<Map<String, DesktopTaskProgress>>(emptyMap())
        val activeTasks: StateFlow<Map<String, DesktopTaskProgress>> = _activeTasks.asStateFlow()
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
            val name = fileName.ifBlank { LocalizationManager.Strings.sysDownloadingFileFallback }
            _activeTasks.update {
                it + (taskId to DesktopTaskProgress(taskId, name, progressPercent.coerceIn(0, 100), speedText))
            }
        }
    }

    actual fun cancelNotification(taskId: String) {
        _activeTasks.update { it - taskId }
    }
}
