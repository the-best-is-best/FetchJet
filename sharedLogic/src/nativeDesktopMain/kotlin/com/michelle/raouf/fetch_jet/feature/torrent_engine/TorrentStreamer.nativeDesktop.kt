package com.michelle.raouf.fetch_jet.feature.torrent_engine

import io.ktor.client.HttpClient
import okio.Path

actual suspend fun streamUrlToFile(
    urlStr: String,
    targetPath: Path,
    userAgent: String,
    speedLimitProvider: () -> Long,
    startByte: Long,
    onProgress: (downloaded: Long, speed: Long) -> Unit
): Result<Unit> {
    val client = HttpClient { webSeedDefaults() }
    return try {
        streamUrlWithClient(client, urlStr, targetPath, userAgent, speedLimitProvider, startByte, onProgress)
    } finally {
        client.close()
    }
}
