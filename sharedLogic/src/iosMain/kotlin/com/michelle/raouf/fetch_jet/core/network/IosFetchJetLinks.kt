@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getString
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.stringByRemovingPercentEncoding

/**
 * fetchjet:// links opened by the Safari extension (the same JavaScript as the desktop one):
 *  - fetchjet://download?url=<link>             one link, as on Android
 *  - fetchjet://add?json=<request or [requests]> link(s) with cookie / referer / userAgent /
 *    fileName, the JSON the desktop app's /add and /links endpoints accept
 *
 * Any web page can open a fetchjet:// link too, so "start without asking" (direct) and a
 * pre-chosen yt-dlp format are ignored: a single link always opens the Add dialog, several
 * open in its link list (nothing starts before the user taps Download).
 */
object IosFetchJetLinks {
    private const val MAX_LINKS = 500

    /** @return true when [link] was a fetchjet:// link (handled or rejected). */
    fun handle(link: String): Boolean {
        if (!link.startsWith("fetchjet://", ignoreCase = true)) return false
        val rest = link.substringAfter("://")
        val action = rest.substringBefore('?').trimEnd('/').lowercase()
        val params = rest.substringAfter('?', "").split('&').mapNotNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key.isBlank()) null else key to (decode(value) ?: return@mapNotNull null)
        }.toMap()

        when (action) {
            "download" -> params["url"]?.trim()?.takeIf(::isAllowedUrl)?.let {
                ExternalDownloadBridge.dispatch(ExternalDownloadRequest(url = it, cookie = params["cookie"].orEmpty()))
            }

            "add" -> {
                val parsed = runCatching { MiniJson.parse(params["json"].orEmpty()) }.getOrNull()
                @Suppress("UNCHECKED_CAST")
                when (parsed) {
                    is Map<*, *> -> (parsed as Map<String, Any?>).toRequest()?.let(ExternalDownloadBridge::dispatch)
                    is List<*> -> ExternalDownloadBridge.dispatchBatch(
                        parsed.take(MAX_LINKS)
                            .mapNotNull { (it as? Map<String, Any?>)?.toRequest() },
                        review = true
                    )
                }
            }
        }
        return true
    }

    private fun Map<String, Any?>.toRequest(): ExternalDownloadRequest? {
        val url = getString("url")?.trim().orEmpty()
        if (!isAllowedUrl(url)) return null
        fun header(key: String) = getString(key)?.trim().orEmpty().take(16 * 1024)
            .filter { it != '\r' && it != '\n' }
        return ExternalDownloadRequest(
            url = url,
            cookie = header("cookie"),
            referer = header("referer"),
            userAgent = header("userAgent"),
            fileName = getString("fileName")?.trim().orEmpty().take(255)
        )
    }

    private fun isAllowedUrl(url: String): Boolean =
        url.length <= 8192 && url.none { it < ' ' } &&
                (url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("magnet:", true))

    private fun decode(value: String): String? =
        NSString.create(string = value).stringByRemovingPercentEncoding
}
