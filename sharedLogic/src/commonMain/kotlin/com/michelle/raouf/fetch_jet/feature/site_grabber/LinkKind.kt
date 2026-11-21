package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeCode
import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeResult

enum class LinkKind { UNKNOWN, FILE, PAGE, VIDEO_PAGE, UNSURE }

object LinkKinds {
    private val videoSignals = listOf(
        Regex("""<meta[^>]+(?:property|name)\s*=\s*["'](?:og:video[^"']*|twitter:player[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""<video\b""", RegexOption.IGNORE_CASE),
        Regex("""<iframe[^>]+src\s*=\s*["'][^"']*(?:youtube|youtu\.be|vimeo|dailymotion|player)""", RegexOption.IGNORE_CASE)
    )

    fun hasVideoSignals(html: String): Boolean = videoSignals.any { it.containsMatchIn(html) }

    fun of(result: LinkProbeResult?): LinkKind = when (result) {
        null -> LinkKind.UNKNOWN
        is LinkProbeResult.Ok -> LinkKind.FILE
        is LinkProbeResult.Failed -> when {
            result.code != LinkProbeCode.WEB_PAGE -> LinkKind.UNSURE
            hasVideoSignals(result.pageHead) -> LinkKind.VIDEO_PAGE
            else -> LinkKind.PAGE
        }
    }
}
