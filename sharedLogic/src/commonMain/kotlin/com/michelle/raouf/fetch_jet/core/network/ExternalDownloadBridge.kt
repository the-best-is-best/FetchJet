package com.michelle.raouf.fetch_jet.core.network

/** One link handed over by the browser extension (or the command line / URL scheme). */
data class ExternalDownloadRequest(
    val url: String,
    val cookie: String = "",
    val referer: String = "",
    val userAgent: String = "",
    val fileName: String = "",
    /**
     * The extension already let the user pick a quality (yt-dlp format) in the browser:
     * the matching entry is marked selected and the download starts without the Add dialog.
     */
    val mediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),
    /** Start right away with the default folder instead of opening the Add dialog. */
    val direct: Boolean = false
) {
    /** Headers worth replaying on the download requests. */
    fun toRequestHeaders(): Map<String, String> = buildMap {
        if (cookie.isNotBlank()) put("Cookie", cookie)
        if (referer.isNotBlank()) put("Referer", referer)
        if (userAgent.isNotBlank()) put("User-Agent", userAgent)
    }
}

object ExternalDownloadBridge {
    /** Single link: the app opens the Add dialog pre-filled so the user can inspect/choose. */
    var onExternalRequest: ((ExternalDownloadRequest) -> Unit)? = null

    /** Several links at once ("Download all links"): queued directly, no dialog per link. */
    var onExternalBatch: ((List<ExternalDownloadRequest>) -> Unit)? = null

    var openInBrowserFallback: ((String) -> Unit)? = null

    fun dispatch(url: String, cookie: String = "") = dispatch(ExternalDownloadRequest(url, cookie))

    fun dispatch(request: ExternalDownloadRequest) {
        val clean = request.copy(url = request.url.trim())
        // Do not log the URL: links from the browser often carry signed tokens or session ids.
        println("ExternalDownloadBridge: dispatched a URL (${clean.url.length} chars)")
        onExternalRequest?.invoke(clean)
    }

    fun dispatchBatch(requests: List<ExternalDownloadRequest>) {
        val clean = requests.map { it.copy(url = it.url.trim()) }.filter { it.url.isNotBlank() }
        if (clean.isEmpty()) return
        println("ExternalDownloadBridge: dispatched a batch of ${clean.size} URLs")
        val handler = onExternalBatch
        if (handler != null) handler(clean) else clean.forEach { onExternalRequest?.invoke(it) }
    }

    fun triggerFallback(url: String) {
        val cleanUrl = url.trim()
        val fallbackUrl =
            cleanUrl + (if (cleanUrl.contains("?")) "&" else "?") + "fetchjet_fallback=1"
        openInBrowserFallback?.invoke(fallbackUrl)
    }
}
