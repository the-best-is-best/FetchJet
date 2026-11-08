@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

private fun parseTimeToSeconds(timeStr: String?): Double {
    if (timeStr.isNullOrBlank()) return 0.0
    val cleaned = timeStr.trim()
    val parts = cleaned.split(":")
    return try {
        when (parts.size) {
            3 -> (parts[0].toDoubleOrNull() ?: 0.0) * 3600.0 + (parts[1].toDoubleOrNull()
                ?: 0.0) * 60.0 + (parts[2].toDoubleOrNull() ?: 0.0)

            2 -> (parts[0].toDoubleOrNull() ?: 0.0) * 60.0 + (parts[1].toDoubleOrNull() ?: 0.0)
            1 -> parts[0].toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    } catch (_: Exception) {
        0.0
    }
}

private fun formatDurationSeconds(totalSeconds: Long): String {
    val s = if (totalSeconds < 0) 0L else totalSeconds
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${
            minutes.toString().padStart(2, '0')
        }:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private fun parseSizeToBytes(sizeStr: String): Long {
    val cleaned = sizeStr.trim().lowercase()
    val numStr = cleaned.filter { it.isDigit() || it == '.' }
    val num = numStr.toDoubleOrNull() ?: return 0L
    return when {
        cleaned.endsWith("gib") || cleaned.endsWith("gb") -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
        cleaned.endsWith("mib") || cleaned.endsWith("mb") -> (num * 1024.0 * 1024.0).toLong()
        cleaned.endsWith("kib") || cleaned.endsWith("kb") -> (num * 1024.0).toLong()
        cleaned.endsWith("b") -> num.toLong()
        else -> (num * 1024.0 * 1024.0).toLong()
    }
}

actual suspend fun downloadMediaWithPlatformTool(
    url: String,
    destinationDir: String,
    fileName: String,
    formatId: String?,
    clipStart: String?,
    clipEnd: String?,
    clipByCutting: Boolean,
    speedLimitBytesPerSec: Long,
    speedLimitProvider: (() -> Long)?,
    onProgress: (Float, Long, Long, Long, String?) -> Unit,
    onPostProcess: ((String, Float) -> Unit)?,
    requestHeaders: Map<String, String>
): Boolean = withContext(Dispatchers.IO) {
    requireIosMediaEngine()
    val bridge = IosMediaBridgeRegistry.bridge
    val isBridgeAvailable = bridge?.isAvailable() == true
    val ytdlp = getExistingYtDlpBinary()
    if (!isBridgeAvailable && ytdlp == null) return@withContext false
    val python = getExistingPythonBinary()
    val ffmpeg = getExistingFfmpegBinary()
    // The in-app engine runs ffmpeg in-process (Swift's Popen shim): there is no binary to find,
    // but yt-dlp can still merge video + audio and cut sections.
    val hasFfmpeg = ffmpeg != null || isBridgeAvailable

    val outDir = destinationDir.toPath()
    try {
        FileSystem.SYSTEM.createDirectories(outDir)
    } catch (_: Exception) {
    }

    val isGenericOrScript = fileName.isBlank() ||
            fileName.startsWith("media_video_", ignoreCase = true) ||
            fileName.startsWith("download_", ignoreCase = true) ||
            fileName.matches(
                Regex(
                    """.*\.(php|asp|aspx|jsp|html|htm|cgi|do|action)$""",
                    RegexOption.IGNORE_CASE
                )
            )

    val cleanName = if (!isGenericOrScript) {
        fileName.replace(Regex("""\.[^.]+$"""), "").ifBlank { "%(title)s" }
    } else {
        "%(title)s"
    }
    val isAudio = fileName.endsWith(".m4a", ignoreCase = true) ||
            fileName.endsWith(".mp3", ignoreCase = true) ||
            fileName.endsWith(".opus", ignoreCase = true) ||
            fileName.endsWith(".aac", ignoreCase = true) ||
            fileName.endsWith(".flac", ignoreCase = true) ||
            fileName.endsWith(".wav", ignoreCase = true) ||
            fileName.endsWith(".ogg", ignoreCase = true) ||
            // A video selector like "bestvideo+bestaudio" names audio too: only an audio-only
            // selector (no '+') means an audio download.
            (formatId?.lowercase()?.let {
                !it.contains('+') && (it.contains("audio") || it.contains("ba") || it == "140" || it == "251")
            } ?: false)

    val outputTemplate = "$destinationDir/$cleanName.%(ext)s"

    val rawArgs = mutableListOf<String>()
    rawArgs.add("-o")
    rawArgs.add(outputTemplate)
    rawArgs.addAll(ytDlpHeaderArgs(requestHeaders, url))

    if (isAudio) {
        rawArgs.add("-f")
        if (!formatId.isNullOrBlank()) {
            rawArgs.add("$formatId/bestaudio/best")
        } else {
            rawArgs.add("bestaudio/best")
        }
    } else {
        if (!hasFfmpeg) {
            rawArgs.add("-f")
            if (!formatId.isNullOrBlank()) {
                rawArgs.add("$formatId/best")
            } else {
                rawArgs.add("best")
            }
        } else {
            if (!formatId.isNullOrBlank()) {
                rawArgs.add("-f")
                if (formatId.contains("+") || formatId.contains("/") || formatId.contains("best")) {
                    rawArgs.add(formatId)
                } else {
                    rawArgs.add("$formatId+bestaudio/$formatId/best")
                    rawArgs.add("--merge-output-format")
                    rawArgs.add("mp4")
                }
            } else {
                rawArgs.add("-f")
                rawArgs.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best")
                rawArgs.add("--merge-output-format")
                rawArgs.add("mp4")
            }
            if (ffmpeg != null) {
                rawArgs.add("--ffmpeg-location")
                rawArgs.add(ffmpeg.substringBeforeLast('/'))
            }
        }
    }

    if (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()) {
        val s = clipStart?.trim()?.ifBlank { null } ?: "0"
        val e = clipEnd?.trim()?.ifBlank { null } ?: "inf"
        if (clipByCutting) {
            // Full-speed download, then a local stream-copy cut by FetchJet's own yt-dlp plugin
            // (see CutPlugin.kt; yt-dlp's "--remove-chapters *a-b" crashes in 2026.x).
            val pluginDir = ensureCutPluginDir(parentDirOf(destinationDir))
            val endSec = if (e == "inf") null else parseTimeToSeconds(e)
            if (pluginDir != null) {
                rawArgs.add("--plugin-dirs"); rawArgs.add(pluginDir)
                rawArgs.add("--use-postprocessor"); rawArgs.add(
                    cutPluginArg(
                        parseTimeToSeconds(s),
                        endSec
                    )
                )
            } else {
                rawArgs.add("--download-sections"); rawArgs.add("*$s-$e")
            }
        } else {
            rawArgs.add("--download-sections")
            rawArgs.add("*$s-$e")
        }
    }

    if (speedLimitBytesPerSec > 0) {
        val rateArg = when {
            speedLimitBytesPerSec >= 1024L * 1024L -> {
                val mb = speedLimitBytesPerSec.toDouble() / (1024.0 * 1024.0)
                if (mb == mb.toLong()
                        .toDouble()
                ) "${mb.toLong()}M" else "${(mb * 10).toLong() / 10.0}M"
            }

            speedLimitBytesPerSec >= 1024L -> "${speedLimitBytesPerSec / 1024L}K"
            else -> "$speedLimitBytesPerSec"
        }
        rawArgs.add("--limit-rate")
        rawArgs.add(rateArg)
    }

    rawArgs.add("-c")
    rawArgs.add("--no-mtime")
    rawArgs.add("--newline")
    // Exact bytes on disk so far ("FJBYTES=" appended to yt-dlp's normal progress line): for
    // fragmented video yt-dlp only estimates the size, and size x percent jumped around.
    rawArgs.add("--progress-template"); rawArgs.add("download:[download] %(progress._default_template)s FJBYTES=%(progress.downloaded_bytes)s")
    rawArgs.add("--no-playlist")
    rawArgs.add("--geo-bypass")
    // (No forced youtube:player_client, as on desktop: "android" now gets SABR-only formats and
    // left only 360p format 18, so a 4K pick silently downloaded 360p.)
    getIosCookieFile()?.let { cookiePath ->
        rawArgs.add("--cookies")
        rawArgs.add(cookiePath)
    }
    rawArgs.add(url)

    // Progress is read the clip way (ffmpeg "time=") only in section mode; a cut-afterwards
    // clip downloads like a normal video and the cut shows on the post-processing line.
    val isClipping = (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()) && !clipByCutting
    val clipStartSec = parseTimeToSeconds(clipStart)
    val clipEndSec = if (!clipEnd.isNullOrBlank()) parseTimeToSeconds(clipEnd) else 0.0
    val clipDurationSec = if (clipEndSec > clipStartSec) clipEndSec - clipStartSec else 0.0

    val ansiRegex = Regex("""\x1B\[[0-?]*[ -/]*[@-~]""")
    val progressRegex =
        Regex("""\[download\]\s+([0-9.]+)%\s+of\s+(~\s*)?([0-9.]+\s*[a-zA-Z]+)(?:.*?\bat\s+([0-9.]+\s*[a-zA-Z/]+))?""")
    val percentOnlyRegex = Regex("""\[download\]\s+([0-9.]+)%""")
    val standaloneSpeedRegex =
        Regex("""\b(?:at\s+)?([0-9.]+)\s*([kmg]?i?[b]?/s)\b""", RegexOption.IGNORE_CASE)
    val ffmpegProgressRegex =
        Regex("""(?:size|Lsize)=\s*([0-9.]+)\s*([a-zA-Z]+).*?time=([0-9:.]+)""")

    // A merged format ("137+140") means video + audio + merge; a single format id (progressive
    // file, audio-only, HLS) is one plain download. Decide from the chosen format up front.
    // Decide from the selector actually given to yt-dlp: a bare "137" became "137+bestaudio".
    val fIndex = rawArgs.indexOf("-f")
    val formatSelector = if (fIndex >= 0) rawArgs.getOrNull(fIndex + 1).orEmpty() else ""
    val isMergedFormat = formatSelector.contains('+')
    var totalExpectedPhases = if (isClipping || !isMergedFormat) 1 else 3
    MediaToolErrors.clear(destinationDir)
    val postProcess = PostProcessTracker(destinationDir)
    var destinationCount = 0

    var recordedTotalBytes = 0L
    var temporaryEstimatedBytes = 0L
    var hasExactTotalBytes = false
    var downloadedBytes = 0L
    var lastSpeedBytesPerSec = 0L

    var currentPhase = if (isClipping) {
        if (clipDurationSec > 0.0) "Clipping Video (00:00:00 / ${
            formatDurationSeconds(
                clipDurationSec.toLong()
            )
        })"
        else "Clipping Video..."
    } else if (isMergedFormat) {
        "Downloading Video (Step 1 of 3)"
    } else {
        "Downloading Media"
    }

    val lineHandler: (String) -> Unit = { rawLine ->
        val currentLine = rawLine.replace(ansiRegex, "").trim()
        if (currentLine.isNotBlank()) {
            postProcess.onLine(currentLine)
            MediaToolErrors.record(destinationDir, currentLine)
            val speedMatch = standaloneSpeedRegex.find(currentLine)
            if (speedMatch != null) {
                val num = speedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = speedMatch.groupValues[2].lowercase()
                val parsedSpeed = (num * ytDlpUnitPrefixMultiplier(unit)).toLong()
                if (parsedSpeed > 0L) {
                    lastSpeedBytesPerSec = parsedSpeed
                }
            }

            // Age restriction detection
            if (currentLine.contains("Sign in to confirm your age", ignoreCase = true) ||
                currentLine.contains("confirm your age", ignoreCase = true) ||
                currentLine.contains("inappropriate for some users", ignoreCase = true)
            ) {
                val hasCookies = getIosCookieFile() != null
                currentPhase = if (hasCookies) {
                    "Age-restricted: Expired cookies in Files > FetchJet"
                } else {
                    "Age-restricted: Place cookies.txt in Files > FetchJet"
                }
                onProgress(0f, 0L, 0L, 0L, currentPhase)
            }

            if (currentLine.contains("has already been downloaded")) {
                currentPhase = "Already Downloaded"
                val finalTotal =
                    if (recordedTotalBytes > 0L) recordedTotalBytes else downloadedBytes
                onProgress(1.0f, finalTotal, finalTotal, 0L, currentPhase)
            }

            if (!isClipping && currentLine.contains("Downloading") && currentLine.contains("format(s):")) {
                val formatPart = currentLine.substringAfter("format(s):").trim()
                if (!formatPart.contains("+")) {
                    totalExpectedPhases = 1
                    currentPhase = "Downloading Media"
                } else {
                    totalExpectedPhases = 3
                    currentPhase = "Downloading Video (Step 1 of 3)"
                }
            }

            if (!isClipping && (currentLine.contains("[download] Destination:") || currentLine.contains(
                    "has already been downloaded"
                ))
            ) {
                destinationCount++
                // A new file starts: its size is not the previous file's.
                if (destinationCount > 1) {
                    recordedTotalBytes = 0L
                    temporaryEstimatedBytes = 0L
                    hasExactTotalBytes = false
                    downloadedBytes = 0L
                }
                if (totalExpectedPhases == 3) {
                    currentPhase =
                        if (destinationCount <= 1) "Downloading Video (Step 1 of 3)" else "Downloading Audio (Step 2 of 3)"
                } else {
                    currentPhase = "Downloading Media"
                }
                onProgress(
                    0f,
                    downloadedBytes,
                    if (hasExactTotalBytes) recordedTotalBytes else 0L,
                    lastSpeedBytesPerSec,
                    currentPhase
                )
            }

            if (!isClipping && (currentLine.contains("[Merger] Merging formats into") || currentLine.contains(
                    "[FixupM3u8] Fixup"
                ) || currentLine.contains(
                    "[VideoConvertor] Converting video"
                ))
            ) {
                currentPhase = "Merging Audio and Video (Step 3 of 3)"
                val finalTotal = if (recordedTotalBytes > 0) recordedTotalBytes else downloadedBytes
                onProgress(0.99f, finalTotal, finalTotal, 0L, currentPhase)
            }

            val fm = ffmpegProgressRegex.find(currentLine)
            if (fm != null) {
                val sizeNum = fm.groupValues[1].toDoubleOrNull() ?: 0.0
                val sizeUnit = fm.groupValues[2].lowercase()
                val dlBytes = (sizeNum * ytDlpUnitMultiplier(sizeUnit, 1.0)).toLong()
                if (dlBytes > 0) downloadedBytes = dlBytes

                val ffmpegBitrateMatch = Regex(
                    """bitrate=\s*([0-9.]+)\s*([kmg]?bits/s)""",
                    RegexOption.IGNORE_CASE
                ).find(currentLine)
                val ffmpegSpeedMultMatch =
                    Regex("""speed=\s*([0-9.]+)x""", RegexOption.IGNORE_CASE).find(currentLine)
                var speedFromFfmpeg = 0L
                if (ffmpegBitrateMatch != null) {
                    val bNum = ffmpegBitrateMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    val bUnit = ffmpegBitrateMatch.groupValues[2].lowercase()
                    val multiplier =
                        ffmpegSpeedMultMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                    val bps = when {
                        bUnit.startsWith("m") -> bNum * 1_000_000.0
                        bUnit.startsWith("k") -> bNum * 1_000.0
                        else -> bNum
                    }
                    speedFromFfmpeg = ((bps * multiplier) / 8.0).toLong()
                    if (clipDurationSec > 0.0 && bps > 0.0) {
                        temporaryEstimatedBytes = ((bps / 8.0) * clipDurationSec).toLong()
                    }
                }

                if (speedFromFfmpeg > 0L) {
                    lastSpeedBytesPerSec = speedFromFfmpeg
                }

                val timeStr = fm.groupValues[3]
                val currentTimeSec = parseTimeToSeconds(timeStr)
                val percent = if (clipDurationSec > 0.0) {
                    (currentTimeSec / clipDurationSec).toFloat().coerceIn(0f, 0.99f)
                } else 0f

                if (recordedTotalBytes <= 0L && percent >= 0.03f && currentTimeSec >= 2.0 && downloadedBytes > 0L) {
                    val est = (downloadedBytes / percent).toLong()
                    if (est > downloadedBytes) {
                        temporaryEstimatedBytes = est
                        recordedTotalBytes = est
                    }
                }

                val curFmt = formatDurationSeconds(currentTimeSec.toLong())
                val totFmt =
                    if (clipDurationSec > 0.0) formatDurationSeconds(clipDurationSec.toLong()) else ""
                currentPhase =
                    if (totFmt.isNotEmpty()) "Clipping Video ($curFmt / $totFmt)" else "Clipping Video ($curFmt)"

                onProgress(
                    percent,
                    downloadedBytes,
                    if (recordedTotalBytes > 0L) recordedTotalBytes else temporaryEstimatedBytes,
                    lastSpeedBytesPerSec,
                    currentPhase
                )
            } else {
                val progMatch = progressRegex.find(currentLine)
                if (progMatch != null) {
                    val percent = progMatch.groupValues[1].toFloatOrNull() ?: 0f
                    val totalStr = progMatch.groupValues[3]
                    val speedStr = progMatch.groupValues.getOrNull(4)

                    val parsedTotal = parseSizeToBytes(totalStr)
                    if (parsedTotal > 0L) {
                        temporaryEstimatedBytes = parsedTotal
                        if (!hasExactTotalBytes) {
                            recordedTotalBytes = parsedTotal
                        }
                    }

                    if (!speedStr.isNullOrBlank()) {
                        val spdMatch = standaloneSpeedRegex.find(speedStr)
                        if (spdMatch != null) {
                            val num = spdMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                            val unit = spdMatch.groupValues[2].lowercase()
                            val parsedSpeed = (num * ytDlpUnitPrefixMultiplier(unit)).toLong()
                            if (parsedSpeed > 0L) {
                                lastSpeedBytesPerSec = parsedSpeed
                            }
                        }
                    }

                    val effectiveTotal =
                        if (recordedTotalBytes > 0L) recordedTotalBytes else temporaryEstimatedBytes
                    val computedDownloaded =
                        if (effectiveTotal > 0L) (effectiveTotal * (percent / 100f)).toLong() else 0L
                    downloadedBytes =
                        Regex("""FJBYTES=(\d+)""").find(currentLine)?.groupValues?.get(1)
                            ?.toLongOrNull() ?: computedDownloaded

                    // Per-file percentage, like desktop/Android; MultiStreamProgress (common code)
                    // combines video + audio so the bar always matches the bytes shown.
                    val overallFraction = (percent / 100f).coerceIn(0f, 1f)

                    onProgress(
                        overallFraction,
                        downloadedBytes,
                        if (hasExactTotalBytes || recordedTotalBytes > 0L) recordedTotalBytes else 0L,
                        lastSpeedBytesPerSec,
                        currentPhase
                    )
                } else {
                    val pMatch = percentOnlyRegex.find(currentLine)
                    if (pMatch != null) {
                        val percent = pMatch.groupValues[1].toFloatOrNull() ?: 0f
                        val effectiveTotal =
                            if (recordedTotalBytes > 0L) recordedTotalBytes else temporaryEstimatedBytes
                        val computedDownloaded =
                            if (effectiveTotal > 0L) (effectiveTotal * (percent / 100f)).toLong() else 0L
                        downloadedBytes = computedDownloaded

                        val overallFraction = (percent / 100f).coerceIn(0f, 1f)

                        onProgress(
                            overallFraction,
                            downloadedBytes,
                            if (hasExactTotalBytes || recordedTotalBytes > 0L) recordedTotalBytes else 0L,
                            lastSpeedBytesPerSec,
                            currentPhase
                        )
                    }
                }
            }
        }
    }

    val exitCode = trackPostProcessing(postProcess, onPostProcess) {
        if (bridge != null && bridge.isAvailable()) {
            runCancellableOnBridge(bridge, rawArgs) { line ->
                lineHandler(line)
            }
        } else {
            val cmdList = mutableListOf<String>()
            if (python != null && (ytdlp != null && (ytdlp.endsWith(".py") || !isExecutable(ytdlp)))) {
                cmdList.add(escapeShellArg(python))
            }
            if (ytdlp != null) {
                cmdList.add(escapeShellArg(ytdlp))
            }
            cmdList.addAll((com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + rawArgs).map {
                escapeShellArg(
                    it
                )
            })
            executeCommandStreaming(cmdList.joinToString(" ")) { rawLine ->
                lineHandler(rawLine)
            }
        }
    }

    val candidates = try {
        FileSystem.SYSTEM.list(outDir).filter {
            val n = it.name
            !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp")
        }
    } catch (_: Exception) {
        emptyList()
    }

    val finalFile = (if (!isGenericOrScript) {
        val baseName = cleanName.take(15)
        candidates.firstOrNull { it.name.startsWith(cleanName) || it.name.startsWith(baseName) }
    } else null) ?: candidates.maxByOrNull {
        try {
            FileSystem.SYSTEM.metadata(it).lastModifiedAtMillis ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    val success = exitCode == 0
    if (success) {
        val totalSize = finalFile?.let {
            try {
                FileSystem.SYSTEM.metadata(it).size
            } catch (_: Exception) {
                null
            }
        } ?: if (recordedTotalBytes > 0L) recordedTotalBytes else downloadedBytes
        onProgress(1f, totalSize, totalSize, 0L, "Completed")
    }
    success
}

/**
 * Runs yt-dlp on the in-app engine so that cancelling the coroutine (Pause / Cancel in the UI)
 * really stops it: the embedded Python cannot be killed like a process, so the bridge is told
 * to raise yt-dlp's DownloadCancelled at its next progress update. Before this, Pause only froze
 * the bar while the download went on in the background, and Resume waited behind it.
 */
private suspend fun runCancellableOnBridge(
    bridge: IosMediaExecutionBridge,
    args: List<String>,
    onLine: (String) -> Unit
): Int = coroutineScope {
    val runId = "dl-" + kotlin.random.Random.nextLong().toULong().toString(16)
    var finished = false
    val watchdog = launch(Dispatchers.Default) {
        try {
            awaitCancellation()
        } finally {
            if (!finished) bridge.cancelRun(runId)
        }
    }
    try {
        bridge.executeYtDlpCancellable(
            runId,
            com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + args
        ) { _, _, line -> onLine(line) }
    } finally {
        finished = true
        watchdog.cancel()
    }
}
