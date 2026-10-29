package com.michelle.raouf.fetch_jet.feature.media_extractor

import android.content.Context
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.util.FileUtil
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val isUpdatingYtDlp = AtomicBoolean(false)

fun triggerBackgroundYtDlpUpdate(context: Context) {
    if (isUpdatingYtDlp.compareAndSet(false, true)) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                RuntimeManager.init(context)
                RuntimeManager.updateYTDL(context, RuntimeManager.UpdateChannel.STABLE)
            } catch (_: Exception) {
            } finally {
                isUpdatingYtDlp.set(false)
            }
        }
    }
}

private fun ensureRuntimeInitialized(): Boolean {
    val context = PathResolver.appContext ?: return false
    return try {
        RuntimeManager.init(context)
        RuntimeManager.assertInit()
        true
    } catch (_: Exception) {
        false
    }
}

private fun safeEncodeUrl(url: String): String {
    val clean = url.trim()
    val sb = StringBuilder()
    for (ch in clean) {
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
            ch == ':' || ch == '/' || ch == '?' || ch == '&' || ch == '=' ||
            ch == '%' || ch == '.' || ch == '-' || ch == '_' || ch == '~' ||
            ch == '+' || ch == '#' || ch == '@' || ch == '!' || ch == '$' ||
            ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == ',' || ch == ';'
        ) {
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

private fun probeContentLength(url: String): Long {
    val safe = safeEncodeUrl(url)
    return try {
        val uri = java.net.URI(safe).toURL()
        val conn = uri.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        conn.connect()
        val len = conn.contentLengthLong
        conn.disconnect()
        if (len > 0L) len else -1L
    } catch (_: Exception) {
        -1L
    }
}

private val contentLengthCache = mutableMapOf<String, Long>()
private fun getCachedContentLength(u: String): Long {
    val clean = u.trim()
    if (clean.isBlank()) return -1L
    return contentLengthCache.getOrPut(clean) { probeContentLength(clean) }
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

actual suspend fun resolvePlatformStream(url: String): String? = withContext(Dispatchers.IO) {
    if (!ensureRuntimeInitialized()) return@withContext null

    try {
        val request = YTDLRequest(url)
        request.addOption("-g")
        request.addOption("--no-warnings")
        PathResolver.appContext?.let { ctx ->
            FileUtil.getCookieFile(ctx) { cookiePath ->
                request.addOption("--cookies", cookiePath)
            }
        }

        val response = RuntimeManager.getInstance().execute(request)
        if (response.exitCode == 0 && response.out.isNotBlank()) {
            return@withContext response.out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        }
    } catch (_: Exception) {
    }

    null
}

actual suspend fun inspectPlatformQualities(url: String): List<MediaQualityEntry> =
    withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty() || !ensureRuntimeInitialized()) {
            return@withContext emptyList()
        }

        val context = PathResolver.appContext ?: return@withContext emptyList()

        // Match Desktop's exact yt-dlp arguments (-j --no-warnings) without forcing restrictive player_clients
        val request = YTDLRequest(trimmedUrl)
        request.addOption("-j")
        request.addOption("--no-warnings")
        FileUtil.getCookieFile(context) { cookiePath ->
            request.addOption("--cookies", cookiePath)
        }

        val response = try {
            RuntimeManager.getInstance().execute(request)
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()

        val output = response.out.trim()
        if (output.isBlank()) return@withContext emptyList()

        // Parse JSON output line-by-line (matching Desktop logic)
        val jsonLine = output.lineSequence().firstOrNull { it.startsWith("{") } ?: output
        val json = try {
            JSONObject(jsonLine)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val videoTitle = json.optString("title").ifBlank { null }
        val videoDuration = json.optLong("duration", 0L)

        val rawFormats: JSONArray = json.optJSONArray("formats") ?: JSONArray()
        val list = mutableListOf<MediaQualityEntry>()
        val seenKeys = mutableSetOf<String>()

        for (i in 0 until rawFormats.length()) {
            val f = rawFormats.optJSONObject(i) ?: continue
            val formatId = f.optString("format_id")
            if (formatId.isBlank()) continue

            val directUrl = f.optString("url").ifBlank { f.optString("manifest_url") }
            val ext = f.optString("ext", "mp4")
            val rawHeight = f.optInt("height", 0)
            val vcodec = f.optString("vcodec", "none")
            val acodec = f.optString("acodec", "none")
            val resolution = f.optString("resolution")
            val formatNote = f.optString("format_note")

            // Parse exact size with fallback to URL clen and Content-Length HTTP probe
            val explicitFilesize = f.optLong("filesize").takeIf { it > 0 } ?: -1L

            // Calculate estimated bitrate size for temporary/internal reference in code
            val bitrateBps = when {
                f.optDouble("bitrate", 0.0) > 0.0 -> {
                    val b = f.optDouble("bitrate")
                    if (b > 100_000.0) b else b * 1000.0
                }

                f.optDouble("tbr", 0.0) > 0.0 -> f.optDouble("tbr") * 1000.0
                f.optDouble("vbr", 0.0) > 0.0 -> {
                    val v = f.optDouble("vbr")
                    val a = if (f.has("abr")) f.optDouble("abr", 128.0) else 128.0
                    (if (v > 100_000.0) v else v * 1000.0) + (if (a > 100_000.0) a else a * 1000.0)
                }

                else -> 0.0
            }
            val estimatedFromBitrate = if (videoDuration > 0 && bitrateBps > 0.0) {
                ((bitrateBps / 8.0) * videoDuration).toLong()
            } else -1L

            val clenFromUrl =
                Regex("""[?&]clen=(\d+)""").find(directUrl)?.groupValues?.get(1)?.toLongOrNull()
                    ?: -1L

            // Only expose verified exact size (filesize, URL clen, or HTTP HEAD probe).
            // Do not expose unconfirmed estimates (like 2.9 GB); let unverified streams remain Unknown (-1L)
            val filesize = when {
                explicitFilesize > 0L -> explicitFilesize
                clenFromUrl > 0L -> clenFromUrl
                else -> getCachedContentLength(directUrl.ifBlank { trimmedUrl })
            }

            val isAudioOnly = (vcodec.equals("none", true) || resolution.contains(
                "audio only",
                true
            ) || formatNote.contains("audio", true)) &&
                    (!acodec.equals("none", true) || ext in listOf(
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
                            directUrl = directUrl.ifBlank { trimmedUrl },
                            format = ext,
                            size = filesize,
                            isSelected = false,
                            formatId = formatId,
                            originalUrl = trimmedUrl,
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
            } else if (formatId.equals("hd", true) || formatNote.contains("hd", true)) {
                rank = 725
                label = "HD Quality (Direct ${ext.uppercase()})"
            } else if (formatId.equals("sd", true) || formatNote.contains("sd", true)) {
                rank = 365
                label = "SD Quality (Direct ${ext.uppercase()})"
            }

            if (label != null) {
                val key = "video_${rank}_${ext.lowercase()}"
                val protocol = f.optString("protocol")
                val isHls = directUrl.contains(".m3u8", ignoreCase = true) ||
                        protocol.contains("m3u8", ignoreCase = true) ||
                        formatId.startsWith("hls", ignoreCase = true)
                val isDirectMp4 =
                    !isHls && (ext.equals("mp4", ignoreCase = true) || directUrl.contains(
                        ".mp4",
                        ignoreCase = true
                    ))

                val existingIndex = list.indexOfFirst {
                    it.qualityLabel == label && it.format.equals(ext, ignoreCase = true)
                }
                if (existingIndex == -1) {
                    seenKeys.add(key)
                    list.add(
                        MediaQualityEntry(
                            index = 0,
                            qualityLabel = label,
                            directUrl = directUrl.ifBlank { trimmedUrl },
                            format = ext,
                            size = filesize,
                            isSelected = false,
                            formatId = formatId,
                            originalUrl = trimmedUrl,
                            videoTitle = videoTitle,
                            durationSeconds = videoDuration
                        )
                    )
                } else if (isDirectMp4) {
                    val existing = list[existingIndex]
                    val existingIsHls = existing.directUrl.contains(".m3u8", ignoreCase = true) ||
                            existing.formatId?.startsWith("hls", ignoreCase = true) == true
                    if (existingIsHls) {
                        list[existingIndex] = MediaQualityEntry(
                            index = 0,
                            qualityLabel = label,
                            directUrl = directUrl.ifBlank { trimmedUrl },
                            format = ext,
                            size = filesize,
                            isSelected = false,
                            formatId = formatId,
                            originalUrl = trimmedUrl,
                            videoTitle = videoTitle,
                            durationSeconds = videoDuration
                        )
                    }
                }
            }
        }

        // If list is empty (e.g. direct mp4 or single stream sites)
        if (list.isEmpty()) {
            val singleUrl = json.optString("url")
            if (singleUrl.isNotBlank()) {
                val ext = json.optString("ext", "mp4")
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "Best Quality (${ext.uppercase()})",
                        directUrl = singleUrl,
                        format = ext,
                        size = json.optLong("filesize").takeIf { it > 0 }
                            ?: getCachedContentLength(singleUrl),
                        isSelected = true,
                        formatId = json.optString("format_id", "best"),
                        originalUrl = trimmedUrl,
                        videoTitle = videoTitle,
                        durationSeconds = videoDuration
                    )
                )
            }
        }

        // Sort descending: 4K -> 2K -> 1080p -> HD -> 720p -> 540p -> 480p -> SD -> 360p -> 240p -> 144p -> Audio (matching JVM Desktop)
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

        list
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

    val ext = if (isAudio) "mp3" else "%(ext)s"
    val outputTemplate = File(outDir, "$cleanName.$ext").absolutePath
    request.addOption("-o", outputTemplate)
    request.addOption("--no-playlist") // a watch?v=...&list=... link is one video, not the whole mix

    if (isAudio) {
        request.addOption("-x")
        request.addOption("--audio-format", "mp3")
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
                    val dlBytes = when {
                        sizeUnit.contains("gib") || sizeUnit.contains("gb") -> (sizeNum * 1024 * 1024 * 1024).toLong()
                        sizeUnit.contains("mib") || sizeUnit.contains("mb") -> (sizeNum * 1024 * 1024).toLong()
                        sizeUnit.contains("kib") || sizeUnit.contains("kb") -> (sizeNum * 1024).toLong()
                        else -> sizeNum.toLong()
                    }
                    if (dlBytes > 0) downloadedBytes = dlBytes

                    // Calculate clipping speed from ffmpeg bitrate and speed multiplier
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

                    // Rolling delta calculation from output byte growth
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSpeedCalcTimeMs
                    if (elapsed >= 300L) {
                        val delta = dlBytes - lastSpeedCalcBytes
                        if (delta > 0L) {
                            val instant = (delta * 1000L) / elapsed
                            rollingCalculatedSpeed = if (rollingCalculatedSpeed <= 0L) instant
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
                        val parsedBytes = when {
                            sizeLower.contains("gib") || sizeLower.contains("gb") -> (sizeNum * 1024 * 1024 * 1024).toLong()
                            sizeLower.contains("mib") || sizeLower.contains("mb") -> (sizeNum * 1024 * 1024).toLong()
                            sizeLower.contains("kib") || sizeLower.contains("kb") -> (sizeNum * 1024).toLong()
                            else -> sizeNum.toLong()
                        }

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

                        downloadedBytes = maxOf(diskBytes, computedFromPercent)

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


actual suspend fun inspectPlatformPlaylist(url: String): MediaPlaylist? =
    withContext(Dispatchers.IO) {
        val trimmedUrl = normalizePlaylistUrl(url)
        if (trimmedUrl.isEmpty() || !ensureRuntimeInitialized()) return@withContext null
        val context = PathResolver.appContext ?: return@withContext null
        val request = YTDLRequest(trimmedUrl)
        request.addOption("--flat-playlist")
        request.addOption("-J")
        request.addOption("--no-warnings")
        request.addOption("--yes-playlist")
        FileUtil.getCookieFile(context) { cookiePath ->
            request.addOption("--cookies", cookiePath)
        }
        val response = try {
            RuntimeManager.getInstance().execute(request)
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        parsePlaylistJson(response.out)
    }
