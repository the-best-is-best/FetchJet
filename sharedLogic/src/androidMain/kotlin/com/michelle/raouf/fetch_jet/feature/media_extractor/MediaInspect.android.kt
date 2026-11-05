package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.util.FileUtil
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
        val conn = com.michelle.raouf.fetch_jet.core.network.openAppConnection(uri)
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

private val contentLengthCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
private fun getCachedContentLength(u: String): Long {
    val clean = u.trim()
    if (clean.isBlank()) return -1L
    if (contentLengthCache.size > 500) contentLengthCache.clear()
    return contentLengthCache.getOrPut(clean) {
        val started = System.currentTimeMillis()
        probeContentLength(clean).also { len ->
            AppLog.d {
                "Inspect: size probe ${System.currentTimeMillis() - started}ms -> $len ${
                    clean.substringBefore(
                        '?'
                    ).take(120)
                }"
            }
        }
    }
}

actual suspend fun inspectPlatformQualities(
    url: String,
    requestHeaders: Map<String, String>
): List<MediaQualityEntry> =
    withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val initStarted = System.currentTimeMillis()
        if (trimmedUrl.isEmpty() || !ensureRuntimeInitialized()) {
            return@withContext emptyList()
        }
        AppLog.d { "Inspect: runtime ready in ${System.currentTimeMillis() - initStarted}ms" }

        val context = PathResolver.appContext ?: return@withContext emptyList()

        // Match Desktop's exact yt-dlp arguments (-j --no-warnings) without forcing restrictive player_clients
        val request = YTDLRequest(trimmedUrl)
        request.addOption("-j")
        request.addOption("--no-warnings")
        request.addOption("--no-playlist")
        ytDlpHeaderArgs(requestHeaders, url).chunked(2)
            .forEach { (k, v) -> request.addOption(k, v) }
        FileUtil.getCookieFile(context) { cookiePath ->
            request.addOption("--cookies", cookiePath)
        }

        val timed = AppLog.verbose
        if (timed) request.addOption("-v")
        val startedAt = System.currentTimeMillis()
        val response = try {
            if (timed) {
                AppLog.d { "Inspect: yt-dlp start $trimmedUrl" }
                RuntimeManager.getInstance()
                    .execute(request, redirectErrorStream = true) { _, _, line ->
                        if (!line.startsWith("{")) {
                            AppLog.d {
                                "Inspect +${System.currentTimeMillis() - startedAt}ms ${
                                    if (line.contains(
                                            "Command-line config"
                                        )
                                    ) line else line.take(300)
                                }"
                            }
                        }
                    }
            } else {
                RuntimeManager.getInstance().execute(request)
            }
        } catch (e: Exception) {
            if (timed) AppLog.d {
                "Inspect: yt-dlp failed after ${System.currentTimeMillis() - startedAt}ms: ${
                    e.message?.take(
                        300
                    )
                }"
            }
            null
        } ?: return@withContext emptyList()
        if (timed) AppLog.d { "Inspect: yt-dlp done in ${System.currentTimeMillis() - startedAt}ms (exit ${response.exitCode})" }

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
        val parseStarted = System.currentTimeMillis()
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

            val clenFromUrl =
                Regex("""[?&]clen=(\d+)""").find(directUrl)?.groupValues?.get(1)?.toLongOrNull()
                    ?: -1L

            // Only expose verified exact size (filesize, URL clen, or HTTP HEAD probe).
            // Do not expose unconfirmed estimates (like 2.9 GB); let unverified streams remain Unknown (-1L)
            val isHlsFormat = f.optString("protocol")
                .let { it.contains("m3u8", ignoreCase = true) || it == "mhtml" } ||
                    directUrl.contains(".m3u8", ignoreCase = true)
            val filesize = when {
                explicitFilesize > 0L -> explicitFilesize
                clenFromUrl > 0L -> clenFromUrl
                isHlsFormat -> -1L
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
                            qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                                label
                            ),
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
                            existing.directUrl.contains("/hls", ignoreCase = true) ||
                            existing.directUrl.contains(
                                "manifest.googlevideo",
                                ignoreCase = true
                            ) ||
                            existing.formatId?.startsWith("hls", ignoreCase = true) == true
                    if (existingIsHls) {
                        list[existingIndex] = MediaQualityEntry(
                            index = 0,
                            qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                                label
                            ),
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
        AppLog.d { "Inspect: ${rawFormats.length()} formats parsed in ${System.currentTimeMillis() - parseStarted}ms" }

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
