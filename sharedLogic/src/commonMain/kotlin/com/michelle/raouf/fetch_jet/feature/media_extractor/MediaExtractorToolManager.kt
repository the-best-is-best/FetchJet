package com.michelle.raouf.fetch_jet.feature.media_extractor

expect fun getMediaExtractorToolStatus(): String
expect suspend fun downloadMediaExtractorTool(): Boolean
expect fun isMediaExtractorToolInstalled(): Boolean
expect fun isFfmpegToolInstalled(): Boolean
expect fun getMediaToolsDirectory(): String