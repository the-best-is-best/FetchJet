package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

actual fun createHttpEngine(
    pathResolver: PathResolver,
    speedLimitProvider: (String) -> Long
): HttpDownloadEngine = HttpChunkDownloader(
    pathResolver = pathResolver,
    speedLimitProvider = speedLimitProvider
)
