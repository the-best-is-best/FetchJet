package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.coroutines.flow.update
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

internal suspend fun MediaStreamDownloader.inspectHlsVariants(
    url: String,
    requestHeaders: Map<String, String>
): List<MediaQualityEntry>? {
    try {
        if (MediaDrm.looksLikeHls(url) && MediaDrm.isProtected(client, url.trim(), requestHeaders)) {
            lastInspectDrm = true
            return emptyList()
        }
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

class MediaToolMissingException(message: String) : Exception(message)

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
        } catch (e: MediaToolMissingException) {
            throw e
        } catch (e: SiteBusyException) {
            SiteBusy.mark(effectiveUrl)
            throw e
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

    // Fallback to embed stream

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

/** The site answered "too many requests" (HTTP 429): asking again now only keeps the limit on. */
class SiteBusyException(val host: String) : Exception() {
    override val message: String
        get() = runCatching { com.tbib.kmp_localization.LocalizationManager.Strings.siteBusyRetryLater }
            .getOrDefault("%1 is limiting requests right now (too many in a row). Try again in a few minutes.")
            .replace("%1", host)
}

fun ytDlpSaysTooManyRequests(text: String): Boolean = text.contains("HTTP Error 429")

internal fun linkHost(url: String): String =
    url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringAfter('@').lowercase().removePrefix("www.")

/** Sites that just answered 429: no new inspect of them for a few minutes. */
object SiteBusy {
    private const val WAIT_MS = 3 * 60 * 1000L
    private val until = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Long>>(emptyMap())

    fun mark(url: String) {
        val now = com.michelle.raouf.fetch_jet.core.util.currentEpochMs()
        until.update { it.filterValues { end -> end > now } + (linkHost(url) to now + WAIT_MS) }
    }

    fun check(url: String) {
        val host = linkHost(url)
        if ((until.value[host] ?: 0L) > com.michelle.raouf.fetch_jet.core.util.currentEpochMs()) throw SiteBusyException(host)
    }
}

/**
 * Two formats of one quality and container: which one the list keeps. H.264 plays everywhere
 * (old TVs, phones, Windows' own player); AV1 / VP9 are smaller. The setting picks
 * ([com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.videoPlaysEverywhere]).
 */
fun playsWiderThan(newCodec: String, keptCodec: String?): Boolean {
    val newIsH264 = newCodec.startsWith("avc1", ignoreCase = true)
    val keptIsH264 = (keptCodec ?: "").startsWith("avc1", ignoreCase = true)
    return if (com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.videoPlaysEverywhere.value) newIsH264 && !keptIsH264
    else !newIsH264 && keptIsH264 && !newCodec.equals("none", ignoreCase = true)
}

/**
 * yt-dlp's -f for a chosen video format: its sound in the preferred kind first (AAC for
 * "plays everywhere", else the best, usually Opus), then the other kind, then the video's own
 * sound, then the best there is: a missing kind falls back to the next.
 */
fun videoWithAudioSelector(formatId: String): String =
    if (com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.videoPlaysEverywhere.value)
        "$formatId+bestaudio[ext=m4a]/$formatId+bestaudio/$formatId/best"
    else "$formatId+bestaudio/$formatId+bestaudio[ext=m4a]/$formatId/best"
