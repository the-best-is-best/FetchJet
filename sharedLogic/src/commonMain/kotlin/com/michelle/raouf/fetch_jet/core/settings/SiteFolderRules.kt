package com.michelle.raouf.fetch_jet.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SiteFolderRule(val site: String, val folder: String) {
    private val host: String get() = SiteFolderRules.cleanSite(site).substringBefore('/')
    private val path: String get() = SiteFolderRules.cleanSite(site).substringAfter('/', "")

    fun matchesLink(linkHost: String, linkPath: String): Boolean {
        val h = linkHost.lowercase().trimEnd('.').removePrefix("www.")
        val s = host
        if (s.isEmpty() || !(h == s || h.endsWith(".$s"))) return false
        val p = path
        if (p.isEmpty()) return true
        val l = linkPath.lowercase().trim('/')
        return l == p || l.startsWith("$p/")
    }
}

object SiteFolderRules {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SiteFolderRule.serializer())

    private fun linkParts(url: String): Pair<String, String>? {
        val text = url.trim()
        if (!text.contains("://")) return null
        val rest = text.substringAfter("://").substringBefore('#').substringBefore('?')
        val authority = rest.substringBefore('/')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.').removePrefix("www.")
        if (host.isEmpty()) return null
        val path = if (rest.contains('/')) rest.substringAfter('/') else ""
        return host to path
    }

    fun hostOf(url: String): String = linkParts(url)?.first.orEmpty()

    fun cleanSite(text: String): String {
        var t = text.trim().substringBefore('#').substringBefore('?')
        if (t.contains("://")) t = t.substringAfter("://")
        val authority = t.substringBefore('/')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.').removePrefix("www.")
        val path = t.substringAfter('/', "").lowercase().split('/').filter { it.isNotEmpty() }.joinToString("/")
        return if (path.isEmpty()) host else "$host/$path"
    }

    fun isValidSite(text: String): Boolean {
        val site = cleanSite(text)
        val host = site.substringBefore('/')
        return host.contains('.') && !host.startsWith('.') && site.none { it.isWhitespace() || it in "\\?#" }
    }

    fun match(rules: List<SiteFolderRule>, vararg urls: String?): SiteFolderRule? {
        val links = urls.mapNotNull { url -> url?.let { linkParts(it) } }
        if (links.isEmpty()) return null
        return rules.filter { rule -> links.any { (host, path) -> rule.matchesLink(host, path) } }
            .maxByOrNull { cleanSite(it.site).length }
    }

    fun encode(rules: List<SiteFolderRule>): String = json.encodeToString(serializer, rules)

    fun decode(text: String?): List<SiteFolderRule> =
        if (text.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
            .filter { isValidSite(it.site) && it.folder.isNotBlank() }

    fun samePath(a: String, b: String): Boolean =
        a.trim().trimEnd('/', '\\').replace('\\', '/').equals(b.trim().trimEnd('/', '\\').replace('\\', '/'), ignoreCase = true)
}
