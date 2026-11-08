package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength

internal suspend fun HttpChunkDownloader.resolveFinalUrlAndMetadata(
    initialUrl: String,
    extraHeaders: Map<String, String> = emptyMap()
): ResolvedHttpMetadata {
    var currentUrl = initialUrl
    var redirectCount = 0
    val maxRedirects = 10

    while (redirectCount < maxRedirects) {
        var redirectTarget: String? = null
        val metadata = client.prepareGet(currentUrl) {
            applyBrowserHeaders(currentUrl, headersFor(extraHeaders, initialUrl, currentUrl))
        }.execute { response ->
            val status = response.status.value
            if (status in 300..399) {
                val location = response.headers[HttpHeaders.Location]
                if (!location.isNullOrBlank()) {
                    redirectTarget = resolveUrl(currentUrl, location)
                    return@execute null
                }
            }

            val contentType = response.headers[HttpHeaders.ContentType]?.lowercase() ?: ""
            if (contentType.contains("text/html") && redirectCount < maxRedirects) {
                try {
                    val html = response.bodyAsText()
                    val redirectUrl = extractRedirectUrlFromHtml(html)
                    if (!redirectUrl.isNullOrBlank()) {
                        redirectTarget = resolveUrl(currentUrl, redirectUrl)
                        return@execute null
                    }
                } catch (_: Exception) {
                }
            }

            ResolvedHttpMetadata(
                finalUrl = currentUrl,
                statusCode = response.status.value,
                statusDescription = response.status.description,
                contentType = contentType,
                contentLength = response.contentLength() ?: -1L,
                acceptRanges = response.headers[HttpHeaders.AcceptRanges]?.lowercase() == "bytes",
                contentDisposition = response.headers[HttpHeaders.ContentDisposition]
            )
        }

        if (redirectTarget != null) {
            currentUrl = redirectTarget
            redirectCount++
            continue
        }

        if (metadata != null) {
            return metadata
        }
    }

    throw Exception("Too many redirects ($maxRedirects)")
}

internal fun extractRedirectUrlFromHtml(html: String): String? {
    val metaRegex = Regex(
        """(?i)<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]+content\s*=\s*["']?[^"'>]*url\s*=\s*["']?([^"'>\s]+)""",
        RegexOption.IGNORE_CASE
    )
    val match = metaRegex.find(html)
    if (match != null && match.groupValues.size > 1) {
        return match.groupValues[1].replace("\"", "").replace("'", "").trim()
    }
    val jsRegex = Regex("""(?i)window\.location\s*=\s*["']([^"']+)["']""")
    val jsMatch = jsRegex.find(html)
    if (jsMatch != null && jsMatch.groupValues.size > 1) {
        return jsMatch.groupValues[1].trim()
    }
    return null
}

internal fun resolveUrl(baseUrl: String, location: String): String {
    if (location.startsWith("http://") || location.startsWith("https://")) {
        return location
    }
    val scheme = baseUrl.substringBefore("://")
    val hostAndPath = baseUrl.substringAfter("://").substringBefore('?').substringBefore('#')
    val host = hostAndPath.substringBefore("/")
    return when {
        location.startsWith("//") -> "$scheme:$location"
        location.startsWith("/") -> "$scheme://$host$location"
        else -> "$scheme://${hostAndPath.substringBeforeLast("/", host)}/$location"
    }
}
