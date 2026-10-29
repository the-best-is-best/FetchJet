@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSNumber
import platform.posix.fread
import platform.posix.pclose
import platform.posix.popen

private val probeClient by lazy { HttpClient() }

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private fun cleanEscapedUrl(url: String): String {
    return url.replace("""\/""", "/")
        .replace("""\u0025""", "%")
        .replace("""\u0026""", "&")
        .replace("&amp;", "&")
        .replace("""\u003D""", "=")
        .replace("""\u003d""", "=")
        .trim()
}

private suspend fun resolveEffectiveMediaUrl(url: String): String {
    val current = url.trim()
    val isRedirectLink = current.contains("facebook.com/share/", ignoreCase = true) ||
            current.contains("fb.watch/", ignoreCase = true) ||
            current.contains("m.facebook.com/", ignoreCase = true) ||
            current.contains("tiktok.com/t/", ignoreCase = true) ||
            current.contains("vm.tiktok.com/", ignoreCase = true) ||
            current.contains("vt.tiktok.com/", ignoreCase = true) ||
            current.contains("t.co/", ignoreCase = true)

    if (!isRedirectLink) return current

    return try {
        val response = probeClient.get(current) {
            header(HttpHeaders.UserAgent, DESKTOP_USER_AGENT)
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        }
        val finalUrl = response.call.request.url.toString()
        if (finalUrl.isNotBlank() && !finalUrl.contains("/login") && !finalUrl.contains("checkpoint")) {
            if (finalUrl.contains("m.facebook.com")) {
                finalUrl.replace("m.facebook.com", "www.facebook.com")
            } else {
                finalUrl
            }
        } else {
            val body = response.bodyAsText()
            val canonicalMatch = Regex(
                """<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(body)
                ?: Regex(
                    """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(body)
            val canonical = canonicalMatch?.groupValues?.get(1)?.let { cleanEscapedUrl(it) }
            if (!canonical.isNullOrBlank() && !canonical.contains("/login") && !canonical.contains("checkpoint")) {
                if (canonical.contains("m.facebook.com")) canonical.replace(
                    "m.facebook.com",
                    "www.facebook.com"
                ) else canonical
            } else {
                current
            }
        }
    } catch (_: Exception) {
        current
    }
}

private suspend fun extractDirectSocialMediaStreams(url: String): List<MediaQualityEntry> {
    return try {
        val effectiveUrl = resolveEffectiveMediaUrl(url)
        val domain = effectiveUrl.lowercase()
        val response = probeClient.get(effectiveUrl) {
            header(HttpHeaders.UserAgent, DESKTOP_USER_AGENT)
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            if (domain.contains("facebook.com") || domain.contains("fb.watch")) {
                header("Referer", "https://www.facebook.com/")
                header("Origin", "https://www.facebook.com")
            } else if (domain.contains("instagram.com")) {
                header("Referer", "https://www.instagram.com/")
            }
        }
        val body = response.bodyAsText()
        val list = mutableListOf<MediaQualityEntry>()

        val titleMatch = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(body)
            ?: Regex(
                """<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(body)
        val rawTitle = titleMatch?.groupValues?.get(1)?.trim()
        val videoTitle = if (!rawTitle.isNullOrBlank()) decodeUnicodeEscapes(rawTitle) else null

        if (domain.contains("facebook.com") || domain.contains("fb.watch")) {
            val hdPatterns = listOf(
                Regex("""["']playable_url_quality_hd["']\s*:\s*["']([^"']+)["']"""),
                Regex("""["']browser_native_hd_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""hd_src\s*:\s*["']([^"']+)["']"""),
                Regex("""hd_src_no_ratelimit\s*:\s*["']([^"']+)["']""")
            )
            val sdPatterns = listOf(
                Regex("""["']playable_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""["']browser_native_sd_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""sd_src\s*:\s*["']([^"']+)["']"""),
                Regex("""sd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),
                Regex("""video_src\s*:\s*["']([^"']+)["']"""),
                Regex(
                    """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ),
                Regex("""(https?:\\?/\\?/[^"'\s]*fbcdn\.net[^"'\s]*\.mp4[^"'\s]*)"""),
                Regex("""(https?:\\?/\\?/[^"'\s]*video[^"'\s]*\.fbcdn\.net[^"'\s]*)""")
            )

            var hdUrl: String? = null
            for (p in hdPatterns) {
                val m = p.find(body)
                if (m != null) {
                    val cleaned = cleanEscapedUrl(m.groupValues[1])
                    if (cleaned.isNotBlank()) {
                        hdUrl = cleaned
                        break
                    }
                }
            }

            var sdUrl: String? = null
            for (p in sdPatterns) {
                val m = p.find(body)
                if (m != null) {
                    val cleaned = cleanEscapedUrl(m.groupValues[1])
                    if (cleaned.isNotBlank() && cleaned != hdUrl) {
                        sdUrl = cleaned
                        break
                    }
                }
            }

            if (hdUrl != null) {
                val size = probeContentLength(hdUrl)
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "HD Quality (720p - MP4)",
                        directUrl = hdUrl,
                        format = "mp4",
                        size = size,
                        isSelected = true,
                        formatId = "hd",
                        originalUrl = url,
                        videoTitle = videoTitle ?: "Facebook Video HD",
                        durationSeconds = 0L
                    )
                )
            }

            if (sdUrl != null) {
                val size = probeContentLength(sdUrl)
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "SD Quality (480p - MP4)",
                        directUrl = sdUrl,
                        format = "mp4",
                        size = size,
                        isSelected = list.isEmpty(),
                        formatId = "sd",
                        originalUrl = url,
                        videoTitle = videoTitle ?: "Facebook Video SD",
                        durationSeconds = 0L
                    )
                )
            }
        } else {
            val ogVideoMatch = Regex(
                """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(body)
                ?: Regex(
                    """<meta\s+name=["']twitter:player:stream["']\s+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(body)
                ?: Regex("""(https?:\\?/\\?/[^"'\s]+\.mp4[^"'\s]*)""").find(body)
            if (ogVideoMatch != null) {
                val videoUrl = cleanEscapedUrl(ogVideoMatch.groupValues[1])
                if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                    val size = probeContentLength(videoUrl)
                    list.add(
                        MediaQualityEntry(
                            index = 0,
                            qualityLabel = "Best Quality (MP4)",
                            directUrl = videoUrl,
                            format = "mp4",
                            size = size,
                            isSelected = true,
                            formatId = "best",
                            originalUrl = url,
                            videoTitle = videoTitle,
                            durationSeconds = 0L
                        )
                    )
                }
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private fun escapeShellArg(arg: String): String {
    return "'" + arg.replace("'", "'\\''") + "'"
}

@OptIn(ExperimentalForeignApi::class)
fun executeCommandStreaming(
    cmd: String,
    onLine: ((String) -> Unit)? = null
): Int {
    val toolsDir = getIosToolsDirectory()
    val pathEnv =
        "PATH=\"$toolsDir:/var/jb/usr/bin:/usr/local/bin:/usr/bin:/bin:/opt/homebrew/bin:/var/jb/usr/local/bin:\$PATH\" PYTHONUNBUFFERED=1"
    val fullCmd = "$pathEnv $cmd 2>&1"

    val fp = popen(fullCmd, "r") ?: return -1
    try {
        memScoped {
            val buffer = allocArray<ByteVar>(1024)
            val lineSb = StringBuilder()
            while (true) {
                val bytesRead = fread(buffer, 1u, 1024u, fp).toLong()
                if (bytesRead <= 0L) break
                for (i in 0 until bytesRead) {
                    val c = (buffer[i.toInt()].toInt() and 0xFF).toChar()
                    if (c == '\r' || c == '\n') {
                        if (lineSb.isNotEmpty()) {
                            val line = lineSb.toString().trim()
                            if (line.isNotEmpty()) {
                                onLine?.invoke(line)
                            }
                            lineSb.clear()
                        }
                    } else {
                        lineSb.append(c)
                    }
                }
            }
            if (lineSb.isNotEmpty()) {
                val line = lineSb.toString().trim()
                if (line.isNotEmpty()) {
                    onLine?.invoke(line)
                }
                lineSb.clear()
            }
        }
    } finally {
        val status = pclose(fp)
        return status
    }
}

fun runYtDlpCapture(args: List<String>): String? {
    val ytdlp = getExistingYtDlpBinary() ?: return null
    val python = getExistingPythonBinary()

    val cmdList = mutableListOf<String>()
    if (python != null && (ytdlp.endsWith(".py") || !isExecutable(ytdlp))) {
        cmdList.add(escapeShellArg(python))
    }
    cmdList.add(escapeShellArg(ytdlp))
    cmdList.addAll(args.map { escapeShellArg(it) })

    val fullCmd = cmdList.joinToString(" ")
    val sb = StringBuilder()
    val code = executeCommandStreaming(fullCmd) { line ->
        sb.append(line).append("\n")
    }
    return if (code == 0 || sb.isNotEmpty()) sb.toString().trim() else null
}

private suspend fun probeContentLength(urlStr: String): Long {
    val clean = urlStr.trim()
    if (clean.isBlank()) return -1L
    return try {
        val response = probeClient.head(clean) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")
        }
        val len = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        if (len > 0L) len else -1L
    } catch (_: Exception) {
        -1L
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

private fun probeMediaDurationWithFfmpeg(url: String): Long {
    val bridge = IosMediaBridgeRegistry.bridge
    if (bridge != null && bridge.isAvailable()) {
        val dur = bridge.probeMediaDuration(url)
        if (dur > 0L) return dur
    }
    val ffmpeg = getExistingFfmpegBinary() ?: return 0L
    val cmd = "${escapeShellArg(ffmpeg)} -hide_banner -i ${escapeShellArg(url)}"
    val sb = StringBuilder()
    executeCommandStreaming(cmd) { sb.append(it) }
    val match =
        Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""").find(sb.toString()) ?: return 0L
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds =
        match.groupValues[3].toDoubleOrNull()?.let { kotlin.math.round(it).toLong() } ?: 0L
    return (hours * 3600L) + (minutes * 60L) + seconds
}


/**
 * iOS cannot spawn processes, so yt-dlp only works through the Swift bridge once it runs
 * Python in-process. Until that bridge reports available, fail immediately with a clear
 * message rather than spinning on a `popen` that can never succeed.
 */
private fun requireIosMediaEngine() {
    val bridge = IosMediaBridgeRegistry.bridge
    if (bridge == null || !bridge.isAvailable()) {
        throw UnsupportedOperationException(
            "Media extraction is not available on iOS yet. It needs the in-app Python engine (coming in a later build)."
        )
    }
}

actual suspend fun resolvePlatformStream(url: String): String? = withContext(Dispatchers.IO) {
    requireIosMediaEngine()
    val args = mutableListOf("-g", "--no-warnings", "--no-playlist", "--geo-bypass")
    getIosCookieFile()?.let { cookiePath ->
        args.add("--cookies")
        args.add(cookiePath)
    }
    args.add(url)
    val bridge = IosMediaBridgeRegistry.bridge
    val output = if (bridge != null && bridge.isAvailable()) {
        // In-process engine only: when it fails, surface its error instead of falling back to a
        // (never working on iOS) popen of a yt-dlp binary, whose shell noise used to look like output.
        bridge.captureYtDlp(args)
    } else {
        runYtDlpCapture(args)
    }
    if (output == null) {
        val reason = bridge?.lastErrorText()?.lineSequence()
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotBlank() }
        throw Exception(reason ?: "yt-dlp returned no stream URL")
    }
    output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
}

private fun getValueFromMap(map: Map<*, *>?, key: String): Any? {
    if (map == null) return null
    val direct = map[key]
    if (direct != null) return direct
    for ((k, v) in map) {
        if (k?.toString() == key) return v
    }
    return null
}

private fun getStringFromMap(map: Map<*, *>?, key: String): String? {
    val v = getValueFromMap(map, key) ?: return null
    val s = v.toString().trim()
    return if (s.isNotEmpty()) s else null
}

private fun getIntFromMap(map: Map<*, *>?, key: String): Int? {
    val v = getValueFromMap(map, key) ?: return null
    if (v is Number) return v.toInt()
    if (v is NSNumber) return v.intValue
    return v.toString().toIntOrNull()
}

private fun getLongFromMap(map: Map<*, *>?, key: String): Long? {
    val v = getValueFromMap(map, key) ?: return null
    if (v is Number) return v.toLong()
    if (v is NSNumber) return v.longLongValue
    return v.toString().toDoubleOrNull()?.toLong()
}

actual suspend fun inspectPlatformQualities(url: String): List<MediaQualityEntry> =
    withContext(Dispatchers.IO) {
        requireIosMediaEngine()
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return@withContext emptyList()

        val args = mutableListOf(
            "-j",
            "--no-warnings",
            "--no-playlist",   // a watch?v=...&list=... link is one video, not the whole mix
            "--geo-bypass"
        )
        getIosCookieFile()?.let { cookiePath ->
            args.add("--cookies")
            args.add(cookiePath)
        }
        args.add(trimmedUrl)
        val bridge = IosMediaBridgeRegistry.bridge
        val output = if (bridge != null && bridge.isAvailable()) {
            bridge.captureYtDlp(args)
        } else {
            runYtDlpCapture(args)
        }
        if (output == null) {
            val reason = bridge?.lastErrorText()?.lineSequence()
                ?.map { it.trim() }
                ?.lastOrNull { it.isNotBlank() }
            throw Exception(reason ?: "yt-dlp returned no metadata")
        }

        val jsonLine = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: output.indexOf('{').takeIf { it != -1 }?.let { start ->
                output.lastIndexOf('}').takeIf { it > start }?.let { end ->
                    output.substring(start, end + 1)
                }
            }
            ?: output.trim()

        val parsedRoot: Map<*, *>? = try {
            MiniJson.parse(jsonLine) as? Map<*, *>
        } catch (_: Exception) {
            null
        }

        if (parsedRoot == null) {
            throw Exception("yt-dlp returned no video metadata for this URL")
        }

        val videoTitle = getStringFromMap(parsedRoot, "title")?.let { decodeUnicodeEscapes(it) }
            ?: getStringFromMap(parsedRoot, "fulltitle")?.let { decodeUnicodeEscapes(it) }
            ?: Regex(""""title"\s*:\s*"([^"]+)"""").find(output)?.groupValues?.get(1)
                ?.let { decodeUnicodeEscapes(it) }

        var videoDuration: Long = getLongFromMap(parsedRoot, "duration")
            ?: Regex(""""duration"\s*:\s*([0-9.]+)""").find(output)?.groupValues?.get(1)
                ?.toDoubleOrNull()?.toLong()
            ?: 0L

        if (videoDuration <= 0L) {
            videoDuration = probeMediaDurationWithFfmpeg(trimmedUrl)
        }

        val contentLengthCache = mutableMapOf<String, Long>()
        suspend fun getCachedContentLength(u: String): Long {
            val clean = u.trim()
            if (clean.isBlank()) return -1L
            return contentLengthCache.getOrPut(clean) { probeContentLength(clean) }
        }

        val rawFormats = (getValueFromMap(parsedRoot, "formats") as? List<*>) ?: emptyList<Any?>()
        val list = mutableListOf<MediaQualityEntry>()
        val seenKeys = mutableSetOf<String>()

        for (rawF in rawFormats) {
            val f = rawF as? Map<*, *> ?: continue
            val formatId = getStringFromMap(f, "format_id") ?: continue
            if (formatId.isBlank()) continue

            val directUrl = getStringFromMap(f, "url") ?: getStringFromMap(f, "manifest_url") ?: ""
            val ext = getStringFromMap(f, "ext") ?: "mp4"
            val rawHeight = getIntFromMap(f, "height") ?: 0
            val vcodec = getStringFromMap(f, "vcodec") ?: "none"
            val acodec = getStringFromMap(f, "acodec") ?: "none"
            val resolution = getStringFromMap(f, "resolution") ?: ""
            val formatNote = getStringFromMap(f, "format_note") ?: ""
            val explicitFilesize = getLongFromMap(f, "filesize")
                ?: getLongFromMap(f, "filesize_approx")
                ?: -1L

            val clenFromUrl =
                Regex("""[?&]clen=(\d+)""").find(directUrl)?.groupValues?.get(1)?.toLongOrNull()
                    ?: -1L
            val filesize = when {
                explicitFilesize > 0L -> explicitFilesize
                clenFromUrl > 0L -> clenFromUrl
                else -> getCachedContentLength(directUrl.ifBlank { trimmedUrl })
            }

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
                val protocol = getStringFromMap(f, "protocol") ?: ""
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

        if (list.isEmpty()) {
            val singleUrl = getStringFromMap(parsedRoot, "url") ?: ""
            if (singleUrl.isNotBlank()) {
                val ext = getStringFromMap(parsedRoot, "ext") ?: "mp4"
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "Best Quality (${ext.uppercase()})",
                        directUrl = singleUrl,
                        format = ext,
                        size = getLongFromMap(parsedRoot, "filesize") ?: getCachedContentLength(
                            singleUrl
                        ),
                        isSelected = true,
                        formatId = getStringFromMap(parsedRoot, "format_id") ?: "best",
                        originalUrl = trimmedUrl,
                        videoTitle = videoTitle,
                        durationSeconds = videoDuration
                    )
                )
            } else {
                val bestStream = resolvePlatformStream(trimmedUrl)
                if (!bestStream.isNullOrBlank()) {
                    list.add(
                        MediaQualityEntry(
                            index = 0,
                            qualityLabel = "Best Quality (yt-dlp Native - MP4)",
                            directUrl = bestStream,
                            format = "mp4",
                            size = -1L,
                            isSelected = true,
                            originalUrl = trimmedUrl,
                            videoTitle = videoTitle,
                            durationSeconds = videoDuration
                        )
                    )
                } else {
                    val clean = trimmedUrl.substringBefore('?')
                    val ext = clean.substringAfterLast('.', "").lowercase()
                    val isKnownMedia = ext in listOf(
                        "mp3", "m4a", "flac", "wav", "aac", "ogg", "opus",
                        "mp4", "mkv", "webm", "avi", "mov"
                    )
                    if (isKnownMedia) {
                        val isAudio =
                            ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
                        val dur =
                            if (videoDuration > 0L) videoDuration else probeMediaDurationWithFfmpeg(
                                trimmedUrl
                            )
                        val size = getCachedContentLength(trimmedUrl)
                        val label =
                            if (isAudio) "Audio Only (${ext.uppercase()})" else "Direct Media (${ext.uppercase()})"
                        val title =
                            videoTitle ?: clean.substringAfterLast('/').substringBeforeLast('.')
                        list.add(
                            MediaQualityEntry(
                                index = 0,
                                qualityLabel = label,
                                directUrl = trimmedUrl,
                                format = ext,
                                size = size,
                                isSelected = true,
                                originalUrl = trimmedUrl,
                                videoTitle = title,
                                durationSeconds = dur
                            )
                        )
                    }
                }
            }
        }

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
    requireIosMediaEngine()
    val bridge = IosMediaBridgeRegistry.bridge
    val isBridgeAvailable = bridge?.isAvailable() == true
    val ytdlp = getExistingYtDlpBinary()
    if (!isBridgeAvailable && ytdlp == null) return@withContext false
    val python = getExistingPythonBinary()
    val ffmpeg = getExistingFfmpegBinary()
    val hasFfmpeg = ffmpeg != null

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

    if (isAudio) {
        if (hasFfmpeg) {
            rawArgs.add("-x")
            rawArgs.add("--audio-format")
            rawArgs.add("mp3")
        }
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
            rawArgs.add("--ffmpeg-location")
            rawArgs.add(ffmpeg.substringBeforeLast('/'))
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
    rawArgs.add("--no-playlist")
    rawArgs.add("--geo-bypass")
    if (url.contains("youtube.com", ignoreCase = true) || url.contains(
            "youtu.be",
            ignoreCase = true
        )
    ) {
        rawArgs.add("--extractor-args")
        rawArgs.add("youtube:player_client=android,web")
    }
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
                val dlBytes = when {
                    sizeUnit.contains("gib") || sizeUnit.contains("gb") -> (sizeNum * 1024 * 1024 * 1024).toLong()
                    sizeUnit.contains("mib") || sizeUnit.contains("mb") -> (sizeNum * 1024 * 1024).toLong()
                    sizeUnit.contains("kib") || sizeUnit.contains("kb") -> (sizeNum * 1024).toLong()
                    else -> sizeNum.toLong()
                }
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
                    }

                    val effectiveTotal =
                        if (recordedTotalBytes > 0L) recordedTotalBytes else temporaryEstimatedBytes
                    val computedDownloaded =
                        if (effectiveTotal > 0L) (effectiveTotal * (percent / 100f)).toLong() else 0L
                    downloadedBytes = computedDownloaded

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
        cmdList.addAll(rawArgs.map { escapeShellArg(it) })
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
        bridge.executeYtDlpCancellable(runId, args) { _, _, line -> onLine(line) }
    } finally {
        finished = true
        watchdog.cancel()
    }
}


actual suspend fun inspectPlatformPlaylist(url: String): MediaPlaylist? =
    withContext(Dispatchers.IO) {
        requireIosMediaEngine()
        val trimmedUrl = normalizePlaylistUrl(url)
        if (trimmedUrl.isEmpty()) return@withContext null
        val args = mutableListOf("--flat-playlist", "-J", "--no-warnings", "--yes-playlist")
        getIosCookieFile()?.let { cookiePath ->
            args.add("--cookies")
            args.add(cookiePath)
        }
        args.add(trimmedUrl)
        val bridge = IosMediaBridgeRegistry.bridge
        val output = if (bridge != null && bridge.isAvailable()) {
            bridge.captureYtDlp(args)
        } else {
            runYtDlpCapture(args)
        }
        output?.let { parsePlaylistJson(it) }
    }
