package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

internal suspend fun MediaStreamDownloader.inspectHlsVariants(
    url: String,
    requestHeaders: Map<String, String>
): List<MediaQualityEntry>? {
    try {
        val variants = HlsDownloader.listVariants(client, url.trim(), requestHeaders)
        if (variants.isNotEmpty()) {
            // One rendition's playlist gives the duration (same for all); size follows
            // from the bandwidth. Enables the clip range and a realistic total.
            val duration =
                HlsDownloader.durationSeconds(client, variants.first().url, requestHeaders)
            return variants.mapIndexed { i, v ->
                val codec = when {
                    v.codecs.contains("avc1") -> " · H.264"
                    v.codecs.contains("hvc1") || v.codecs.contains("hev1") -> " · H.265"
                    else -> ""
                }
                MediaQualityEntry(
                    index = i,
                    qualityLabel = com.michelle.raouf.fetch_jet.domain.model.withQualityTag(
                        v.label
                    ) + codec,
                    directUrl = v.url,
                    format = "mp4",
                    size = if (duration > 0 && v.bandwidth > 0) duration * v.bandwidth / 8 else -1L,
                    isSelected = i == 0,
                    formatId = HlsDownloader.FORMAT_ID,
                    originalUrl = v.url,
                    durationSeconds = duration
                )
            }
        }
    } catch (e: Exception) {
        lastHlsError = e.message
    }
    return null
}

internal suspend fun MediaStreamDownloader.inspectPlatformWithCookieRetry(
    effectiveUrl: String,
    pageHeaders: Map<String, String>
): List<MediaQualityEntry> {
    val requestHeaders = com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.effective(
        effectiveUrl,
        pageHeaders
    )
    return run {
        val withHeaders = try {
            inspectPlatformQualities(effectiveUrl, requestHeaders)
        } catch (e: Exception) {
            com.michelle.raouf.fetch_jet.core.log.AppLog.d { "MediaStream: inspect failed with headers ${requestHeaders.keys}: ${e.message}" }
            emptyList()
        }
        com.michelle.raouf.fetch_jet.core.log.AppLog.d {
            "MediaStream: inspect ${effectiveUrl.take(80)} headers=${requestHeaders.keys} -> ${withHeaders.size} formats"
        }
        // "Good" = at least one real resolution (720p, 1080p…). A logged-in Facebook
        // page gives yt-dlp a single nameless "sd/hd" entry; the same page without the
        // cookie gives every quality.
        fun good(list: List<MediaQualityEntry>) =
            list.any { Regex("""\b\d{3,4}p\b|\b4K\b""").containsMatchIn(it.qualityLabel) }
        if (requestHeaders.isEmpty() || good(withHeaders)) withHeaders
        else {
            val plain = try {
                com.michelle.raouf.fetch_jet.core.log.AppLog.d { "MediaStream: weak result with the browser headers, retrying without them" }
                inspectPlatformQualities(effectiveUrl, emptyMap())
            } catch (_: Exception) {
                emptyList()
            }
            com.michelle.raouf.fetch_jet.core.log.AppLog.d { "MediaStream: inspect without headers -> ${plain.size} formats" }
            if (plain.size > withHeaders.size) {
                // Downloads of this site must not send the cookie either.
                com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.markCookieUnhelpful(
                    effectiveUrl
                )
                plain
            } else withHeaders
        }
    }
}

internal fun withTitleAndDuration(
    platformQualities: List<MediaQualityEntry>,
    resolvedTitle: String?
): List<MediaQualityEntry> {
    val maxDur = platformQualities.maxOfOrNull { it.durationSeconds } ?: 0L
    // Facebook lists nameless "sd"/"hd" progressive files next to the real
    // 360p/720p/1080p ones: with numbered qualities present they are only noise.
    val numbered =
        platformQualities.any { Regex("""\b\d{3,4}p\b|\b4K\b""").containsMatchIn(it.qualityLabel) }
    val cleaned = if (numbered) platformQualities.filterNot {
        it.qualityLabel.startsWith("HD Quality (Direct") || it.qualityLabel.startsWith("SD Quality (Direct")
    } else platformQualities
    return cleaned.map {
        var entry = it
        if (entry.videoTitle.isNullOrBlank() && resolvedTitle != null) {
            entry = entry.copy(videoTitle = resolvedTitle)
        }
        if (entry.durationSeconds <= 0L && maxDur > 0L) {
            entry = entry.copy(durationSeconds = maxDur)
        }
        entry
    }
}

