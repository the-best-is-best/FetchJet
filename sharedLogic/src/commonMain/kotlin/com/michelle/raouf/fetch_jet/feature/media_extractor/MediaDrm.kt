package com.michelle.raouf.fetch_jet.feature.media_extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

object MediaDrm {
    private val drmKeyFormats = listOf("widevine", "playready", "fairplay", "streamingkeydelivery", "edef8ba9", "9a04f079")

    fun hlsIsProtected(playlist: String): Boolean = playlist.lineSequence().any { raw ->
        val line = raw.trim()
        if (!line.startsWith("#EXT-X-KEY") && !line.startsWith("#EXT-X-SESSION-KEY")) return@any false
        val upper = line.uppercase()
        val method = Regex("METHOD=([A-Z0-9-]+)").find(upper)?.groupValues?.get(1) ?: ""
        val keyFormat = line.lowercase()
        (method.isNotEmpty() && method != "NONE" && method != "AES-128") || drmKeyFormats.any { it in keyFormat }
    }

    fun dashIsProtected(manifest: String): Boolean = manifest.contains("<ContentProtection", ignoreCase = true) ||
            manifest.contains(":ContentProtection", ignoreCase = true)

    fun errorSaysDrm(text: String?): Boolean = text != null && Regex("\\bDRM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

    fun looksLikeHls(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)

    fun looksLikeDash(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".mpd", ignoreCase = true)

    suspend fun isProtected(client: HttpClient, url: String, headers: Map<String, String>): Boolean {
        val hls = looksLikeHls(url)
        val dash = looksLikeDash(url)
        if (!hls && !dash) return false
        val text = fetch(client, url, headers) ?: return false
        if (dash || dashIsProtected(text)) return dashIsProtected(text)
        if (hlsIsProtected(text)) return true
        val firstVariant = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return false
        if (!text.contains("#EXT-X-STREAM-INF")) return false
        val variantText = fetch(client, resolveAgainst(url, firstVariant), headers) ?: return false
        return hlsIsProtected(variantText)
    }

    private suspend fun fetch(client: HttpClient, url: String, headers: Map<String, String>): String? = try {
        val response = client.get(url) {
            headers.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) header(k, v) }
        }
        if (response.status.isSuccess()) response.bodyAsText().take(2_000_000) else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun resolveAgainst(base: String, ref: String): String = when {
        ref.startsWith("http://") || ref.startsWith("https://") -> ref
        ref.startsWith("//") -> base.substringBefore("//") + ref
        ref.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + ref
        else -> base.substringBefore('?').substringBeforeLast('/') + "/" + ref
    }
}
