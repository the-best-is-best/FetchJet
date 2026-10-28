package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry

expect suspend fun resolvePlatformStream(url: String): String?
expect suspend fun inspectPlatformQualities(url: String): List<MediaQualityEntry>
expect suspend fun downloadMediaWithPlatformTool(
    url: String,
    destinationDir: String,
    fileName: String,
    formatId: String?,
    clipStart: String? = null,
    clipEnd: String? = null,
    speedLimitBytesPerSec: Long = 0L,
    speedLimitProvider: (() -> Long)? = null,
    onProgress: (Float, Long, Long, Long, String?) -> Unit
): Boolean