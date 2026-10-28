package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.deniscerri.ytdl.core.RuntimeManager
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun getMediaExtractorToolStatus(): String {
    val context = PathResolver.appContext ?: return "Installed & Ready (Built-in)"
    return try {
        val ver = RuntimeManager.versionName(context)
        if (!ver.isNullOrBlank()) "yt-dlp $ver (Ready)" else "Installed & Ready (Built-in)"
    } catch (_: Exception) {
        "Installed & Ready (Built-in)"
    }
}

actual suspend fun downloadMediaExtractorTool(): Boolean = withContext(Dispatchers.IO) {
    val context = PathResolver.appContext ?: return@withContext false
    try {
        val status = RuntimeManager.updateYTDL(context, RuntimeManager.UpdateChannel.STABLE)
        status == RuntimeManager.UpdateStatus.DONE || status == RuntimeManager.UpdateStatus.ALREADY_UP_TO_DATE
    } catch (_: Exception) {
        false
    }
}

actual fun isMediaExtractorToolInstalled(): Boolean = true

actual fun isFfmpegToolInstalled(): Boolean = true
actual fun getMediaToolsDirectory(): String = "Built-in (Denis Cerri Runtime)"