package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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
    val executableFile = getExistingYtDlpBinary() ?: return@withContext false

    val proxy = try {
        LocalThrottlingProxy(speedLimitProvider)
    } catch (_: Exception) {
        null
    }

    val command = mutableListOf<String>()
    command.add(executableFile.absolutePath)

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

        // Provide ffmpeg location
        getExistingFfmpegBinary()?.let { ffmpegBin ->
            command.add("--ffmpeg-location")
            command.add(ffmpegBin.parentFile.absolutePath)
        }
    }

    command.add("-c")
    command.add("--no-mtime")
    command.add("--geo-bypass")
    command.add("--no-playlist") // a watch?v=...&list=... link is one video, not the whole mix
    // A direct HLS/DASH playlist URL: one video, written as mp4 whatever the stream container is,
    // and the referer is required by most players' CDNs.
    val isPlaylistUrl =
        url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".mpd") }
    if (isPlaylistUrl) {
        command.add("--merge-output-format"); command.add("mp4")
        command.add("--remux-video"); command.add("mp4")
        command.add("--hls-prefer-ffmpeg")
    }
    // (No forced youtube:player_client: the "android" client now needs a PO token and only
    // produced warnings; yt-dlp's default client selection is the right one.)
    if (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()) {
        val s = clipStart?.trim()?.ifBlank { null } ?: "0"
        val e = clipEnd?.trim()?.ifBlank { null } ?: "inf"
        if (clipByCutting) {
            // Full-speed download, then a local stream-copy cut by FetchJet's own yt-dlp plugin
            // (see CutPlugin.kt; yt-dlp's "--remove-chapters *a-b" crashes in 2026.x).
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
    // "-o" is a yt-dlp *template*: a literal '%' in the title or folder must be doubled, while
    // the fallback template keeps its own %(..)s fields.
    val outputTemplate = if (fileName.isNotBlank() && !isScript && isAudio) {
        File(destinationDir, fileName.substringBeforeLast('.')).absolutePath.replace("%", "%%") + ".%(ext)s"
    } else if (fileName.isNotBlank() && !isScript) {
        File(destinationDir, fileName).absolutePath.replace("%", "%%")
    } else {
        File(destinationDir.replace("%", "%%"), "%(title)s.%(ext)s").path
    }
    command.add(outputTemplate)

    command.add("--newline")
    // Exact bytes on disk so far ("FJBYTES=" appended to yt-dlp's normal progress line): for
    // fragmented video yt-dlp only estimates the size, and size x percent jumped around.
    command.add("--progress-template"); command.add("download:[download] %(progress._default_template)s FJBYTES=%(progress.downloaded_bytes)s")
    command.add("--")
    command.add(url)

    val process: Process
    val secrets = YtDlpSecretArgs.split(command)
    try {
        val processBuilder = ProcessBuilder(secrets.command)
            .redirectErrorStream(true)
        // yt-dlp on Windows writes the pipe in the console code page: titles in Arabic and other
        // scripts arrived garbled (and so did the file paths in its progress lines).
        processBuilder.environment()["PYTHONIOENCODING"] = "utf-8"
        processBuilder.environment()["PYTHONUTF8"] = "1"

        if (proxy != null) {
            val proxyUrl = "http://127.0.0.1:${proxy.port}"
            val env = processBuilder.environment()
            env["http_proxy"] = proxyUrl
            env["https_proxy"] = proxyUrl
            env["HTTP_PROXY"] = proxyUrl
            env["HTTPS_PROXY"] = proxyUrl
            env["all_proxy"] = proxyUrl
            env["ALL_PROXY"] = proxyUrl
        }

        process = processBuilder.start()
        secrets.deleteWhenDone(process)
    } catch (e: Exception) {
        secrets.deleteNow()
        try {
            proxy?.close()
        } catch (_: Exception) {
        }
        return@withContext false
    }

    val isCancelled = java.util.concurrent.atomic.AtomicBoolean(false)

    // Progress is read the clip way (ffmpeg "time=") only in section mode; a cut-afterwards
    // clip downloads like a normal video and the cut shows on the post-processing line.
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

    // A merged format ("137+140") means video + audio + merge; a single format id (progressive
    // file, audio-only, HLS) is one plain download. Decide from the chosen format instead of
    // guessing "3 steps" and correcting later.
    // Decide from the selector actually passed to yt-dlp: a bare "137" was turned into
    // "137+bestaudio" above, which is a merge too.
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

    val tickerThread = if (proxy != null) {
        Thread {
            while (!isCancelled.get() && process.isAlive) {
                try {
                    Thread.sleep(300)
                    if (isCancelled.get() || !process.isAlive) break
                    if (proxy.totalDownloadedBytes.get() > 0L) {
                        val liveSpeed = proxy.getInstantaneousDownloadSpeedBytesPerSec()
                        onProgress(
                            progressHolder.percent,
                            progressHolder.downloadedBytes,
                            progressHolder.totalBytes,
                            liveSpeed,
                            progressHolder.phase
                        )
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "MediaStreamExtractor-Ticker"
            start()
        }
    } else null
    var lastSpeedCalcTimeMs = System.currentTimeMillis()
    var lastSpeedCalcBytes = 0L
    var rollingSpeedBytesPerSec = 0L
    var lastSpeedBytesPerSec = 0L

    val readerThread = Thread {
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var line: String? = null
                while (!isCancelled.get() && reader.readLine().also { line = it } != null) {
                    if (isCancelled.get()) break
                    val rawLine = line ?: continue
                    val currentLine = rawLine.replace(ansiRegex, "").trim()
                    if (currentLine.isBlank()) continue
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
                        // A new file starts: its size is not the previous file's.
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
                        if (!isCancelled.get()) {
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
                        if (!isCancelled.get()) {
                            onProgress(
                                1.0f,
                                progressHolder.totalBytes,
                                progressHolder.totalBytes,
                                0L,
                                progressHolder.phase
                            )
                        }
                    }

                    if (!progressHolder.isMerging && !isCancelled.get()) {
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

                            // Only establish totalBytes once past early buffering, and freeze once set
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

                            if (!isCancelled.get()) {
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
                                val proxyTotal = proxy?.totalDownloadedBytes?.get() ?: 0L
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

                                if (!isCancelled.get()) {
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
            }
        } catch (_: Exception) {
        }
    }
    readerThread.isDaemon = true
    readerThread.start()

    try {
        trackPostProcessing(postProcess, onPostProcess) {
            while (process.isAlive) {
                coroutineContext.ensureActive()
                process.waitFor(150, TimeUnit.MILLISECONDS)
            }
        }
        val exitCode = process.exitValue()
        exitCode == 0 && !isCancelled.get()
    } catch (e: kotlinx.coroutines.CancellationException) {
        isCancelled.set(true)
        throw e
    } finally {
        isCancelled.set(true)
        try {
            tickerThread?.interrupt()
        } catch (_: Exception) {
        }
        try {
            proxy?.close()
        } catch (_: Exception) {
        }
        try {
            process.descendants().forEach { it.destroyForcibly() }
        } catch (_: Exception) {
        }
        try {
            process.destroyForcibly()
        } catch (_: Exception) {
        }
        try {
            process.waitFor(500, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
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
