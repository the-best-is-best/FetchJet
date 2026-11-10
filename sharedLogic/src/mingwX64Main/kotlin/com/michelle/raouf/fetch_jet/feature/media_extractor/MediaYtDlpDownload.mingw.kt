package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.platform.win.WinException
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun joinPath(dir: String, name: String): String =
    winFullPath(dir.replace('/', '\\').trimEnd('\\') + "\\" + name)

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
    val executable = getExistingYtDlpBinary() ?: return@withContext false

    val proxy = try {
        LocalThrottlingProxy.start(speedLimitProvider)
    } catch (_: Throwable) {
        null
    }

    val command = mutableListOf<String>()
    command.add(executable)

    command.addAll(ytDlpRuntimeArgs())
    command.addAll(ytDlpHeaderArgs(requestHeaders, url))

    if (proxy != null) {
        command.add("--proxy")
        command.add("http://127.0.0.1:${proxy.port}")
        command.add("--downloader-args")
        command.add("ffmpeg_i:-http_proxy http://127.0.0.1:${proxy.port}")
    } else if (speedLimitBytesPerSec > 0L) {
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
        command.add("--limit-rate")
        command.add(rateArg)
    }

    val isAudio = fileName.endsWith(".m4a", ignoreCase = true) ||
            fileName.endsWith(".mp3", ignoreCase = true) ||
            fileName.endsWith(".opus", ignoreCase = true) ||
            fileName.endsWith(".aac", ignoreCase = true) ||
            fileName.endsWith(".flac", ignoreCase = true) ||
            fileName.endsWith(".wav", ignoreCase = true) ||
            fileName.endsWith(".ogg", ignoreCase = true)

    if (getExistingFfmpegBinary() == null && !isSystemFfmpegInstalled()) {
        command.add("-f")
        if (!formatId.isNullOrBlank()) {
            if (isAudio) {
                command.add("$formatId/bestaudio/best")
            } else {
                command.add("$formatId/best")
            }
        } else {
            command.add("best")
        }
    } else {
        if (!formatId.isNullOrBlank()) {
            command.add("-f")
            if (isAudio) {
                command.add("$formatId/bestaudio/best")
            } else {
                command.add("$formatId+bestaudio/$formatId/best")
                command.add("--merge-output-format")
                command.add("mp4")
            }
        } else {
            command.add("-f")
            command.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best")
            command.add("--merge-output-format")
            command.add("mp4")
        }

        getExistingFfmpegBinary()?.let { ffmpegBin ->
            command.add("--ffmpeg-location")
            command.add((WinPaths.parentOf(ffmpegBin) ?: getToolsDir()))
        }
    }

    command.add("-c")
    command.add("--no-mtime")
    command.add("--geo-bypass")
    command.add("--no-playlist")
    val isPlaylistUrl =
        url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".mpd") }
    if (isPlaylistUrl) {
        command.add("--merge-output-format"); command.add("mp4")
        command.add("--remux-video"); command.add("mp4")
        command.add("--hls-prefer-ffmpeg")
    }
    if (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()) {
        val s = clipStart?.trim()?.ifBlank { null } ?: "0"
        val e = clipEnd?.trim()?.ifBlank { null } ?: "inf"
        if (clipByCutting) {
            val pluginDir = ensureCutPluginDir(parentDirOf(destinationDir))
            val endSec = if (e == "inf") null else parseTimeToSeconds(e)
            if (pluginDir != null) {
                command.add("--plugin-dirs"); command.add(pluginDir)
                command.add("--use-postprocessor"); command.add(
                    cutPluginArg(
                        parseTimeToSeconds(s),
                        endSec
                    )
                )
            } else {
                command.add("--download-sections"); command.add("*$s-$e")
            }
        } else {
            command.add("--download-sections")
            command.add("*$s-$e")
        }
    }
    command.add("-o")
    val isScript = fileName.matches(
        Regex(
            """.*\.(php|asp|aspx|jsp|html|htm|cgi|do|action)$""",
            RegexOption.IGNORE_CASE
        )
    )
    val outputTemplate = if (fileName.isNotBlank() && !isScript && isAudio) {
        joinPath(destinationDir, fileName.substringBeforeLast('.')).replace("%", "%%") + ".%(ext)s"
    } else if (fileName.isNotBlank() && !isScript) {
        joinPath(destinationDir, fileName).replace("%", "%%")
    } else {
        joinPath(destinationDir.replace("%", "%%"), "%(title)s.%(ext)s")
    }
    command.add(outputTemplate)

    command.add("--newline")
    command.add("--progress-template"); command.add("download:[download] %(progress._default_template)s FJBYTES=%(progress.downloaded_bytes)s")
    command.add("--")
    command.add(url)

    val secrets = YtDlpSecretArgs.split(command)
    val environment = HashMap<String, String?>(ytDlpEnvironment)
    if (proxy != null) {
        val proxyUrl = "http://127.0.0.1:${proxy.port}"
        for (name in listOf("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy", "ALL_PROXY")) {
            environment[name] = proxyUrl
        }
    }

    val isCancelled = AtomicInt(0)
    fun cancelled(): Boolean = isCancelled.value != 0

    val isClipping = (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()) && !clipByCutting
    val clipStartSec = parseTimeToSeconds(clipStart)
    val clipEndSec = if (!clipEnd.isNullOrBlank()) parseTimeToSeconds(clipEnd) else 0.0
    val clipDurationSec = if (clipEndSec > clipStartSec) clipEndSec - clipStartSec else 0.0

    val ansiRegex = Regex("""\x1B\[[0-?]*[ -/]*[@-~]""")
    val progressRegex =
        Regex("""\[download\]\s+([0-9.]+)%\s+of\s+(?:~\s*)?([0-9.]+\s*[a-zA-Z]+)(?:.*?\bat\s+([0-9.]+\s*[a-zA-Z/]+))?""")
    val percentOnlyRegex = Regex("""\[download\]\s+([0-9.]+)%""")
    val ffmpegProgressRegex =
        Regex("""(?:size|Lsize)=\s*([0-9.]+)\s*([a-zA-Z]+).*?time=([0-9:.]+)""")

    val fIndex = command.indexOf("-f")
    val formatSelector = if (fIndex >= 0) command.getOrNull(fIndex + 1).orEmpty() else ""
    val isMergedFormat = formatSelector.contains('+')
    var totalExpectedPhases = if (isClipping || !isMergedFormat) 1 else 3
    MediaToolErrors.clear(destinationDir)
    val postProcess = PostProcessTracker(destinationDir)
    var destinationCount = 0

    class ProgressHolder {
        @Volatile
        var percent = 0f

        @Volatile
        var downloadedBytes = 0L

        @Volatile
        var totalBytes = 0L

        @Volatile
        var phase = ""

        @Volatile
        var isMerging = false
    }

    val initialPhase = if (isClipping) {
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

    val progressHolder = ProgressHolder().apply { phase = initialPhase }

    var rollingSpeedBytesPerSec = 0L
    var lastSpeedBytesPerSec = 0L

    val handleLine: (String) -> Unit = handleLine@{ rawLine ->
        if (cancelled()) return@handleLine
        val currentLine = rawLine.replace(ansiRegex, "").trim()
        if (currentLine.isBlank()) return@handleLine
        postProcess.onLine(currentLine)
        MediaToolErrors.record(destinationDir, currentLine)

        if (!isClipping && currentLine.contains("Downloading") && currentLine.contains("format(s):")) {
            val formatPart = currentLine.substringAfter("format(s):").trim()
            if (!formatPart.contains("+")) {
                totalExpectedPhases = 1
                progressHolder.phase = "Downloading Media"
            } else {
                totalExpectedPhases = 3
                progressHolder.phase = "Downloading Video (Step 1 of 3)"
            }
        }

        if (!isClipping && (currentLine.contains("[download] Destination:") || currentLine.contains(
                "has already been downloaded"
            ))
        ) {
            destinationCount++
            if (destinationCount > 1) {
                progressHolder.totalBytes = 0L
                progressHolder.downloadedBytes = 0L
                progressHolder.percent = 0f
            }
            if (totalExpectedPhases == 3) {
                progressHolder.phase = if (destinationCount <= 1) {
                    "Downloading Video (Step 1 of 3)"
                } else {
                    "Downloading Audio (Step 2 of 3)"
                }
            } else {
                progressHolder.phase = "Downloading Media"
            }
            if (!cancelled()) {
                onProgress(
                    0f,
                    progressHolder.downloadedBytes,
                    progressHolder.totalBytes,
                    lastSpeedBytesPerSec,
                    progressHolder.phase
                )
            }
        }
        if (!isClipping && (currentLine.contains("[Merger]") || currentLine.contains("Merging formats") || currentLine.contains(
                "[ffmpeg] Merging"
            ))
        ) {
            progressHolder.isMerging = true
            progressHolder.phase = "Merging Audio & Video (Step 3 of 3)"
            if (!cancelled()) {
                onProgress(
                    1.0f,
                    progressHolder.totalBytes,
                    progressHolder.totalBytes,
                    0L,
                    progressHolder.phase
                )
            }
        }

        if (!progressHolder.isMerging && !cancelled()) {
            val fm = ffmpegProgressRegex.find(currentLine)
            if (fm != null) {
                val sizeNum = fm.groupValues[1].toDoubleOrNull() ?: 0.0
                val sizeUnit = fm.groupValues[2].lowercase()
                val downloadedBytes =
                    (sizeNum * ytDlpUnitMultiplier(sizeUnit, 1.0)).toLong()
                if (downloadedBytes > 0) progressHolder.downloadedBytes =
                    downloadedBytes

                val timeStr = fm.groupValues[3]
                val currentTimeSec = parseTimeToSeconds(timeStr)

                val percent = if (clipDurationSec > 0.0) {
                    (currentTimeSec / clipDurationSec).toFloat().coerceIn(0f, 0.99f)
                } else 0f

                if (progressHolder.totalBytes <= 0L && percent >= 0.03f && currentTimeSec >= 2.0 && progressHolder.downloadedBytes > 0) {
                    val est = (progressHolder.downloadedBytes / percent).toLong()
                    if (est > progressHolder.downloadedBytes) {
                        progressHolder.totalBytes = est
                    }
                }

                progressHolder.percent = percent

                val proxySpeed = proxy?.getInstantaneousDownloadSpeedBytesPerSec() ?: 0L
                val effectiveSpeed = if (proxy != null) {
                    proxySpeed
                } else {
                    rollingSpeedBytesPerSec
                }
                lastSpeedBytesPerSec = effectiveSpeed

                val curFmt = formatDurationSeconds(currentTimeSec.toLong())
                val totFmt =
                    if (clipDurationSec > 0.0) formatDurationSeconds(clipDurationSec.toLong()) else ""
                progressHolder.phase =
                    if (totFmt.isNotEmpty()) "Clipping Video ($curFmt / $totFmt)" else "Clipping Video ($curFmt)"

                if (!cancelled()) {
                    onProgress(
                        percent,
                        progressHolder.downloadedBytes,
                        progressHolder.totalBytes,
                        lastSpeedBytesPerSec,
                        progressHolder.phase
                    )
                }
            } else {
                val m = progressRegex.find(currentLine)
                if (m != null) {
                    val percent = m.groupValues[1].toFloatOrNull() ?: 0f
                    val sizeStr = m.groupValues[2]
                    val speedStr = m.groupValues.getOrNull(3) ?: ""

                    val sizeNum =
                        sizeStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                    val sizeLower = sizeStr.lowercase()
                    val totalBytes =
                        (sizeNum * ytDlpUnitMultiplier(sizeLower, 1.0)).toLong()
                    if (totalBytes > 0) progressHolder.totalBytes = totalBytes
                    val downloadedBytes =
                        Regex("""FJBYTES=(\d+)""").find(currentLine)?.groupValues?.get(1)
                            ?.toLongOrNull()
                            ?: if (progressHolder.totalBytes > 0) (progressHolder.totalBytes * (percent / 100f)).toLong() else 0L
                    progressHolder.downloadedBytes = downloadedBytes

                    val speedNum =
                        speedStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                    val speedLower = speedStr.lowercase()
                    val speedBytesPerSec = (speedNum * ytDlpUnitMultiplier(
                        speedLower,
                        if (speedLower.contains("b/s")) 1.0 else 1024.0
                    )).toLong()
                    val proxyTotal = proxy?.totalDownloadedBytes ?: 0L
                    val proxySpeed =
                        proxy?.getInstantaneousDownloadSpeedBytesPerSec() ?: 0L
                    if (proxy != null && proxyTotal > 0L) {
                        lastSpeedBytesPerSec = proxySpeed
                    } else if (proxySpeed > 0L) {
                        lastSpeedBytesPerSec = proxySpeed
                    } else if (speedBytesPerSec > 0) {
                        lastSpeedBytesPerSec = speedBytesPerSec
                    }
                    progressHolder.percent = percent / 100f

                    if (!cancelled()) {
                        onProgress(
                            percent / 100f,
                            downloadedBytes,
                            progressHolder.totalBytes,
                            lastSpeedBytesPerSec,
                            progressHolder.phase
                        )
                    }
                } else {
                    val pm = percentOnlyRegex.find(currentLine)
                    if (pm != null) {
                        val percent = pm.groupValues[1].toFloatOrNull() ?: 0f
                        val downloadedBytes =
                            if (progressHolder.totalBytes > 0) (progressHolder.totalBytes * (percent / 100f)).toLong() else progressHolder.downloadedBytes
                        progressHolder.percent = percent / 100f
                        progressHolder.downloadedBytes = downloadedBytes
                        onProgress(
                            percent / 100f,
                            downloadedBytes,
                            progressHolder.totalBytes,
                            lastSpeedBytesPerSec,
                            progressHolder.phase
                        )
                    } else if (currentLine.contains("[download]")) {
                        onProgress(
                            0f,
                            progressHolder.downloadedBytes,
                            progressHolder.totalBytes,
                            lastSpeedBytesPerSec,
                            progressHolder.phase
                        )
                    }
                }
            }
        }
    }

    try {
        coroutineScope {
            val ticker = if (proxy != null) launch {
                while (isActive && !cancelled()) {
                    delay(300)
                    if (cancelled()) break
                    if (proxy.totalDownloadedBytes > 0L) {
                        onProgress(
                            progressHolder.percent,
                            progressHolder.downloadedBytes,
                            progressHolder.totalBytes,
                            proxy.getInstantaneousDownloadSpeedBytesPerSec(),
                            progressHolder.phase
                        )
                    }
                }
            } else null
            try {
                val result = trackPostProcessing(postProcess, onPostProcess) {
                    winRunProcess(
                        secrets.command, environment = environment, mergeStderr = true, keepOutput = false,
                        onStdoutLine = { line ->
                            try {
                                handleLine(line)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
                result.exitCode == 0 && !cancelled()
            } finally {
                isCancelled.value = 1
                ticker?.cancel()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: WinException) {
        false
    } finally {
        isCancelled.value = 1
        secrets.deleteNow()
        try {
            proxy?.close()
        } catch (_: Throwable) {
        }
    }
}

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
