package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.util.FileUtil
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    if (!ensureRuntimeInitialized()) {
        return@withContext false
    }

    val context = PathResolver.appContext ?: return@withContext false
    val outDir = File(destinationDir)
    if (!outDir.exists()) outDir.mkdirs()

    val request = YTDLRequest(url)
    val processId = "dl_${UUID.randomUUID()}"

    val cleanName = fileName.replace(Regex("""\.[^.]+$"""), "").ifBlank { "download" }
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

    val outputTemplate = File(outDir, "$cleanName.%(ext)s").absolutePath
    request.addOption("-o", outputTemplate)
    ytDlpHeaderArgs(requestHeaders, url).chunked(2).forEach { (k, v) -> request.addOption(k, v) }
    request.addOption("--no-playlist") // a watch?v=...&list=... link is one video, not the whole mix

    if (isAudio) {
        if (!formatId.isNullOrBlank()) {
            request.addOption("-f", "$formatId/bestaudio/best")
        } else {
            request.addOption("-f", "bestaudio/best")
        }
    } else {
        if (!formatId.isNullOrBlank()) {
            if (formatId.contains("+") || formatId.contains("/") || formatId.contains("best")) {
                request.addOption("-f", formatId)
            } else {
                request.addOption("-f", "$formatId+bestaudio/$formatId/best")
                request.addOption("--merge-output-format", "mp4")
            }
        } else {
            request.addOption(
                "-f",
                "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best"
            )
            request.addOption("--merge-output-format", "mp4")
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
                request.addOption("--plugin-dirs", pluginDir)
                request.addOption(
                    "--use-postprocessor",
                    cutPluginArg(parseTimeToSeconds(s), endSec)
                )
            } else {
                request.addOption("--download-sections", "*$s-$e")
            }
        } else {
            request.addOption("--download-sections", "*$s-$e")
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
        request.addOption("--limit-rate", rateArg)
    }

    request.addOption("-c")
    request.addOption("--no-mtime")
    request.addOption("--newline")
    // Exact bytes on disk so far ("FJBYTES=" appended to yt-dlp's normal progress line): for
    // fragmented video yt-dlp only estimates the size, and size x percent jumped around.
    request.addOption(
        "--progress-template",
        "download:[download] %(progress._default_template)s FJBYTES=%(progress.downloaded_bytes)s"
    )
    FileUtil.getCookieFile(context) { cookiePath ->
        request.addOption("--cookies", cookiePath)
    }

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
    val ffmpegProgressRegex =
        Regex("""(?:size|Lsize)=\s*([0-9.]+)\s*([a-zA-Z]+).*?time=([0-9:.]+)""")
    val standaloneSpeedRegex =
        Regex("""\b(?:at\s+)?([0-9.]+)\s*([kmg]?i?[b]?/s)\b""", RegexOption.IGNORE_CASE)

    // A merged format ("137+140") means video + audio + merge; a single format id (progressive
    // file, audio-only, HLS) is one plain download. Decide from the chosen format instead of
    // guessing "3 steps" and correcting later.
    // Decide from the selector actually given to yt-dlp: a bare "137" became "137+bestaudio".
    val isMergedFormat = !isAudio && (formatId.isNullOrBlank() || formatId.contains('+') ||
            !(formatId.contains('/') || formatId.contains("best")))
    var totalExpectedPhases = if (isClipping || !isMergedFormat) 1 else 3
    MediaToolErrors.clear(destinationDir)
    val postProcess = PostProcessTracker(destinationDir)
    var destinationCount = 0
    var isMerging = false

    var recordedTotalBytes = 0L
    var temporaryEstimatedBytes = 0L
    var hasExactTotalBytes = false
    var downloadedBytes = 0L
    var lastSpeedBytesPerSec = 0L
    var lastSpeedCalcTimeMs = System.currentTimeMillis()
    var lastSpeedCalcBytes = 0L
    var rollingCalculatedSpeed = 0L

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

    try {
        // execute() blocks its thread, so a coroutine cancel (pause / cancel in the UI) was
        // never observed and yt-dlp kept downloading; a resume then started a second yt-dlp
        // on the same files. runKillable kills the process when the coroutine is cancelled.
        val response = trackPostProcessing(postProcess, onPostProcess) {
            RuntimeManager.getInstance().runKillable { killableId ->
                RuntimeManager.getInstance().execute(
                    request = request,
                    processId = killableId,
                    redirectErrorStream = true
                ) { progressPercent, _, rawLine ->
                    val currentLine = rawLine.replace(ansiRegex, "").trim()
                    if (currentLine.isBlank()) return@execute
                    postProcess.onLine(currentLine)
                    MediaToolErrors.record(destinationDir, currentLine)

                    // 1. Scan line for speed using standalone speed regex
                    val speedMatch = standaloneSpeedRegex.find(currentLine)
                    if (speedMatch != null) {
                        val num = speedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        val unit = speedMatch.groupValues[2].lowercase()
                        val parsedSpeed = when {
                            unit.startsWith("g") -> (num * 1024 * 1024 * 1024).toLong()
                            unit.startsWith("m") -> (num * 1024 * 1024).toLong()
                            unit.startsWith("k") -> (num * 1024).toLong()
                            else -> num.toLong()
                        }
                        if (parsedSpeed > 0L) {
                            lastSpeedBytesPerSec = parsedSpeed
                        }
                    }

                    // 2. Rolling delta speed calculation as dynamic fallback
                    val nowMs = System.currentTimeMillis()
                    val elapsed = nowMs - lastSpeedCalcTimeMs
                    if (elapsed >= 400L) {
                        val fileOnDisk = File(outputTemplate)
                        val partFile = File("$outputTemplate.part")
                        val diskBytes = when {
                            partFile.exists() -> partFile.length()
                            fileOnDisk.exists() -> fileOnDisk.length()
                            else -> 0L
                        }
                        val currentBytes = if (downloadedBytes > 0) downloadedBytes else diskBytes
                        val delta = currentBytes - lastSpeedCalcBytes
                        if (delta > 0L && elapsed > 0L) {
                            val instant = (delta * 1000L) / elapsed
                            rollingCalculatedSpeed = if (rollingCalculatedSpeed <= 0L) instant
                            else (rollingCalculatedSpeed * 0.4 + instant * 0.6).toLong()
                            lastSpeedCalcBytes = currentBytes
                            lastSpeedCalcTimeMs = nowMs
                        } else if (elapsed >= 1500L) {
                            lastSpeedCalcTimeMs = nowMs
                        }
                    }

                    val effectiveSpeed = when {
                        lastSpeedBytesPerSec > 0L -> lastSpeedBytesPerSec
                        rollingCalculatedSpeed > 0L -> rollingCalculatedSpeed
                        else -> 0L
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
                            currentPhase = if (destinationCount <= 1) {
                                "Downloading Video (Step 1 of 3)"
                            } else {
                                "Downloading Audio (Step 2 of 3)"
                            }
                        } else {
                            currentPhase = "Downloading Media"
                        }
                        onProgress(
                            0f,
                            downloadedBytes,
                            if (hasExactTotalBytes) recordedTotalBytes else 0L,
                            effectiveSpeed,
                            currentPhase
                        )
                    }

                    if (!isClipping && (currentLine.contains("[Merger]") || currentLine.contains("Merging formats") || currentLine.contains(
                            "[ffmpeg] Merging"
                        ))
                    ) {
                        isMerging = true
                        currentPhase = "Merging Audio & Video (Step 3 of 3)"
                        onProgress(1.0f, recordedTotalBytes, recordedTotalBytes, 0L, currentPhase)
                    }

                    if (!isMerging) {
                        val fm = ffmpegProgressRegex.find(currentLine)
                        if (fm != null) {
                            val sizeNum = fm.groupValues[1].toDoubleOrNull() ?: 0.0
                            val sizeUnit = fm.groupValues[2].lowercase()
                            val dlBytes = (sizeNum * ytDlpUnitMultiplier(sizeUnit, 1.0)).toLong()
                            if (dlBytes > 0) downloadedBytes = dlBytes

                            // Calculate clipping speed from ffmpeg bitrate and speed multiplier
                            val ffmpegBitrateMatch = Regex(
                                """bitrate=\s*([0-9.]+)\s*([kmg]?bits/s)""",
                                RegexOption.IGNORE_CASE
                            ).find(currentLine)
                            val ffmpegSpeedMultMatch =
                                Regex("""speed=\s*([0-9.]+)x""", RegexOption.IGNORE_CASE).find(
                                    currentLine
                                )
                            var speedFromFfmpeg = 0L
                            if (ffmpegBitrateMatch != null) {
                                val bNum = ffmpegBitrateMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                                val bUnit = ffmpegBitrateMatch.groupValues[2].lowercase()
                                val multiplier =
                                    ffmpegSpeedMultMatch?.groupValues?.get(1)?.toDoubleOrNull()
                                        ?: 1.0
                                val bps = when {
                                    bUnit.startsWith("m") -> bNum * 1_000_000.0
                                    bUnit.startsWith("k") -> bNum * 1_000.0
                                    else -> bNum
                                }
                                speedFromFfmpeg = ((bps * multiplier) / 8.0).toLong()
                                if (clipDurationSec > 0.0 && bps > 0.0) {
                                    temporaryEstimatedBytes =
                                        ((bps / 8.0) * clipDurationSec).toLong()
                                }
                            }

                            // Rolling delta calculation from output byte growth
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedCalcTimeMs
                            if (elapsed >= 300L) {
                                val delta = dlBytes - lastSpeedCalcBytes
                                if (delta > 0L) {
                                    val instant = (delta * 1000L) / elapsed
                                    rollingCalculatedSpeed =
                                        if (rollingCalculatedSpeed <= 0L) instant
                                        else (rollingCalculatedSpeed * 0.4 + instant * 0.6).toLong()
                                    lastSpeedCalcBytes = dlBytes
                                    lastSpeedCalcTimeMs = now
                                }
                            }

                            val clippingSpeed = when {
                                speedFromFfmpeg > 0L -> speedFromFfmpeg
                                rollingCalculatedSpeed > 0L -> rollingCalculatedSpeed
                                lastSpeedBytesPerSec > 0L -> lastSpeedBytesPerSec
                                else -> 0L
                            }
                            if (clippingSpeed > 0L) {
                                lastSpeedBytesPerSec = clippingSpeed
                            }

                            val timeStr = fm.groupValues[3]
                            val currentTimeSec = parseTimeToSeconds(timeStr)
                            val percent = if (clipDurationSec > 0.0) {
                                (currentTimeSec / clipDurationSec).toFloat().coerceIn(0f, 0.99f)
                            } else 0f

                            // Only establish totalBytes once past early buffering, and freeze once set
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
                                recordedTotalBytes,
                                lastSpeedBytesPerSec,
                                currentPhase
                            )
                        } else {
                            val m = progressRegex.find(currentLine)
                            if (m != null) {
                                val percent = (m.groupValues[1].toFloatOrNull() ?: 0f) / 100f
                                val isApproximate = m.groupValues[2].isNotBlank()
                                val sizeStr = m.groupValues[3]

                                val sizeNum =
                                    sizeStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                val sizeLower = sizeStr.lowercase()
                                val parsedBytes =
                                    (sizeNum * ytDlpUnitMultiplier(sizeLower, 1.0)).toLong()

                                // Store approximate/unconfirmed size internally in code temporarily
                                if (isApproximate && parsedBytes > 0) {
                                    temporaryEstimatedBytes = parsedBytes
                                }

                                // Only adopt confirmed real size when yt-dlp outputs without '~'
                                if (!isApproximate && parsedBytes > 0) {
                                    hasExactTotalBytes = true
                                    recordedTotalBytes = parsedBytes
                                }

                                val fileOnDisk = File(outputTemplate)
                                val partFile = File("$outputTemplate.part")
                                val diskBytes = when {
                                    partFile.exists() -> partFile.length()
                                    fileOnDisk.exists() -> fileOnDisk.length()
                                    else -> 0L
                                }

                                val computedFromPercent = when {
                                    hasExactTotalBytes && recordedTotalBytes > 0 -> (recordedTotalBytes * percent).toLong()
                                    temporaryEstimatedBytes > 0 -> (temporaryEstimatedBytes * percent).toLong()
                                    else -> 0L
                                }

                                downloadedBytes =
                                    Regex("""FJBYTES=(\d+)""").find(currentLine)?.groupValues?.get(1)
                                        ?.toLongOrNull() ?: maxOf(diskBytes, computedFromPercent)

                                onProgress(
                                    percent,
                                    downloadedBytes,
                                    if (hasExactTotalBytes) recordedTotalBytes else 0L,
                                    effectiveSpeed,
                                    currentPhase
                                )
                            } else {
                                val pm = percentOnlyRegex.find(currentLine)
                                if (pm != null) {
                                    val percent = (pm.groupValues[1].toFloatOrNull() ?: 0f) / 100f
                                    val fileOnDisk = File(outputTemplate)
                                    val partFile = File("$outputTemplate.part")
                                    val diskBytes = when {
                                        partFile.exists() -> partFile.length()
                                        fileOnDisk.exists() -> fileOnDisk.length()
                                        else -> 0L
                                    }
                                    downloadedBytes = if (recordedTotalBytes > 0) {
                                        (recordedTotalBytes * percent).toLong()
                                    } else maxOf(diskBytes, downloadedBytes)

                                    onProgress(
                                        percent,
                                        downloadedBytes,
                                        if (hasExactTotalBytes) recordedTotalBytes else 0L,
                                        effectiveSpeed,
                                        currentPhase
                                    )
                                } else if (progressPercent >= 0f) {
                                    val percent = (progressPercent / 100f).coerceIn(0f, 1f)
                                    val fileOnDisk = File(outputTemplate)
                                    val partFile = File("$outputTemplate.part")
                                    val diskBytes = when {
                                        partFile.exists() -> partFile.length()
                                        fileOnDisk.exists() -> fileOnDisk.length()
                                        else -> 0L
                                    }
                                    downloadedBytes = if (recordedTotalBytes > 0) {
                                        (recordedTotalBytes * percent).toLong()
                                    } else maxOf(diskBytes, downloadedBytes)

                                    onProgress(
                                        percent,
                                        downloadedBytes,
                                        if (hasExactTotalBytes) recordedTotalBytes else 0L,
                                        effectiveSpeed,
                                        currentPhase
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        response.exitCode == 0
    } catch (e: kotlinx.coroutines.CancellationException) {
        RuntimeManager.getInstance().destroyProcessById(processId)
        throw e
    } catch (e: Exception) {
        false
    }
}
