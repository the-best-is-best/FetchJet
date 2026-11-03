@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSNumber

private val probeClients = com.michelle.raouf.fetch_jet.core.network.NetworkClient {
    HttpClient { useNetworkSettings() }
}
private val probeClient get() = probeClients.current

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

actual suspend fun inspectPlatformQualities(
    url: String,
    requestHeaders: Map<String, String>
): List<MediaQualityEntry> =
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
        args.addAll(ytDlpHeaderArgs(requestHeaders, url))
        getIosCookieFile()?.let { cookiePath ->
            args.add("--cookies")
            args.add(cookiePath)
        }
        args.add(trimmedUrl)
        val bridge = IosMediaBridgeRegistry.bridge
        val output = if (bridge != null && bridge.isAvailable()) {
            bridge.captureYtDlp(com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs() + args)
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
            val isHlsFormat = (getStringFromMap(f, "protocol") ?: "").let {
                it.contains(
                    "m3u8",
                    ignoreCase = true
                ) || it == "mhtml"
            } ||
                    directUrl.contains(".m3u8", ignoreCase = true)
            val filesize = when {
                explicitFilesize > 0L -> explicitFilesize
                clenFromUrl > 0L -> clenFromUrl
                isHlsFormat -> -1L
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
                                qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                                    label
                                ),
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
