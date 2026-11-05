package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private val desktopUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

internal fun hostOf(url: String): String =
    url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()

/**
 * The browser's Cookie/Referer belong to the site the link came from. A redirect or chunk
 * request to another host (CDN) gets only the User-Agent, never the session cookie.
 */
internal fun headersFor(
    extra: Map<String, String>,
    originalUrl: String,
    requestUrl: String
): Map<String, String> {
    if (extra.isEmpty() || requestUrl.isBlank()) return extra
    val sameHost = hostOf(originalUrl) == hostOf(requestUrl)
    return if (sameHost) extra else extra.filterKeys {
        it.equals(
            "User-Agent",
            ignoreCase = true
        )
    }
}

/**
 * Headers captured by the browser extension win over our synthetic browser headers:
 * the site already accepted exactly these (Cookie, Referer, User-Agent) for this link.
 */
internal fun HttpRequestBuilder.applyExtraHeaders(extra: Map<String, String>) {
    for ((name, value) in extra) {
        if (name.isBlank() || value.isBlank()) continue
        if (name.any { it < ' ' } || value.any { it == '\r' || it == '\n' }) continue
        headers.remove(name)
        header(name, value)
    }
    if (extra.keys.any { it.equals("Referer", ignoreCase = true) }) {
        headers.remove("Sec-Fetch-Site")
        header("Sec-Fetch-Site", "same-origin")
    }
}

internal fun HttpRequestBuilder.applyBrowserHeaders(
    url: String = "",
    extra: Map<String, String> = emptyMap()
) {
    header(HttpHeaders.UserAgent, desktopUserAgent)
    header(
        "Sec-Ch-Ua",
        "\"Google Chrome\";v=\"124\", \"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\""
    )
    header("Sec-Ch-Ua-Mobile", "?0")
    header("Sec-Ch-Ua-Platform", "\"Windows\"")
    header("Sec-Fetch-Dest", "document")
    header("Sec-Fetch-Mode", "navigate")
    header("Sec-Fetch-Site", "none")
    header("Sec-Fetch-User", "?1")
    header(
        HttpHeaders.Accept,
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    )
    header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
    if (url.isNotBlank()) {
        val scheme = url.substringBefore("://")
        val hostAndPath = url.substringAfter("://")
        val host = hostAndPath.substringBefore("/")
        if (scheme.isNotBlank() && host.isNotBlank()) {
            header("Referer", "$scheme://$host/")
        }
    }
    applyExtraHeaders(extra)
}

internal fun HttpRequestBuilder.applyDownloadHeaders(
    url: String = "",
    extra: Map<String, String> = emptyMap()
) {
    header(HttpHeaders.UserAgent, desktopUserAgent)
    header(
        "Sec-Ch-Ua",
        "\"Google Chrome\";v=\"124\", \"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\""
    )
    header("Sec-Ch-Ua-Mobile", "?0")
    header("Sec-Ch-Ua-Platform", "\"Windows\"")
    header("Sec-Fetch-Dest", "document")
    header("Sec-Fetch-Mode", "navigate")
    header("Sec-Fetch-Site", "none")
    header("Sec-Fetch-User", "?1")
    header(HttpHeaders.Accept, "*/*")
    header(HttpHeaders.AcceptEncoding, "identity")
    if (url.isNotBlank()) {
        val scheme = url.substringBefore("://")
        val hostAndPath = url.substringAfter("://")
        val host = hostAndPath.substringBefore("/")
        if (scheme.isNotBlank() && host.isNotBlank()) {
            header("Referer", "$scheme://$host/")
        }
    }
    applyExtraHeaders(extra)
}
