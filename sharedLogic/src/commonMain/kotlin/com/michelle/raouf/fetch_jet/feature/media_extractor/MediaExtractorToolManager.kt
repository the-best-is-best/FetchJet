package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.network.createStreamHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

expect fun getMediaExtractorToolStatus(): String
expect suspend fun downloadMediaExtractorTool(onProgress: (ToolDownloadProgress) -> Unit = {}): Boolean
expect suspend fun autoUpdateMediaTools(onProgress: (ToolDownloadProgress) -> Unit = {}): Boolean
expect fun isMediaExtractorToolInstalled(): Boolean
expect fun isFfmpegToolInstalled(): Boolean
expect fun getMediaToolsDirectory(): String

data class JsRuntimeStatus(val name: String, val isReady: Boolean)

expect fun getJsRuntimeStatus(): JsRuntimeStatus

data class ToolDownloadProgress(val tool: String, val fraction: Float? = null)

suspend fun latestGitHubReleaseTag(repo: String): String? = try {
    val client = createStreamHttpClient()
    try {
        val body = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "FetchJet")
        }.bodyAsText()
        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    } finally {
        client.close()
    }
} catch (_: Exception) {
    null
}
