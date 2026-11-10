package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.network.AppHttp
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getDouble
import com.michelle.raouf.fetch_jet.core.util.getInt
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getObjectArray
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import kotlinx.coroutines.CancellationException

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun safeEncodeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    val sb = StringBuilder()
    for (ch in trimmed) {
        if (ch.code in 33..126) {
            sb.append(ch)
        } else if (ch == ' ') {
            sb.append("%20")
        } else {
            for (b in ch.toString().encodeToByteArray()) {
                val v = b.toInt() and 0xFF
                sb.append('%').append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 15])
            }
        }
    }
    return sb.toString()
}

suspend fun probeContentLength(url: String): Long {
    val safe = safeEncodeUrl(url)
    return try {
        val response = AppHttp.client.head(safe) {
            timeout {
                connectTimeoutMillis = 4000
                socketTimeoutMillis = 4000
                requestTimeoutMillis = 8000
            }
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        }
        val length = response.contentLength() ?: -1L
        if (length > 0L) length else -1L
    } catch (e: CancellationException) {
        throw e
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

suspend fun probeMediaDurationWithFfmpeg(url: String): Long {
    val binary = getExistingFfmpegBinary() ?: (if (isSystemFfmpegInstalled()) "ffmpeg" else return 0L)
    return try {
        val result = winRunProcess(
            listOf(binary, "-hide_banner", "-i", safeEncodeUrl(url)), mergeStderr = true, timeoutMs = 12_000
        )
        parseDurationFromFfmpegOutput(result.stdout)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        0L
    }
}

actual suspend fun inspectPlatformQualities(
    url: String,
    requestHeaders: Map<String, String>
): List<MediaQualityEntry> {
    getExistingYtDlpBinary()
        ?: throw Exception("yt-dlp tool is not found. Please click 'Download Tools' in Settings.")

    val output =
        runYtDlp(
            ytDlpRuntimeArgs() + listOf(
                "-j",
                "--no-warnings",
                "--no-playlist"
            ) + ytDlpHeaderArgs(requestHeaders, url) + listOf("--", url),
            timeoutSec = 90L
        )
            ?: return emptyList()

    val parsedRoot = try {
        MiniJson.parse(output) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    }

    MediaSubtitleStore.record(url, parseSubtitlesFromRoot(parsedRoot))

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
    suspend fun getCachedContentLength(u: String): Long {
        val clean = u.trim()
        if (clean.isBlank()) return -1L
        contentLengthCache[clean]?.let { return it }
        val length = probeContentLength(clean)
        contentLengthCache[clean] = length
        return length
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
        val isHlsFormat = (f.getString("protocol") ?: "").let {
            it.contains(
                "m3u8",
                ignoreCase = true
            ) || it == "mhtml"
        } ||
                directUrl.contains(".m3u8", ignoreCase = true)
        val filesize = when {
            explicitFilesize > 0 -> explicitFilesize
            isHlsFormat -> -1L
            else -> getCachedContentLength(directUrl.ifBlank { url })
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
                        qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                            label
                        ),
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
                        qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                            label
                        ),
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
                        qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                            label
                        ),
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

private fun decodeUnicodeEscapes(input: String): String {
    val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
    return regex.replace(input) { m ->
        try {
            m.groupValues[1].toInt(16).toChar().toString()
        } catch (_: Exception) {
            m.value
        }
    }
}