internal suspend fun MediaStreamDownloader.inspectYouTubeFallbacks(
    effectiveUrl: String,
    list: MutableList<MediaQualityEntry>,
    startIndex: Int
): Int {
    var index = startIndex
    val videoId = extractYouTubeVideoId(effectiveUrl)

    // 1. Try Piped API
    if (!videoId.isNullOrBlank()) {
        try {
            val apiUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
            val response: HttpResponse = client.get(apiUrl) {
                header(HttpHeaders.UserAgent, desktopUserAgent)
                timeout {
                    requestTimeoutMillis = 3000L
                    connectTimeoutMillis = 3000L
                    socketTimeoutMillis = 3000L
                }
            }
            if (response.status.isSuccess()) {
                val json = response.bodyAsText()
                val pipedEntries = parsePipedQualities(json)
                for (entry in pipedEntries) {
                    if (list.none { it.directUrl == entry.directUrl || it.qualityLabel == entry.qualityLabel }) {
                        list.add(
                            entry.copy(
                                index = index++,
                                isSelected = list.isEmpty()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // 2. Query Cobalt API across quality tiers (1080, 720, 480, 360)
    val qualitiesToTry = listOf("1080", "720", "480", "360")
    for (q in qualitiesToTry) {
        try {
            val cobaltUrl = "https://api.cobalt.tools/api/json"
            val response: HttpResponse = client.post(cobaltUrl) {
                header(HttpHeaders.UserAgent, desktopUserAgent)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                timeout {
                    requestTimeoutMillis = 3000L
                    connectTimeoutMillis = 3000L
                    socketTimeoutMillis = 3000L
                }
                setBody("""{"url":"$effectiveUrl","vQuality":"$q"}""")
            }
            if (response.status.isSuccess()) {
                val json = response.bodyAsText()
                val urlMatch =
                    Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(json)
                val dlUrl = urlMatch?.groupValues?.get(1)?.replace("""\/""", "/")
                if (!dlUrl.isNullOrBlank() && dlUrl.startsWith("http")) {
                    val label = when (q) {
                        "1080" -> "1080p (Full HD - MP4)"
                        "720" -> "720p (HD - MP4)"
                        "480" -> "480p (SD - MP4)"
                        else -> "360p (Low - MP4)"
                    }
                    if (list.none {
                            it.qualityLabel.contains(
                                q,
                                true
                            ) || it.directUrl == dlUrl
                        }) {
                        list.add(
                            MediaQualityEntry(
                                index = index++,
                                qualityLabel = label,
                                directUrl = dlUrl,
                                format = "mp4",
                                size = -1L,
                                isSelected = list.isEmpty()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // 3. Fallback to embed stream if still empty

    if (list.isEmpty() && !videoId.isNullOrBlank()) {

        val embedUrl = resolveYouTubeEmbedStream(videoId)

        if (!embedUrl.isNullOrBlank()) {

            list.add(

                MediaQualityEntry(

                    index = index++,

                    qualityLabel = "720p (HD Stream - MP4)",

                    directUrl = embedUrl,

                    format = "mp4",

                    size = -1L,

                    isSelected = true

                )

            )

        }

    }
    return index
}

internal suspend fun MediaStreamDownloader.inspectWebPage(
    effectiveUrl: String,
    resolvedTitle: String?,
    list: MutableList<MediaQualityEntry>,
    startIndex: Int
): Int {
    var index = startIndex
    val cleanUrl = effectiveUrl.substringBefore('?')
    val ext = cleanUrl.substringAfterLast('.', "").lowercase()
    val isDirectAudio = ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
    val isDirectVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v")

    if (isDirectAudio || isDirectVideo) {
        val label =
            if (isDirectAudio) "Audio Only (${ext.uppercase()})" else "Direct Media (${ext.uppercase()})"
        var headSize = -1L
        try {
            val headResp = client.head(effectiveUrl)
            headSize =
                headResp.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        } catch (_: Exception) {
        }

        list.add(
            MediaQualityEntry(
                index = index++,
                qualityLabel = label,
                directUrl = effectiveUrl,
                format = ext,
                size = headSize,
                isSelected = list.isEmpty(),
                originalUrl = effectiveUrl,
                videoTitle = resolvedTitle
            )
        )
    } else {
        val response = client.get(effectiveUrl) {
            header(HttpHeaders.UserAgent, desktopUserAgent)
        }
        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val webQualities =
                extractMultipleWebQualities(effectiveUrl, body, resolvedTitle)
            for (entry in webQualities) {
                if (list.none { it.directUrl == entry.directUrl }) {
                    list.add(
                        entry.copy(
                            index = index++,
                            isSelected = list.isEmpty()
                        )
                    )
                }
            }
        }
    }
    return index
}
