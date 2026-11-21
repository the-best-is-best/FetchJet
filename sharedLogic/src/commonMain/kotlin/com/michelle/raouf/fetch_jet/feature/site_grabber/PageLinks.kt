package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.core.settings.FileCategory

data class PageScan(
    val files: List<String>,
    val pages: List<String>,
    val maybeFiles: List<String>,
    val needsScript: Boolean
)

object PageLinks {
    private val fileAttributes = setOf(
        "src", "data-src", "data-lazy-src", "data-original", "data-file", "data-download", "data-full",
        "data-image", "data-video", "data-audio", "poster", "data-poster", "data-bg", "data-zoom-image"
    )
    private val pageAttributes = setOf("href", "data-href", "data-url", "data-link")
    private val setAttributes = setOf("srcset", "data-srcset", "data-lazy-srcset")
    private val metaProperties = setOf(
        "og:image", "og:image:url", "og:image:secure_url", "og:video", "og:video:url", "og:video:secure_url",
        "og:audio", "og:audio:url", "twitter:image", "twitter:player:stream"
    )
    private val downloadWords = listOf("download", "attachment", "getfile", "get_file", "file.php", "dl.php", "/dl/", "/file/", "/files/")

    private val tagRegex = Regex("""<([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>""")
    private val attrRegex = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val plainUrlRegex = Regex("""https?://[^\s"'<>\\)\]]+""")

    fun scan(html: String, pageUrl: String): PageScan {
        val base = baseOf(html, pageUrl)
        val files = LinkedHashSet<String>()
        val pages = LinkedHashSet<String>()
        val maybe = LinkedHashSet<String>()
        var anchors = 0
        var scripts = 0

        fun add(raw: String, fromFileAttribute: Boolean, anchor: Boolean) {
            val url = resolve(base, raw) ?: return
            when {
                fromFileAttribute -> files.add(url)
                isFileUrl(url) -> files.add(url)
                anchor && looksLikeDownload(url) -> maybe.add(url)
                anchor -> pages.add(url)
            }
        }

        for (tag in tagRegex.findAll(html)) {
            val name = tag.groupValues[1].lowercase()
            if (name == "script") {
                scripts++
                continue
            }
            if (name == "a") anchors++
            val attributes = attributesOf(tag.groupValues[2])
            if (name == "meta") {
                val key = (attributes["property"] ?: attributes["name"])?.lowercase()
                if (key != null && key in metaProperties) attributes["content"]?.let { add(it, true, false) }
                continue
            }
            if (name == "base") continue
            for ((attribute, value) in attributes) {
                when {
                    attribute in fileAttributes -> add(value, !(name == "a" || name == "iframe"), name == "a")
                    attribute in setAttributes -> largestOf(value)?.let { add(it, true, false) }
                    attribute in pageAttributes -> add(value, false, name == "a" || attribute != "href")
                    attribute.startsWith("data-") && value.startsWith("http", ignoreCase = true) -> add(value, false, false)
                }
            }
        }
        for (found in plainUrlRegex.findAll(html)) {
            val url = decode(found.value).trimEnd('.', ',', ';', ':', '!', '?', '&')
            val resolved = resolve(base, url) ?: continue
            if (isFileUrl(resolved)) files.add(resolved)
        }
        val needsScript = files.isEmpty() && pages.size < 3 && anchors < 5 && scripts >= 2 ||
                Regex("""<div[^>]+id\s*=\s*["'](root|app|__next|__nuxt)["'][^>]*>\s*</div>""", RegexOption.IGNORE_CASE).containsMatchIn(html) &&
                files.isEmpty() && anchors < 5
        return PageScan(files.toList(), pages.filter { it !in files }.toList(), maybe.filter { it !in files }.toList(), needsScript)
    }

    fun titleOf(html: String): String {
        val raw = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
            ?: return ""
        return decode(raw).replace(Regex("""\s+"""), " ").trim().take(80)
    }

