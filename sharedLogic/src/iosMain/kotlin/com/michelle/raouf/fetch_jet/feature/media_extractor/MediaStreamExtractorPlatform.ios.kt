@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * iOS cannot spawn processes, so yt-dlp only works through the Swift bridge once it runs
 * Python in-process. Until that bridge reports available, fail immediately with a clear
 * message rather than spinning on a `popen` that can never succeed.
 */
internal fun requireIosMediaEngine() {
    val bridge = IosMediaBridgeRegistry.bridge
    if (bridge == null || !bridge.isAvailable()) {
        throw UnsupportedOperationException(
            "Media extraction is not available on iOS yet. It needs the in-app Python engine (coming in a later build)."
        )
    }
    iosEjsCacheReady
}

actual suspend fun resolvePlatformStream(url: String): String? = withContext(Dispatchers.IO) {
    requireIosMediaEngine()
    val args = mutableListOf("-g", "--no-warnings", "--no-playlist", "--geo-bypass")
    getIosCookieFile()?.let { cookiePath ->
        args.add("--cookies")
        args.add(cookiePath)
    }
    args.add(url)
    val bridge = IosMediaBridgeRegistry.bridge
    val output = if (bridge != null && bridge.isAvailable()) {
        // In-process engine only: when it fails, surface its error instead of falling back to a
        // (never working on iOS) popen of a yt-dlp binary, whose shell noise used to look like output.
        bridge.captureYtDlp(com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + args)
    } else {
        runYtDlpCapture(args)
    }
    if (output == null) {
        val reason = bridge?.lastErrorText()?.lineSequence()
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotBlank() }
        throw Exception(reason ?: "yt-dlp returned no stream URL")
    }
    output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
}

actual suspend fun inspectPlatformPlaylist(
    url: String,
    headers: Map<String, String>
): MediaPlaylist? =
    withContext(Dispatchers.IO) {
        requireIosMediaEngine()
        val trimmedUrl = normalizePlaylistUrl(url)
        if (trimmedUrl.isEmpty()) return@withContext null
        val args = mutableListOf("--flat-playlist", "-J", "--no-warnings", "--yes-playlist")
        args.addAll(ytDlpHeaderArgs(headers, trimmedUrl))
        getIosCookieFile()?.let { cookiePath ->
            args.add("--cookies")
            args.add(cookiePath)
        }
        args.add(trimmedUrl)
        val bridge = IosMediaBridgeRegistry.bridge
        val output = if (bridge != null && bridge.isAvailable()) {
            bridge.captureYtDlp(com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + args)
        } else {
            runYtDlpCapture(args)
        }
        output?.let { parsePlaylistJson(it) }
    }

actual fun lastMediaInspectionError(): String? = null

actual suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String? =
    withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return@withContext null
        val args =
            mutableListOf("-j", "--no-warnings", "--no-playlist", "--geo-bypass", "-f", selector)
        args.addAll(ytDlpHeaderArgs(headers, url))
        getIosCookieFile()?.let { args.add("--cookies"); args.add(it) }
        args.add(trimmedUrl)
        val bridge = IosMediaBridgeRegistry.bridge
        try {
            if (bridge != null && bridge.isAvailable()) bridge.captureYtDlp(com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + args) else runYtDlpCapture(
                args
            )
        } catch (_: Exception) {
            null
        }
    }
