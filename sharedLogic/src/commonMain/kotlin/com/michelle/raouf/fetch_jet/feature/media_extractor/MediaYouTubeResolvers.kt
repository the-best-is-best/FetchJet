package com.michelle.raouf.fetch_jet.feature.media_extractor


import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

internal suspend fun MediaStreamDownloader.resolveYouTubeFromHtmlPage(url: String): String? {

    try {

        val response: HttpResponse = client.get(url) {

            header(HttpHeaders.UserAgent, desktopUserAgent)

            header(

                HttpHeaders.Accept,

                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

            )

        }

        if (response.status.isSuccess()) {

            val html = response.bodyAsText()

            val regex = Regex("""ytInitialPlayerResponse\s*=\s*(\{.+?\});</script>""")

            val match = regex.find(html)

            if (match != null) {

                val json = match.groupValues[1]

                val urlMatch =

                    Regex("""["']url["']\s*:\s*["'](https?:\\?/\\?/[^"']+)["']""").find(json)

                if (urlMatch != null) {

                    return cleanEscapedUrl(urlMatch.groupValues[1])

                }

            }

            val gvMatch =

                Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)""").find(

                    html

                )

            if (gvMatch != null) {

                return cleanEscapedUrl(gvMatch.groupValues[1])

            }

        }

    } catch (_: Exception) {

    }

    return null

}

internal suspend fun MediaStreamDownloader.resolveAnyValidYouTubeStream(url: String): String? {

    // 0. Try platform-specific resolver first (e.g. yt-dlp bundled binary)

    val platformStream = try {

        resolvePlatformStream(url)

    } catch (_: Exception) {

        null

    }

    if (!platformStream.isNullOrBlank() && platformStream.startsWith("http")) {

        return platformStream

    }


    val videoId = extractYouTubeVideoId(url)


    // 1. Try parsing YouTube HTML page directly

    val htmlStreamUrl = resolveYouTubeFromHtmlPage(url)

    if (!htmlStreamUrl.isNullOrBlank()) return htmlStreamUrl


    // 2. Fallback to embed stream

    if (!videoId.isNullOrBlank()) {

        val embedUrl = resolveYouTubeEmbedStream(videoId)

        if (!embedUrl.isNullOrBlank()) return embedUrl

    }



    return null

}

internal suspend fun MediaStreamDownloader.resolveYouTubeEmbedStream(videoId: String): String? {

    try {

        val embedUrl = "https://www.youtube.com/embed/$videoId"

        val response: HttpResponse = client.get(embedUrl) {

            header(HttpHeaders.UserAgent, desktopUserAgent)

        }

        if (response.status.isSuccess()) {

            val body = response.bodyAsText()

            val extracted = extractHighQualityVideoUrl(embedUrl, body)

            if (!extracted.isNullOrBlank()) {

                return extracted

            }

            val m =

                Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)""").find(

                    body

                )

            if (m != null) {

                return cleanEscapedUrl(m.groupValues[1])

            }

        }

    } catch (_: Exception) {

    }

    return null

}

internal fun MediaStreamDownloader.extractYouTubeVideoId(url: String): String? {

    try {

        val lower = url.lowercase()

        when {

            lower.contains("/shorts/") -> {

                val parts = url.split("/shorts/")

                if (parts.size > 1) {

                    return parts[1].substringBefore("?").substringBefore("/")

                }

            }


            lower.contains("youtu.be/") -> {

                val parts = url.split("youtu.be/")

                if (parts.size > 1) {

                    return parts[1].substringBefore("?").substringBefore("/")

                }

            }


            lower.contains("watch?v=") -> {

                val parts = url.split("watch?v=")

                if (parts.size > 1) {

                    return parts[1].substringBefore("&").substringBefore("/")

                }

            }

        }

    } catch (_: Exception) {

    }

    return null

}

internal fun MediaStreamDownloader.isYouTubeUrl(url: String): Boolean {

    val lower = url.lowercase()

    return lower.contains("youtube.com") || lower.contains("youtu.be")

}

/**
 * Every way of writing a YouTube video link (/shorts/, /live/, /embed/, youtu.be, watch
 * with a playlist) becomes "https://www.youtube.com/watch?v=ID": one page, one inspection,
 * one cache entry, instead of yt-dlp running once per spelling.
 */
internal fun MediaStreamDownloader.normalizeYouTubeUrl(url: String): String {
    val id = MediaUrls.youTubeVideoId(url)
        ?: return url
    return "https://www.youtube.com/watch?v=$id"
}
