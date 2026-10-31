package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun runLocalFfmpeg(args: List<String>): Boolean = withContext(Dispatchers.Default) {
    val bridge = IosMediaBridgeRegistry.bridge ?: return@withContext false
    try {
        bridge.executeFfmpeg(args, null) == 0
    } catch (e: Exception) {
        AppLog.e("ffmpeg (local join) could not run: ${e.message}"); false
    }
}
