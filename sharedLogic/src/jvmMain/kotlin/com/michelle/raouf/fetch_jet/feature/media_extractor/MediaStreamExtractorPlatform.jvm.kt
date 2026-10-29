package com.michelle.raouf.fetch_jet.feature.media_extractor


import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getDouble
import com.michelle.raouf.fetch_jet.core.util.getInt
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getObjectArray
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit


actual suspend fun resolvePlatformStream(url: String): String? {

    return runYtDlp(
        listOf(
            "--js-runtimes",
            "deno",
            "--js-runtimes",
            "node",
            "-g",
            "--no-warnings",
            "--no-playlist",
            "--",
            url
        )
    )

}

private fun safeEncodeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    val sb = StringBuilder()
    for (ch in trimmed) {
        if (ch.code in 33..126) {
            sb.append(ch)
        } else if (ch == ' ') {
            sb.append("%20")
        } else {
            val bytes = ch.toString().toByteArray(Charsets.UTF_8)
            for (b in bytes) {
                sb.append(String.format("%%%02X", b))
            }
        }
    }
    return sb.toString()
}

fun probeContentLength(url: String): Long {
    val safe = safeEncodeUrl(url)
    return try {
        val uri = java.net.URI(safe).toURL()
        val conn = uri.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.connect()
        val len = conn.contentLengthLong
        conn.disconnect()
        if (len > 0L) len else -1L
    } catch (_: Exception) {
        -1L
    }
}

