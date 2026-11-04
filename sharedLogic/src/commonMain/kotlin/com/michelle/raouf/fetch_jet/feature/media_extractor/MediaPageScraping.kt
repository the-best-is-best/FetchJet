package com.michelle.raouf.fetch_jet.feature.media_extractor


import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal fun MediaStreamDownloader.extractHighQualityVideoUrl(
    pageUrl: String,
    body: String
): String? {

    val domain = pageUrl.lowercase()


    // 1. YouTube High Quality Extractor

    if (domain.contains("youtube.com") || domain.contains("youtu.be")) {

        val ytPatterns = listOf(

            Regex("""["']url["']\s*:\s*["'](https?:\\?/\\?/[^"']*googlevideo\.com\\?/videoplayback[^"']*)["']"""),

            Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)"""),

            Regex("""["']streamingData["']\s*:\s*\{.*?"url"\s*:\s*["']([^"']+)["']""")

        )

        for (p in ytPatterns) {

            val m = p.find(body)

            if (m != null) {

                val raw = m.groupValues[1]

                val cleaned = cleanEscapedUrl(raw)

                if (cleaned.isNotBlank()) return cleaned

            }

        }

    }


    // 2. Facebook High Quality (HD) Extractor

    if (domain.contains("facebook.com") || domain.contains("fb.watch")) {

        val fbPatterns = listOf(

            Regex("""["']playable_url_quality_hd["']\s*:\s*["']([^"']+)["']"""),

            Regex("""["']browser_native_hd_url["']\s*:\s*["']([^"']+)["']"""),

            Regex("""["']playable_url["']\s*:\s*["']([^"']+)["']"""),

            Regex("""["']browser_native_sd_url["']\s*:\s*["']([^"']+)["']"""),

            Regex("""hd_src\s*:\s*["']([^"']+)["']"""),

            Regex("""sd_src\s*:\s*["']([^"']+)["']"""),

            Regex("""sd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),

            Regex("""hd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),

            Regex("""video_src\s*:\s*["']([^"']+)["']"""),

            Regex(

                """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

                RegexOption.IGNORE_CASE

            ),

            Regex("""(https?:\\?/\\?/[^"'\s]*fbcdn\.net[^"'\s]*\.mp4[^"'\s]*)"""),

            Regex("""(https?:\\?/\\?/[^"'\s]*video[^"'\s]*\.fbcdn\.net[^"'\s]*)""")

        )

        for (p in fbPatterns) {

            val m = p.find(body)

            if (m != null) {

                val raw = m.groupValues[1]

                val cleaned = cleanEscapedUrl(raw)

                if (cleaned.isNotBlank()) return cleaned

            }

        }

    }


    // 3. Instagram & TikTok High Quality Video Extractor

    if (domain.contains("instagram.com") || domain.contains("tiktok.com")) {

        val socialPatterns = listOf(

            Regex(

                """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

                RegexOption.IGNORE_CASE

            ),

            Regex("""["']video_url["']\s*:\s*["']([^"']+)["']"""),

            Regex("""["']playAddr["']\s*:\s*["']([^"']+)["']"""),

            Regex("""(https?:\\?/\\?/[^"'\s]*instagram\.com[^"'\s]*\.mp4[^"'\s]*)"""),

            Regex("""(https?:\\?/\\?/[^"'\s]*tiktokcdn\.com[^"'\s]*)""")

        )

        for (p in socialPatterns) {

            val m = p.find(body)

            if (m != null) {

                val raw = m.groupValues[1]

                val cleaned = cleanEscapedUrl(raw)

                if (cleaned.isNotBlank()) return cleaned

            }

        }

    }


    // 4. Generic High Quality OpenGraph or HTML5 Video Tag Extractor

    val genericPatterns = listOf(

        Regex(

            """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

            RegexOption.IGNORE_CASE

        ),

        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),

        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),

        Regex(

            """(https?://[^"'\s]+\.(?:mp4|m4v|webm|mkv|ts)(?:\?[^"'\s]*)?)""",

            RegexOption.IGNORE_CASE

        )

    )

    for (p in genericPatterns) {

        val m = p.find(body)

        if (m != null) {

            val raw = m.groupValues[1]

            val cleaned = cleanEscapedUrl(raw)

            if (cleaned.isNotBlank()) return cleaned

        }

    }



    return null

}

internal fun MediaStreamDownloader.extractMultipleWebQualities(
    pageUrl: String,
    body: String,
    videoTitle: String?
): List<MediaQualityEntry> {
    val domain = pageUrl.lowercase()
    val list = mutableListOf<MediaQualityEntry>()
    val seenUrls = mutableSetOf<String>()

    if (domain.contains("facebook.com") || domain.contains("fb.watch")) {
        val extractedDuration =
            Regex(""""playable_duration_in_ms"\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(1)
                ?.toLongOrNull()?.let { it / 1000L }
                ?: Regex("""playable_duration_in_ms\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(
                    1
                )?.toLongOrNull()?.let { it / 1000L }
                ?: Regex(""""duration"\s*:\s*"?([0-9.]+)"?""").find(body)?.groupValues?.get(1)
                    ?.toDoubleOrNull()?.toLong()
                ?: Regex("""video_duration\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(1)
                    ?.toLongOrNull()
                ?: 0L

        val hdPatterns = listOf(
            Regex("""["']playable_url_quality_hd["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']browser_native_hd_url["']\s*:\s*["']([^"']+)["']"""),
            Regex("""hd_src\s*:\s*["']([^"']+)["']"""),
            Regex("""hd_src_no_ratelimit\s*:\s*["']([^"']+)["']""")
        )
        for (p in hdPatterns) {
            val m = p.find(body)
            if (m != null) {
                val url = cleanEscapedUrl(m.groupValues[1])
                if (url.startsWith("http") && !seenUrls.contains(url)) {
                    seenUrls.add(url)
                    list.add(
                        MediaQualityEntry(
                            index = list.size,
                            qualityLabel = "HD Quality (Direct MP4)",
                            directUrl = url,
                            format = "mp4",
                            size = -1L,
                            isSelected = list.isEmpty(),
                            formatId = "hd",
                            originalUrl = pageUrl,
                            videoTitle = videoTitle,
                            durationSeconds = extractedDuration
                        )
                    )
                    break
                }
            }
        }

        val sdPatterns = listOf(
            Regex("""["']playable_url["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']browser_native_sd_url["']\s*:\s*["']([^"']+)["']"""),
            Regex("""sd_src\s*:\s*["']([^"']+)["']"""),
            Regex("""sd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),
            Regex("""video_src\s*:\s*["']([^"']+)["']""")
        )
        for (p in sdPatterns) {
            val m = p.find(body)
            if (m != null) {
                val url = cleanEscapedUrl(m.groupValues[1])
                if (url.startsWith("http") && !seenUrls.contains(url)) {
                    seenUrls.add(url)
                    list.add(
                        MediaQualityEntry(
                            index = list.size,
                            qualityLabel = "SD Quality (Direct MP4)",
                            directUrl = url,
                            format = "mp4",
                            size = -1L,
                            isSelected = list.isEmpty(),
                            formatId = "sd",
                            originalUrl = pageUrl,
                            videoTitle = videoTitle,
                            durationSeconds = extractedDuration
                        )
                    )
                    break
                }
            }
        }
    }

    if (list.isEmpty()) {
        val genericUrl = extractHighQualityVideoUrl(pageUrl, body)
        if (!genericUrl.isNullOrBlank() && !seenUrls.contains(genericUrl)) {
            list.add(
                MediaQualityEntry(
                    index = 0,
                    qualityLabel = "High Quality HD Stream",
                    directUrl = genericUrl,
                    format = "mp4",
                    size = -1L,
                    isSelected = true,
                    originalUrl = pageUrl,
                    videoTitle = videoTitle
                )
            )
        }
    }

    return list
}

internal fun MediaStreamDownloader.cleanEscapedUrl(rawUrl: String): String {

    return rawUrl

        .replace("""\/""", "/")

        .replace("""\u0026""", "&")

        .replace("""&amp;""", "&")

        .replace("""\u00253D""", "=")

        .replace("""\u002526""", "&")

        .replace("""\u0025""", "%")

        .trim()

}

internal fun MediaStreamDownloader.extractMediaTitle(url: String): String {

    val domain = url.lowercase()

    val timestamp = timeSourceMs()

    return when {

        domain.contains("youtube.com") || domain.contains("youtu.be") -> "YouTube_Video_$timestamp"

        domain.contains("vimeo.com") -> "Vimeo_HD_Video_$timestamp"

        domain.contains("tiktok.com") -> "TikTok_Video_$timestamp"

        domain.contains("instagram.com") -> "Instagram_Reel_$timestamp"

        domain.contains("twitter.com") || domain.contains("x.com") -> "Twitter_Video_$timestamp"

        domain.contains("facebook.com") || domain.contains("fb.watch") -> "Facebook_Video_$timestamp"

        domain.contains("twitch.tv") -> "Twitch_Stream_$timestamp"

        else -> {

            val hostName = url.substringBefore("?").substringAfter("://").substringBefore("/")

                .replace(".", "_")

            if (hostName.isNotBlank()) "Media_Stream_${hostName}_$timestamp" else "Media_Stream_$timestamp"

        }

    }

}

internal suspend fun MediaStreamDownloader.fetchYouTubeTitle(url: String): String? {

    return try {

        val oembedUrl = "https://www.youtube.com/oembed?url=${url}&format=json"

        val response: HttpResponse = client.get(oembedUrl)

        if (response.status.isSuccess()) {

            val json = response.bodyAsText()

            val match = Regex("""["']title["']\s*:\s*["']([^"']+)["']""").find(json)

            if (match != null) {

                val rawTitle = match.groupValues[1]

                decodeUnicodeEscapes(rawTitle)

            } else null

        } else null

    } catch (_: Exception) {

        null

    }

}

internal suspend fun MediaStreamDownloader.fetchGenericMediaTitle(url: String): String? {

    return try {

        val domain = url.lowercase()

        if (domain.contains("vimeo.com")) {

            val oembedUrl = "https://vimeo.com/api/oembed.json?url=$url"

            val response: HttpResponse = client.get(oembedUrl)

            if (response.status.isSuccess()) {

                val json = response.bodyAsText()

                val match = Regex("""["']title["']\s*:\s*["']([^"']+)["']""").find(json)

                if (match != null) {

                    return decodeUnicodeEscapes(match.groupValues[1])

                }

            }

        }

        null

    } catch (_: Exception) {

        null

    }

}

internal fun MediaStreamDownloader.decodeUnicodeEscapes(input: String): String {

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

