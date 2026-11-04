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
    val direct: Boolean = false,
    /** The extension's scissors button: open the Add dialog with this quality and the clip range on. */
    val openClip: Boolean = false,
    /** [url] is a captured video-only stream and this its audio: download both, merge them. */
    val audioUrl: String = ""
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
        set(value) {
            field = value
            // A file / link opened at launch arrives before the UI exists: replay it now.
            if (value != null) {
                val waiting = pending.toList()
                pending.clear()
                waiting.forEach { value(it) }
            }
        }
    private val pending = mutableListOf<ExternalDownloadRequest>()

    /**
     * Several links at once ("Download all links"): queued directly, no dialog per link. With
     * `review` (links any web page can send, iOS fetchjet://) they open in the Add dialog's list.
     */
    var onExternalBatch: ((List<ExternalDownloadRequest>, Boolean) -> Unit)? = null

    var openInBrowserFallback: ((String) -> Unit)? = null

    /**
     * Brings the app in front of whatever the user is looking at (the browser, usually).
     * Set by each platform's entry point; called when a link arrives from the extension, the
     * clipboard or a fetchjet:// URL.
     */
    var onShowAndFocus: (() -> Unit)? = null
    fun showAndFocus() {
        onShowAndFocus?.invoke()
    }

    fun dispatch(url: String, cookie: String = "") = dispatch(ExternalDownloadRequest(url, cookie))

    fun dispatch(request: ExternalDownloadRequest) {
        val clean = request.copy(url = request.url.trim())
        // Do not log the URL: links from the browser often carry signed tokens or session ids.
        println("ExternalDownloadBridge: dispatched a URL (${clean.url.length} chars)")
        val handler = onExternalRequest
        if (handler != null) handler(clean) else pending.add(clean)
    }

    fun dispatchBatch(requests: List<ExternalDownloadRequest>, review: Boolean = false) {
        val clean = requests.map { it.copy(url = it.url.trim()) }.filter { it.url.isNotBlank() }
        if (clean.isEmpty()) return
        println("ExternalDownloadBridge: dispatched a batch of ${clean.size} URLs")
        val handler = onExternalBatch
        if (handler != null) handler(clean, review) else clean.forEach {
            onExternalRequest?.invoke(
                it
            )
        }
    }

    fun triggerFallback(url: String) {
        val cleanUrl = url.trim()
        val fallbackUrl =
            cleanUrl + (if (cleanUrl.contains("?")) "&" else "?") + "fetchjet_fallback=1"
        openInBrowserFallback?.invoke(fallbackUrl)
    }
}