fun parseDurationFromFfmpegOutput(output: String): Long {
    val match = Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""").find(output) ?: return 0L
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds =
        match.groupValues[3].toDoubleOrNull()?.let { kotlin.math.round(it).toLong() } ?: 0L
    return (hours * 3600L) + (minutes * 60L) + seconds
}

fun probeMediaDurationWithFfmpeg(url: String): Long {
    val ffmpegBin = getExistingFfmpegBinary()
    val command = mutableListOf<String>()

    if (ffmpegBin != null) {
        // Run the binary directly. Going through `cmd.exe /c` would let `&`, `|`, `^`
        // and `>` inside a URL run shell commands.
        command.add(ffmpegBin.absolutePath)
    } else if (isSystemFfmpegInstalled()) {
        command.add("ffmpeg")
    } else {
        return 0L
    }

    command.add("-hide_banner")
    command.add("-i")
    command.add(safeEncodeUrl(url))

    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val outputBuilder = StringBuilder()
        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.append(line ?: "").append("\n")
                    }
                }
            } catch (_: Exception) {
            }
        }
        readerThread.start()

        val finished = process.waitFor(12, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            readerThread.interrupt()
        }
        readerThread.join(1000)

        val output = outputBuilder.toString()
        parseDurationFromFfmpegOutput(output)
    } catch (_: Exception) {
        0L
    }
}

actual suspend fun inspectPlatformQualities(url: String): List<MediaQualityEntry> {
    getExistingYtDlpBinary()
        ?: throw Exception("yt-dlp tool is not found. Please click 'Download Tools' in Settings.")

    val output =
        runYtDlp(
            listOf(
                "--js-runtimes",
                "deno",
                "--js-runtimes",
                "node",
                "-j",
                "--no-warnings",
                "--no-playlist",
                "--",
                url
            )
        )
            ?: return emptyList()

    val parsedRoot = try {
        MiniJson.parse(output) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    }

    val videoTitle = parsedRoot?.getString("title")?.let { decodeUnicodeEscapes(it) }
        ?: Regex(""""title"\s*:\s*"([^"]+)"""").find(output)?.groupValues?.get(1)
            ?.let { decodeUnicodeEscapes(it) }

    var videoDuration = parsedRoot?.getLong("duration")
        ?: Regex(""""duration"\s*:\s*([0-9.]+)""").find(output)?.groupValues?.get(1)
            ?.toDoubleOrNull()?.toLong()
        ?: 0L

    if (videoDuration <= 0L) {
        videoDuration = probeMediaDurationWithFfmpeg(url)
    }

    val contentLengthCache = mutableMapOf<String, Long>()
    fun getCachedContentLength(u: String): Long {
        val clean = u.trim()
        if (clean.isBlank()) return -1L
        return contentLengthCache.getOrPut(clean) { probeContentLength(clean) }
    }

    val rawFormats = parsedRoot?.getObjectArray("formats") ?: emptyList()
    val list = mutableListOf<MediaQualityEntry>()
    val seenKeys = mutableSetOf<String>()

    for (f in rawFormats) {
        val formatId = f["format_id"]?.toString() ?: continue
        if (formatId.isBlank()) continue

        val directUrl = f.getString("url") ?: f.getString("manifest_url") ?: ""
        val ext = f.getString("ext") ?: "mp4"
        val rawHeight = f.getInt("height") ?: 0
        val vcodec = f.getString("vcodec") ?: "none"
        val acodec = f.getString("acodec") ?: "none"
        val resolution = f.getString("resolution") ?: ""
        val formatNote = f.getString("format_note") ?: ""
        val explicitFilesize = f.getLong("filesize") ?: f.getLong("filesize_approx") ?: -1L
        val bitrateBps = when {
            f.getDouble("bitrate") != null && f.getDouble("bitrate")!! > 0 -> {
                val b = f.getDouble("bitrate")!!
                if (b > 100_000.0) b else b * 1000.0
            }

            f.getDouble("tbr") != null && f.getDouble("tbr")!! > 0 -> {
                f.getDouble("tbr")!! * 1000.0
            }

            f.getDouble("vbr") != null && f.getDouble("vbr")!! > 0 -> {
                val v = f.getDouble("vbr")!!
                val a = f.getDouble("abr") ?: 128.0
                (if (v > 100_000.0) v else v * 1000.0) + (if (a > 100_000.0) a else a * 1000.0)
            }

            else -> 0.0
        }
        val filesize =
            if (explicitFilesize > 0) explicitFilesize else getCachedContentLength(directUrl.ifBlank { url })

        // Check if audio only
        val isAudioOnly = (vcodec.equals("none", ignoreCase = true) || resolution.contains(
            "audio only",
            ignoreCase = true
        ) || formatNote.contains("audio", ignoreCase = true)) &&
                (!acodec.equals("none", ignoreCase = true) || ext in listOf(
                    "m4a",
                    "mp3",
                    "opus",
                    "aac",
                    "flac",
                    "wav",
                    "ogg"
                ))

        if (isAudioOnly) {
            val audioKey = "audio_${ext.lowercase()}"
            if (!seenKeys.contains(audioKey)) {
                seenKeys.add(audioKey)
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "Audio Only (${ext.uppercase()})",
                        directUrl = directUrl,
                        format = ext,
                        size = filesize,
                        isSelected = false,
                        formatId = formatId,
                        originalUrl = url,
                        videoTitle = videoTitle,
                        durationSeconds = videoDuration
                    )
                )
            }
            continue
        }

        val parsedHeightFromText =
            Regex("""(\d+)p""").find(formatId)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d+)p""").find(resolution)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d+)x(\d+)""").find(resolution)?.groupValues?.get(2)?.toIntOrNull()
                ?: 0
        val effectiveRawHeight = if (rawHeight > 0) rawHeight else parsedHeightFromText

        // Map height to standard resolution categories
        val height = when {
            effectiveRawHeight in 2100..2200 -> 2160
            effectiveRawHeight in 1400..1500 -> 1440
            effectiveRawHeight in 1050..1150 -> 1080
            effectiveRawHeight in 700..750 -> 720
            effectiveRawHeight in 520..560 -> 540
            effectiveRawHeight in 460..500 -> 480
            effectiveRawHeight in 340..380 -> 360
            effectiveRawHeight in 220..260 -> 240
            effectiveRawHeight in 130..160 -> 144
            else -> effectiveRawHeight
        }

        var label: String? = null
        var rank = 0

        if (height > 0) {
            rank = height
            label = when {
                height >= 2160 -> "4K (${ext.uppercase()})"
                height >= 1440 -> "1440p (2K - ${ext.uppercase()})"
                height >= 1080 -> "1080p (Full HD - ${ext.uppercase()})"
                height >= 720 -> "720p (HD - ${ext.uppercase()})"
                height >= 540 -> "540p (${ext.uppercase()})"
                height >= 480 -> "480p (SD - ${ext.uppercase()})"
                height >= 360 -> "360p (SD - ${ext.uppercase()})"
                height >= 240 -> "240p (Low - ${ext.uppercase()})"
                height >= 144 -> "144p (Low - ${ext.uppercase()})"
                else -> "${height}p (${ext.uppercase()})"
            }
        } else if (formatId.equals("hd", ignoreCase = true) || formatNote.contains(
                "hd",
                ignoreCase = true
            )
        ) {
            rank = 725
            label = "HD Quality (Direct ${ext.uppercase()})"
        } else if (formatId.equals("sd", ignoreCase = true) || formatNote.contains(
                "sd",
                ignoreCase = true
            )
        ) {
            rank = 365
            label = "SD Quality (Direct ${ext.uppercase()})"
        }

        if (label != null) {
            val key = "video_${rank}_${ext.lowercase()}"
            val protocol = f["protocol"]?.toString() ?: ""
            val isHls = directUrl.contains(".m3u8", ignoreCase = true) ||
                    protocol.contains("m3u8", ignoreCase = true) ||
                    formatId.startsWith("hls", ignoreCase = true)
            val isDirectMp4 = !isHls && (ext.equals("mp4", ignoreCase = true) || directUrl.contains(
                ".mp4",
                ignoreCase = true
            ))

            val existingIndex = list.indexOfFirst {
                it.qualityLabel == label && it.format.equals(
                    ext,
                    ignoreCase = true
                )
            }
            if (existingIndex == -1) {
                seenKeys.add(key)
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = label,
                        directUrl = directUrl,
                        format = ext,
                        size = filesize,
                        isSelected = false,
                        formatId = formatId,
                        originalUrl = url,
                        videoTitle = videoTitle,
                        durationSeconds = videoDuration
                    )
                )
            } else if (isDirectMp4) {
                val existing = list[existingIndex]
                val existingIsHls = existing.directUrl.contains(
                    ".m3u8",
                    ignoreCase = true
                ) || existing.formatId?.startsWith("hls", ignoreCase = true) == true
                if (existingIsHls) {
                    list[existingIndex] = MediaQualityEntry(
                        index = 0,
                        qualityLabel = label,
                        directUrl = directUrl,
                        format = ext,
                        size = filesize,
                        isSelected = false,
                        formatId = formatId,
                        originalUrl = url,
                        videoTitle = videoTitle,
                        durationSeconds = videoDuration
                    )
                }
            }
        }
    }

    if (list.isEmpty()) {
        val bestStream = resolvePlatformStream(url)
        if (!bestStream.isNullOrBlank()) {
            list.add(
                MediaQualityEntry(
                    index = 0,
                    qualityLabel = "Best Quality (yt-dlp Native - MP4)",
                    directUrl = bestStream,
                    format = "mp4",
                    size = -1L,
                    isSelected = true,
                    originalUrl = url,
                    videoTitle = videoTitle,
                    durationSeconds = videoDuration
                )
            )
        } else {
            val clean = url.substringBefore('?')
            val ext = clean.substringAfterLast('.', "").lowercase()
            val isKnownMedia = ext in listOf(
                "mp3",
                "m4a",
                "flac",
                "wav",
                "aac",
                "ogg",
                "opus",
                "mp4",
                "mkv",
                "webm",
                "avi",
                "mov"
            )
            if (isKnownMedia) {
                val isAudio = ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
                val dur =
                    if (videoDuration > 0L) videoDuration else probeMediaDurationWithFfmpeg(url)
                val size = getCachedContentLength(url)
                val label =
                    if (isAudio) "Audio Only (${ext.uppercase()})" else "Direct Media (${ext.uppercase()})"
                val title = videoTitle ?: clean.substringAfterLast('/').substringBeforeLast('.')
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = label,
                        directUrl = url,
                        format = ext,
                        size = size,
                        isSelected = true,
                        originalUrl = url,
                        videoTitle = title,
                        durationSeconds = dur
                    )
                )
            }
        }
    }

    // Sort descending: 4K -> 2K -> 1080p -> HD -> 720p -> 540p -> 480p -> SD -> 360p -> 240p -> 144p -> Audio
    list.sortByDescending { entry ->
        when {
            entry.qualityLabel.contains("4K") -> 2160
            entry.qualityLabel.contains("1440p") -> 1440
            entry.qualityLabel.contains("1080p") -> 1080
            entry.qualityLabel.contains("HD Quality") -> 725
            entry.qualityLabel.contains("720p") -> 720
            entry.qualityLabel.contains("540p") -> 540
            entry.qualityLabel.contains("480p") -> 480
            entry.qualityLabel.contains("SD Quality") -> 365
            entry.qualityLabel.contains("360p") -> 360
            entry.qualityLabel.contains("240p") -> 240
            entry.qualityLabel.contains("144p") -> 144
            entry.qualityLabel.contains("Best Quality") -> 1000
            entry.qualityLabel.startsWith("Audio Only") -> -1
            else -> {
                val match = Regex("""(\d+)p""").find(entry.qualityLabel)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
        }
    }

    list.forEachIndexed { i, entry ->
        list[i] = entry.copy(
            index = i,
            isSelected = (i == 0),
            videoTitle = videoTitle,
            durationSeconds = if (entry.durationSeconds > 0L) entry.durationSeconds else videoDuration
        )
    }

    return list
}


private class BlockingRateLimiter(private val speedLimitProvider: () -> Long) {
    private val lock = Any()
    private var availableTokens: Double = 0.0
    private var lastRefillTime = System.nanoTime()
    private var lastLimit = 0L

    fun acquire(bytes: Int) {
        if (bytes <= 0) return
        var waitMs = 0L
        var waitNanos = 0

        synchronized(lock) {
            val currentLimit = speedLimitProvider()
            if (currentLimit <= 0L) {
                availableTokens = 0.0
                return
            }

            val now = System.nanoTime()
            if (currentLimit != lastLimit) {
                lastLimit = currentLimit
                availableTokens = 0.0
                lastRefillTime = now
            }

            val elapsedSec = (now - lastRefillTime) / 1_000_000_000.0
            lastRefillTime = now

            val maxTokens = maxOf(16 * 1024.0, currentLimit * 0.20)
            availableTokens =
                minOf(maxTokens, availableTokens + elapsedSec * currentLimit.toDouble())
            availableTokens -= bytes.toDouble()

            if (availableTokens < 0.0) {
                val deficit = -availableTokens
                val waitSec = deficit / currentLimit.toDouble()
                waitMs = (waitSec * 1000.0).toLong()
                waitNanos = ((waitSec * 1_000_000_000.0) % 1_000_000).toInt()
            }
        }

        if (waitMs > 0L || waitNanos > 0) {
            try {
                Thread.sleep(waitMs, waitNanos)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

private class LocalThrottlingProxy(
    private val speedLimitProvider: (() -> Long)? = null
) : AutoCloseable {
    private val serverSocket =
        java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    @Volatile
    private var isRunning = true
    private val rateLimiter = speedLimitProvider?.let { BlockingRateLimiter(it) }
    private val openSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()

    val totalDownloadedBytes = java.util.concurrent.atomic.AtomicLong(0L)
    val totalUploadedBytes = java.util.concurrent.atomic.AtomicLong(0L)

    private var lastSpeedCalcTimeMs = System.currentTimeMillis()
    private var lastSpeedCalcBytes = 0L
    private var currentSpeedBytesPerSec = 0L

    @Synchronized
    fun getInstantaneousDownloadSpeedBytesPerSec(): Long {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastSpeedCalcTimeMs
        if (elapsedMs >= 350L) {
            val totalBytes = totalDownloadedBytes.get()
            val deltaBytes = totalBytes - lastSpeedCalcBytes
            if (deltaBytes > 0L && elapsedMs > 0L) {
                val instant = (deltaBytes * 1000L) / elapsedMs
                currentSpeedBytesPerSec =
                    if (currentSpeedBytesPerSec <= 0L) instant else (instant * 0.7 + currentSpeedBytesPerSec * 0.3).toLong()
                lastSpeedCalcTimeMs = now
                lastSpeedCalcBytes = totalBytes
            } else if (elapsedMs >= 1200L) {
                currentSpeedBytesPerSec = 0L
                lastSpeedCalcTimeMs = now
                lastSpeedCalcBytes = totalBytes
            }
        }
        return currentSpeedBytesPerSec
    }

    init {
        Thread {
            while (isRunning && !serverSocket.isClosed) {
                try {
                    val clientSocket = serverSocket.accept()
                    openSockets.add(clientSocket)
                    Thread {
                        handleConnection(clientSocket)
                    }.apply {
                        isDaemon = true
                        start()
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "LocalThrottlingProxy-Acceptor"
            start()
        }
    }

    private fun handleConnection(clientSocket: java.net.Socket) {
        var remoteSocket: java.net.Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBuffer = ByteArray(8192)
            var totalRead = 0
            while (totalRead < headerBuffer.size) {
                val r = clientIn.read(headerBuffer, totalRead, headerBuffer.size - totalRead)
                if (r <= 0) break
                totalRead += r
                val text = String(headerBuffer, 0, totalRead, Charsets.ISO_8859_1)
                if (text.contains("\r\n\r\n")) break
            }

            if (totalRead <= 0) return
            val requestHeader = String(headerBuffer, 0, totalRead, Charsets.ISO_8859_1)
            val firstLine = requestHeader.substringBefore("\r\n")
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val target = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                val host = target.substringBefore(":")
                val port = target.substringAfter(":", "443").toIntOrNull() ?: 443

                val remote = java.net.Socket(host, port)
                remote.tcpNoDelay = true
                remoteSocket = remote
                openSockets.add(remote)

                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                val remoteIn = remote.getInputStream()
                val remoteOut = remote.getOutputStream()

                val uploadThread = Thread {
                    try {
                        val buf = ByteArray(16384)
                        while (isRunning) {
                            val r = clientIn.read(buf)
                            if (r <= 0) break
                            totalUploadedBytes.addAndGet(r.toLong())
                            remoteOut.write(buf, 0, r)
                            remoteOut.flush()
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            remote.close()
                        } catch (_: Exception) {
                        }
                        try {
                            clientSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }.apply { isDaemon = true; start() }

                try {
                    val buf = ByteArray(16384)
                    while (isRunning) {
                        val r = remoteIn.read(buf)
                        if (r <= 0) break
                        totalDownloadedBytes.addAndGet(r.toLong())
                        rateLimiter?.acquire(r)
                        clientOut.write(buf, 0, r)
                        clientOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        remote.close()
                    } catch (_: Exception) {
                    }
                    try {
                        clientSocket.close()
                    } catch (_: Exception) {
                    }
                }

                uploadThread.join(300)
            } else {
                val hostLine =
                    requestHeader.lines().firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                val rawHost = hostLine?.substringAfter(":")?.trim() ?: target.substringAfter("://")
                    .substringBefore("/")
                val host = rawHost.substringBefore(":")
                val port = rawHost.substringAfter(":", "80").toIntOrNull() ?: 80

                val remote = java.net.Socket(host, port)
                remote.tcpNoDelay = true
                remoteSocket = remote
                openSockets.add(remote)

                val remoteOut = remote.getOutputStream()
                val remoteIn = remote.getInputStream()

                remoteOut.write(headerBuffer, 0, totalRead)
                remoteOut.flush()

                val uploadThread = Thread {
                    try {
                        val buf = ByteArray(16384)
                        while (isRunning) {
                            val r = clientIn.read(buf)
                            if (r <= 0) break
                            totalUploadedBytes.addAndGet(r.toLong())
                            remoteOut.write(buf, 0, r)
                            remoteOut.flush()
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            remote.close()
                        } catch (_: Exception) {
                        }
                        try {
                            clientSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }.apply { isDaemon = true; start() }

                try {
                    val buf = ByteArray(16384)
                    while (isRunning) {
                        val r = remoteIn.read(buf)
                        if (r <= 0) break
                        totalDownloadedBytes.addAndGet(r.toLong())
                        rateLimiter?.acquire(r)
                        clientOut.write(buf, 0, r)
                        clientOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        remote.close()
                    } catch (_: Exception) {
                    }
                    try {
                        clientSocket.close()
                    } catch (_: Exception) {
                    }
                }

                uploadThread.join(300)
            }
        } catch (_: Exception) {
        } finally {
            try {
                clientSocket.close()
            } catch (_: Exception) {
            }
            try {
                remoteSocket?.close()
            } catch (_: Exception) {
            }
            openSockets.remove(clientSocket)
            remoteSocket?.let { openSockets.remove(it) }
        }
    }

    override fun close() {
        isRunning = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
        for (s in openSockets) {
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
        openSockets.clear()
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
    onPostProcess: ((String, Float) -> Unit)?
): Boolean = withContext(Dispatchers.IO) {
    val executableFile = getExistingYtDlpBinary() ?: return@withContext false

    val proxy = try {
        LocalThrottlingProxy(speedLimitProvider)
    } catch (_: Exception) {
        null
    }

    val command = mutableListOf<String>()
    command.add(executableFile.absolutePath)

    // yt-dlp's default JS runtime is deno; naming only "node" would *replace* it. Allow both so
    // whichever the user has installed is used for YouTube's JS challenges.
    command.add("--js-runtimes")
    command.add("deno")
    command.add("--js-runtimes")
    command.add("node")

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
    val outputTemplate = if (fileName.isNotBlank() && !isScript) {
        File(destinationDir, fileName).absolutePath.replace("%", "%%")
    } else {
        File(destinationDir.replace("%", "%%"), "%(title)s.%(ext)s").path
    }
    command.add(outputTemplate)

    command.add("--newline")
    command.add("--")
    command.add(url)

    val process: Process
    try {
        val processBuilder = ProcessBuilder(command)
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
    } catch (e: Exception) {
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
                            val downloadedBytes = when {
                                sizeUnit.contains("gib") || sizeUnit.contains("gb") -> (sizeNum * 1024 * 1024 * 1024).toLong()
                                sizeUnit.contains("mib") || sizeUnit.contains("mb") -> (sizeNum * 1024 * 1024).toLong()
                                sizeUnit.contains("kib") || sizeUnit.contains("kb") -> (sizeNum * 1024).toLong()
                                else -> sizeNum.toLong()
                            }
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
                                val totalBytes = when {
                                    sizeLower.contains("gib") || sizeLower.contains("gb") -> (sizeNum * 1024 * 1024 * 1024).toLong()
                                    sizeLower.contains("mib") || sizeLower.contains("mb") -> (sizeNum * 1024 * 1024).toLong()
                                    sizeLower.contains("kib") || sizeLower.contains("kb") -> (sizeNum * 1024).toLong()
                                    else -> sizeNum.toLong()
                                }
                                if (totalBytes > 0) progressHolder.totalBytes = totalBytes
                                val downloadedBytes =
                                    if (progressHolder.totalBytes > 0) (progressHolder.totalBytes * (percent / 100f)).toLong() else 0L
                                progressHolder.downloadedBytes = downloadedBytes

                                val speedNum =
                                    speedStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                val speedLower = speedStr.lowercase()
                                val speedBytesPerSec = when {
                                    speedLower.contains("gib") || speedLower.contains("gb") -> (speedNum * 1024 * 1024 * 1024).toLong()
                                    speedLower.contains("mib") || speedLower.contains("mb") -> (speedNum * 1024 * 1024).toLong()
                                    speedLower.contains("kib") || speedLower.contains("kb") -> (speedNum * 1024).toLong()
                                    speedLower.contains("b/s") -> speedNum.toLong()
                                    else -> (speedNum * 1024).toLong()
                                }
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

private fun runYtDlp(args: List<String>): String? {

    return try {

        val executableFile = getExistingYtDlpBinary() ?: return null

        val command = mutableListOf<String>()

        // Run yt-dlp directly (never through `cmd.exe /c`: cmd re-parses `&`, `|`, `^`
        // and `>` in the URL, which is a command-injection hole reachable from any web page).
        command.add(executableFile.absolutePath)

        command.addAll(args)


        val process = ProcessBuilder(command)

            .redirectErrorStream(true)

            .start()


        val outputBuilder = StringBuilder()

        val readerThread = Thread {

            try {

                process.inputStream.bufferedReader().use { reader ->

                    var line: String?

                    while (reader.readLine().also { line = it } != null) {

                        outputBuilder.append(line ?: "").append("\n")

                    }

                }

            } catch (_: Exception) {

            }

        }

        readerThread.start()


        val finished = process.waitFor(25, TimeUnit.SECONDS)

        if (!finished) {

            process.destroyForcibly()

            readerThread.interrupt()

            return null

        }



        readerThread.join(2000)

        val output = outputBuilder.toString().trim()



        if (process.exitValue() == 0 && output.isNotBlank()) {

            output

        } else {

            null

        }

    } catch (_: Exception) {

        null

    }

}


fun getToolsDir(): File {

    val dir = File(System.getProperty("user.home"), ".fetch_jet/tools")

    if (!dir.exists()) dir.mkdirs()

    return dir

}


fun getExistingYtDlpBinary(): File? {

    val isWin = System.getProperty("os.name").lowercase().contains("win")

    val name = if (isWin) "yt-dlp.exe" else "yt-dlp"


    val toolsFile = File(getToolsDir(), name)

    if (toolsFile.exists() && toolsFile.canExecute()) return toolsFile


    // (No fallback to a file in the current working directory: that would let a planted
    //  yt-dlp.exe / ffmpeg.exe next to a downloaded file get executed.)



    return null

}


fun getExistingFfmpegBinary(): File? {

    val isWin = System.getProperty("os.name").lowercase().contains("win")

    val name = if (isWin) "ffmpeg.exe" else "ffmpeg"


    val toolsFile = File(getToolsDir(), name)

    if (toolsFile.exists() && toolsFile.canExecute()) return toolsFile


    // (No fallback to a file in the current working directory: that would let a planted
    //  yt-dlp.exe / ffmpeg.exe next to a downloaded file get executed.)



    return null

}


@Volatile
private var systemFfmpegCache: Boolean? = null

fun isSystemFfmpegInstalled(): Boolean {
    systemFfmpegCache?.let { return it }
    val result = try {
        val process = ProcessBuilder("ffmpeg", "-version")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()   // previously left running forever
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }
    systemFfmpegCache = result
    return result
}


private fun decodeUnicodeEscapes(input: String): String {

    val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")

    return regex.replace(input) { m ->

        try {

            val codePoint = m.groupValues[1].toInt(16)

            codePoint.toChar().toString()

        } catch (_: Exception) {

            m.value

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

