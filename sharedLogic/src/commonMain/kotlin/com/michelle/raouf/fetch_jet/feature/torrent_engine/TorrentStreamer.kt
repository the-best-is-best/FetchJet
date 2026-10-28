package com.michelle.raouf.fetch_jet.feature.torrent_engine

import okio.Path

expect suspend fun streamUrlToFile(
    urlStr: String,
    targetPath: Path,
    userAgent: String,
    speedLimitProvider: () -> Long = { 0L },
    startByte: Long = 0L,
    onProgress: (downloaded: Long, speed: Long) -> Unit
): Result<Unit>