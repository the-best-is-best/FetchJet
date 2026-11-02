package com.michelle.raouf.fetch_jet.feature.media_extractor

/** Link spellings of the video sites, reduced to one canonical form. */
object MediaUrls {
    private val ytId = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** The 11-character video id of any YouTube link, or null when it is not a single video. */
    fun youTubeVideoId(url: String): String? {
        val u = url.trim()
        val host = u.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()
        if (!host.endsWith("youtube.com") && host != "youtu.be" && !host.endsWith("youtube-nocookie.com")) return null
        val pathAndQuery = u.substringAfter("://", "").substringAfter('/', "")
        val path = pathAndQuery.substringBefore('?').substringBefore('#')
        val query = pathAndQuery.substringAfter('?', "").substringBefore('#')
        fun check(s: String?) = s?.takeIf { ytId.matches(it) }
        if (host == "youtu.be") return check(path.substringBefore('/'))
        val segs = path.split('/').filter { it.isNotBlank() }
        if (segs.size >= 2 && segs[0] in setOf(
                "shorts",
                "live",
                "embed",
                "v"
            )
        ) return check(segs[1])
        if (segs.firstOrNull() == "watch" || segs.isEmpty()) {
            return query.split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                ?.let { check(it) }
        }
        return null
    }

    /** One spelling per video for caches: YouTube → watch?v=ID, everything else trimmed as is. */
    fun canonical(url: String): String =
        youTubeVideoId(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url.trim()
}
