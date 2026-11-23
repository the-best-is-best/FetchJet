package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.core.settings.FileCategory

interface SiteAdapter {
    fun matches(url: String): Boolean

    suspend fun grab(
        url: String,
        fetcher: PageFetcher,
        onProgress: (GrabProgress) -> Unit,
        onFiles: (List<GrabbedFile>) -> Unit
    ): GrabResult
}

object SiteAdapters {
    val all: List<SiteAdapter> = listOf(GitHubReleasesAdapter)

    fun forUrl(url: String): SiteAdapter? = all.firstOrNull { it.matches(url.trim()) }
}

object GitHubReleasesAdapter : SiteAdapter {
    private const val MAX_RELEASES = 10

    private val releaseUrl = Regex(
        """^https?://(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/releases(?:/(latest|tag/[^/?#]+))?/?(?:[?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private class Target(val owner: String, val repo: String, val kind: String)

    private fun parse(url: String): Target? {
        val m = releaseUrl.find(url.trim()) ?: return null
        return Target(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    override fun matches(url: String): Boolean = parse(url) != null

    override suspend fun grab(
        url: String,
        fetcher: PageFetcher,
        onProgress: (GrabProgress) -> Unit,
        onFiles: (List<GrabbedFile>) -> Unit
    ): GrabResult {
        val target = parse(url) ?: return GrabResult(emptyList(), 0, 0, 0, false)
        val base = "https://github.com/${target.owner}/${target.repo}"
        val found = LinkedHashMap<String, GrabbedFile>()
        var pagesRead = 0
        var refused = 0
        var reachedLimit = false
        var title = target.repo

        suspend fun readRelease(tag: String) {
            val pageUrl = "$base/releases/tag/$tag"
            val fragment = runCatching { fetcher.page("$base/releases/expanded_assets/$tag") }.getOrNull()
            if (fragment == null || fragment.status !in 200..299) {
                refused++
                return
            }
            pagesRead++
            val fresh = ArrayList<GrabbedFile>()
            for (file in assetsOf(fragment.html, target.owner, target.repo, tag, pageUrl)) {
                if (file.url !in found) {
                    found[file.url] = file
                    fresh.add(file)
                }
            }
            if (fresh.isNotEmpty()) onFiles(fresh)
            onProgress(GrabProgress(pagesRead, 0, found.size, title))
        }

        when {
            target.kind.startsWith("tag/", true) -> {
                val tag = target.kind.substringAfter('/')
                title = "${target.repo} ${percentDecodeOr(tag)}"
                onProgress(GrabProgress(0, 1, 0, title))
                readRelease(tag)
            }

            target.kind.equals("latest", true) -> {
                onProgress(GrabProgress(0, 2, 0, title))
                val page = runCatching { fetcher.page("$base/releases/latest") }.getOrNull()
                val tag = page?.takeIf { it.status in 200..299 }?.let { tagRegex(target).find(it.finalUrl)?.groupValues?.get(1) }
                if (tag == null) refused++ else {
                    pagesRead++
                    title = "${target.repo} ${percentDecodeOr(tag)}"
                    readRelease(tag)
                }
            }

            else -> {
                onProgress(GrabProgress(0, 1, 0, title))
                val page = runCatching { fetcher.page("$base/releases") }.getOrNull()
                if (page == null || page.status !in 200..299) refused++ else {
                    pagesRead++
                    title = "${target.repo} releases"
                    val tags = LinkedHashSet<String>()
                    val links = Regex("""href="/${Regex.escape(target.owner)}/${Regex.escape(target.repo)}/releases/tag/([^"?#/]+)"""", RegexOption.IGNORE_CASE)
                    for (m in links.findAll(page.html)) tags.add(m.groupValues[1])
                    if (tags.size > MAX_RELEASES) reachedLimit = true
                    for (tag in tags.take(MAX_RELEASES)) readRelease(tag)
                }
            }
        }
        onProgress(GrabProgress(pagesRead, 0, found.size, title))
        return GrabResult(found.values.toList(), pagesRead, refused, 0, reachedLimit, title)
    }

    private fun tagRegex(target: Target) =
        Regex("""/${Regex.escape(target.owner)}/${Regex.escape(target.repo)}/releases/tag/([^/?#]+)""", RegexOption.IGNORE_CASE)

    private fun percentDecodeOr(text: String): String =
        runCatching { com.michelle.raouf.fetch_jet.feature.http_downloader.percentDecode(text) }.getOrDefault(text)

    internal fun assetsOf(html: String, owner: String, repo: String, tag: String, pageUrl: String): List<GrabbedFile> {
        val o = Regex.escape(owner)
        val r = Regex.escape(repo)
        val anchor = Regex(
            """<a\s[^>]*href="(/$o/$r/(?:releases/download/[^"]+|archive/refs/tags/[^"]+\.(?:zip|tar\.gz)))"""",
            RegexOption.IGNORE_CASE
        )
        val matches = anchor.findAll(html).toList()
        val sizeText = Regex(""">\s*([0-9]+(?:\.[0-9]+)?)\s*(Bytes|Byte|KB|MB|GB|TB)\s*<""")
        val out = ArrayList<GrabbedFile>()
        for ((i, m) in matches.withIndex()) {
            val href = PageLinks.decode(m.groupValues[1])
            val fileUrl = "https://github.com$href"
            val name = if ("/archive/refs/tags/" in href) {
                val ext = if (href.endsWith(".tar.gz", true)) ".tar.gz" else ".zip"
                "$repo-${percentDecodeOr(tag).removePrefix("v")}$ext"
            } else PageLinks.fileNameOf(fileUrl)
            if (name.isBlank()) continue
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else html.length
            val size = sizeText.find(html.substring(m.range.last, end))?.let { s ->
                val value = s.groupValues[1].toDoubleOrNull() ?: return@let -1L
                val unit = when (s.groupValues[2]) {
                    "KB" -> 1024.0
                    "MB" -> 1024.0 * 1024
                    "GB" -> 1024.0 * 1024 * 1024
                    "TB" -> 1024.0 * 1024 * 1024 * 1024
                    else -> 1.0
                }
                (value * unit).toLong()
            } ?: -1L
            out.add(GrabbedFile(fileUrl, name, FileCategory.categoryFor(name), size, pageUrl))
        }
        return out
    }
}
