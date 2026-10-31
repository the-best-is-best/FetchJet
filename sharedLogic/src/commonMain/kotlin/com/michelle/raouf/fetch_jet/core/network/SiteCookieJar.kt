package com.michelle.raouf.fetch_jet.core.network

/**
 * The browser's Cookie / Referer / User-Agent for each site, as the extension sent them with
 * a link, kept in memory only while the app runs (never written to disk). A link of the same
 * site typed or pasted later - the next lesson of a course, a quality picked again, a playlist
 * entry - then still opens pages that need the login.
 */
object SiteCookieJar {
    // Copy-on-write map: reads need no lock, a lost write only re-saves the same site.
    @kotlin.concurrent.Volatile
    private var bySite: Map<String, Map<String, String>> = emptyMap()

    /** "www.udemy.com" and "udemy.com" are one site; "m.youtube.com" / "youtu.be" -> "youtube.com". */
    fun siteOf(url: String): String {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore(':').lowercase().removePrefix("www.").removePrefix("m.")
        if (host == "youtu.be") return "youtube.com"
        val parts = host.split('.')
        // keep the last two labels (three for co.uk-style suffixes)
        return when {
            parts.size <= 2 -> host
            parts[parts.size - 2].length <= 3 && parts.size >= 3 -> parts.takeLast(3)
                .joinToString(".")

            else -> parts.takeLast(2).joinToString(".")
        }
    }

    /** Remembers the headers that came with [url] (only those worth keeping). */
    fun remember(url: String, headers: Map<String, String>) {
        val keep = headers.filterKeys {
            it.equals("Cookie", true) || it.equals(
                "Referer",
                true
            ) || it.equals("User-Agent", true)
        }
            .filterValues { it.isNotBlank() }
        if (keep.none { it.key.equals("Cookie", true) }) return
        val site = siteOf(url).ifBlank { return }
        bySite = bySite + (site to keep)
    }

    /** Headers remembered for [url]'s site, or [fallback] when none. */
    fun headersFor(url: String, fallback: Map<String, String> = emptyMap()): Map<String, String> {
        if (fallback.any { it.key.equals("Cookie", true) }) return fallback
        val site = siteOf(url)
        val saved = bySite[site] ?: return fallback
        // Referer of the saved page may not fit this link: keep the site's root instead.
        val root = saved["Referer"]?.let { r ->
            r.substringBefore("://") + "://" + r.substringAfter("://").substringBefore('/') + "/"
        }
        return buildMap {
            putAll(fallback)
            saved["Cookie"]?.let { put("Cookie", it) }
            if (!containsKey("Referer")) root?.let { put("Referer", it) }
            saved["User-Agent"]?.let { if (!containsKey("User-Agent")) put("User-Agent", it) }
        }
    }

    fun clear() {
        bySite = emptyMap()
    }
}
