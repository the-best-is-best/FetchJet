package com.michelle.raouf.fetch_jet.feature.media_extractor

internal class DesktopToolAssets(
    val ytDlpZip: String,
    val ytDlpProgram: String,
    val ffmpegUrl: String,
    val ffmpegSha256: String,
    val ffmpegGzipped: Boolean,
    val denoZip: String,
    val bundledQuickJs: String? = null,
    val playerSupported: Boolean = true,
    val ytDlpRepo: String = UPSTREAM_YT_DLP_REPO,
)

internal const val UPSTREAM_YT_DLP_REPO = "yt-dlp/yt-dlp"

internal expect val desktopToolAssets: DesktopToolAssets
