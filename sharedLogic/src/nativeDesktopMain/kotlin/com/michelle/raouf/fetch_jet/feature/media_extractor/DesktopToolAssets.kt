package com.michelle.raouf.fetch_jet.feature.media_extractor

internal class DesktopToolAssets(
    val ytDlpZip: String,
    val ytDlpProgram: String,
    val ffmpegUrl: String,
    val ffmpegSha256: String,
    val ffmpegZipped: Boolean,
    val denoZip: String,
)

internal expect val desktopToolAssets: DesktopToolAssets