    fun nameOf(url: String, title: String): String {
        val host = hostOf(url).removePrefix("www.")
        val brand = host.split('.').let { if (it.size >= 2) it[it.size - 2] else host }.lowercase()
        val parts = title.split(Regex("""\s+[·|\-–—]\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
        val fromTitle = parts.firstOrNull { part ->
            val low = part.lowercase()
            low != brand && low != host && !low.startsWith("index of")
        }
        val segments = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
            .split('/').filter { it.isNotEmpty() }.map {
                runCatching { com.michelle.raouf.fetch_jet.feature.http_downloader.percentDecode(it) }.getOrDefault(it)
            }
        val fromPath = segments.lastOrNull { !it.contains('.') || it.length > 12 }?.takeIf { it.isNotBlank() }
        return (fromTitle ?: fromPath ?: host).take(50).trim()
    }

    fun isFileUrl(url: String): Boolean {
        val name = fileNameOf(url)
        return FileCategory.folderFor(name) != null
    }

    fun looksLikeDownload(url: String): Boolean {
        val lower = url.lowercase()
        return downloadWords.any { it in lower }
    }

    fun fileNameOf(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
        val last = path.trimEnd('/').substringAfterLast('/')
        return runCatching { com.michelle.raouf.fetch_jet.feature.http_downloader.percentDecode(last) }.getOrDefault(last)
    }

    fun hostOf(url: String): String = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
        .substringAfter('@').substringBefore(':').lowercase()

    fun sameSite(a: String, b: String): Boolean {
        val x = hostOf(a)
        val y = hostOf(b)
        if (x == y) return true
        val xs = x.split('.')
        val ys = y.split('.')
        if (xs.size < 2 || ys.size < 2) return false
        return xs.takeLast(2) == ys.takeLast(2)
    }

    fun resolve(base: String, raw: String): String? {
        val value = decode(raw).trim()
        if (value.isEmpty() || value.startsWith("#")) return null
        val lower = value.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("data:") ||
            lower.startsWith("blob:") || lower.startsWith("about:") || lower.startsWith("magnet:")
        ) return null
        val noFragment = value.substringBefore('#')
        val scheme = base.substringBefore("://")
        val rest = base.substringAfter("://").substringBefore('#')
        val host = rest.substringBefore('/')
        val path = if ('/' in rest) "/" + rest.substringAfter('/').substringBefore('?') else "/"
        val out = when {
            noFragment.startsWith("http://", true) || noFragment.startsWith("https://", true) -> noFragment
            noFragment.startsWith("//") -> "$scheme:$noFragment"
            noFragment.startsWith("/") -> "$scheme://$host$noFragment"
            noFragment.startsWith("?") -> "$scheme://$host$path$noFragment"
            else -> "$scheme://$host${path.substringBeforeLast('/', "")}/$noFragment"
        }
        if (!out.startsWith("http://", true) && !out.startsWith("https://", true)) return null
        return normalizeDots(out)
    }

    private fun normalizeDots(url: String): String {
        val scheme = url.substringBefore("://")
        val rest = url.substringAfter("://")
        val host = rest.substringBefore('/')
        val tail = if ('/' in rest) rest.substring(host.length) else ""
        val query = if ('?' in tail) "?" + tail.substringAfter('?') else ""
        val path = tail.substringBefore('?')
        if ("/." !in path) return url
        val parts = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "." -> {}
                ".." -> if (parts.size > 1) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return "$scheme://$host${parts.joinToString("/")}$query"
    }

    private fun baseOf(html: String, pageUrl: String): String {
        val tag = Regex("""<base\b[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value ?: return pageUrl
        val href = attributesOf(tag.removePrefix("<base").removeSuffix(">"))["href"] ?: return pageUrl
        return resolve(pageUrl, href) ?: pageUrl
    }

    private fun attributesOf(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in attrRegex.findAll(text)) {
            val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            out[m.groupValues[1].lowercase()] = value
        }
        return out
    }

    private fun largestOf(srcset: String): String? {
        var best: String? = null
        var bestSize = -1.0
        for (candidate in srcset.split(',')) {
            val pieces = candidate.trim().split(Regex("""\s+"""))
            val url = pieces.firstOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val size = pieces.getOrNull(1)?.let { d -> d.dropLast(1).toDoubleOrNull() } ?: 1.0
            if (size >= bestSize) {
                bestSize = size
                best = url
            }
        }
        return best
    }

    fun decode(text: String): String {
        if ('&' !in text) return text
        return text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&#x2F;", "/").replace("&#47;", "/")
    }
}
