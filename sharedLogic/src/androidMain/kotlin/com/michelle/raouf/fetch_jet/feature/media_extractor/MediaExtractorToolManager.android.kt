package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.deniscerri.ytdl.core.RuntimeManager
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun getMediaExtractorToolStatus(): String {
    val context =
        PathResolver.appContext ?: return LocalizationManager.Strings.sysToolsInstalledBuiltIn
    return try {
        val ver = RuntimeManager.versionName(context)
        if (!ver.isNullOrBlank()) LocalizationManager.Strings.sysYtDlpVersionReady(ver) else LocalizationManager.Strings.sysToolsInstalledBuiltIn
    } catch (_: Exception) {
        LocalizationManager.Strings.sysToolsInstalledBuiltIn
    }
}

private fun updateYtDlp(onProgress: (ToolDownloadProgress) -> Unit): RuntimeManager.UpdateStatus? {
    val context = PathResolver.appContext ?: return null
    onProgress(ToolDownloadProgress("yt-dlp"))
    return RuntimeManager.updateYTDL(context, RuntimeManager.UpdateChannel.STABLE)
}

actual suspend fun downloadMediaExtractorTool(onProgress: (ToolDownloadProgress) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val status = updateYtDlp(onProgress)
            status == RuntimeManager.UpdateStatus.DONE || status == RuntimeManager.UpdateStatus.ALREADY_UP_TO_DATE
        } catch (_: Exception) {
            false
        }
    }

actual suspend fun autoUpdateMediaTools(onProgress: (ToolDownloadProgress) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val context = PathResolver.appContext ?: return@withContext false
            val latest = latestGitHubReleaseTag("yt-dlp/yt-dlp") ?: return@withContext false
            if (RuntimeManager.version(context) == latest) return@withContext false
            updateYtDlp(onProgress) == RuntimeManager.UpdateStatus.DONE
        } catch (_: Exception) {
            false
        }
    }

actual fun isMediaExtractorToolInstalled(): Boolean = true

actual fun isFfmpegToolInstalled(): Boolean = true
actual fun getMediaToolsDirectory(): String = "Built-in (Denis Cerri Runtime)"

actual fun getJsRuntimeStatus(): JsRuntimeStatus = JsRuntimeStatus(
    name = "QuickJS (built-in)",
    isReady = runCatching {
        RuntimeManager.initialized && RuntimeManager.quickJsLocation.isAvailable
    }.getOrDefault(false)
)

internal actual val toolUpdateSwapsAtomically: Boolean = false
