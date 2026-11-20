package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.core.network.NetworkClient
import com.michelle.raouf.fetch_jet.core.network.SiteCookieJar
import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.feature.http_downloader.applyBrowserHeaders
import com.michelle.raouf.fetch_jet.feature.http_downloader.contentDispositionFileName
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength

class HttpPageFetcher(private val extraHeaders: Map<String, String> = emptyMap()) : PageFetcher {
    private val clients = NetworkClient {
        HttpClient {
            install(HttpRedirect) { checkHttpMethod = false }
            install(HttpTimeout) {
                requestTimeoutMillis = null
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis = 20_000L
            }
            useNetworkSettings()
        }
    }

    private fun headersFor(url: String): Map<String, String> = SiteCookieJar.headersFor(url, extraHeaders)

    override suspend fun page(url: String): FetchedPage {
        val headers = headersFor(url)
        return clients.current.prepareGet(url) {
            applyBrowserHeaders(url, headers)
        }.execute { response ->
            val type = response.headers[HttpHeaders.ContentType].orEmpty()
            val length = response.contentLength() ?: -1L
            val html = if (looksLikeText(type) && length < MAX_PAGE_BYTES) response.bodyAsText().take(MAX_PAGE_CHARS) else ""
            FetchedPage(response.status.value, response.request.url.toString(), type, html)
        }
    }

    override suspend fun probe(url: String): FileProbe? {
        val headers = headersFor(url)
        val head = runCatching {
            clients.current.prepareRequest(url) {
                method = HttpMethod.Head
                applyBrowserHeaders(url, headers)
            }.execute { response -> describe(response) }
        }.getOrNull()
        if (head != null && head.second) return head.first
        return runCatching {
            clients.current.prepareGet(url) {
                applyBrowserHeaders(url, headers)
                header(HttpHeaders.Range, "bytes=0-0")
            }.execute { response -> describe(response).takeIf { it.second }?.first }
        }.getOrNull()
    }

    private fun describe(response: HttpResponse): Pair<FileProbe, Boolean> {
        val status = response.status.value
        val type = response.headers[HttpHeaders.ContentType].orEmpty()
        val range = response.headers[HttpHeaders.ContentRange]?.substringAfter('/')?.toLongOrNull()
        val size = range ?: response.contentLength() ?: -1L
        val name = contentDispositionFileName(response.headers[HttpHeaders.ContentDisposition])
            ?: PageLinks.fileNameOf(response.request.url.toString()).takeIf { it.isNotBlank() }
        return FileProbe(name, size, type) to (status in 200..299)
    }

    private fun looksLikeText(type: String): Boolean {
        val lower = type.lowercase()
        return lower.isBlank() || lower.contains("html") || lower.contains("xml") || lower.startsWith("text/")
    }

    private companion object {
        const val MAX_PAGE_BYTES = 4_000_000L
        const val MAX_PAGE_CHARS = 2_000_000
    }
}
