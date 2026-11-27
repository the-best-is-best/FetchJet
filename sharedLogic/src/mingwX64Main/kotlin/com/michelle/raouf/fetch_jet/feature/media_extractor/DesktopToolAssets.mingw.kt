package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.platform.win.WinApp

private const val LEGACY_YT_DLP_REPO = "nicolaasjan/yt-dlp"
private const val LEGACY_YT_DLP_ZIP = "yt-dlp_win7.zip"

private val windowsToolAssets: DesktopToolAssets by lazy {
    DesktopToolAssets(
        ytDlpZip = if (WinApp.isLegacy) LEGACY_YT_DLP_ZIP else "yt-dlp_win.zip",
        ytDlpProgram = "yt-dlp.exe",
        ffmpegUrl = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-win32-x64.gz",
        ffmpegSha256 = "8883a3dffbd0a16cf4ef95206ea05283f78908dbfb118f73c83f4951dcc06d77",
        ffmpegGzipped = true,
        denoZip = "deno-x86_64-pc-windows-msvc.zip",
        bundledQuickJs = if (WinApp.isLegacy && WinApp.exeDir.isNotEmpty()) "${WinApp.exeDir}\\qjs.exe" else null,
        legacyPlayer = WinApp.isLegacy,
        ytDlpRepo = if (WinApp.isLegacy) LEGACY_YT_DLP_REPO else UPSTREAM_YT_DLP_REPO,
    )
}

internal actual val desktopToolAssets: DesktopToolAssets get() = windowsToolAssets
