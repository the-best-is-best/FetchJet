package com.michelle.raouf.fetch_jet.core.settings

import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SiteFolderRule(val site: String, val folder: String) {
    fun matches(host: String): Boolean {
        val h = host.lowercase().trimEnd('.').removePrefix("www.")
        val s = SiteLogin.cleanSite(site)
        return s.isNotEmpty() && (h == s || h.endsWith(".$s"))
    }
}

object SiteFolderRules {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SiteFolderRule.serializer())

    fun hostOf(url: String): String {
        val text = url.trim()
        if (!text.contains("://")) return ""
        return SiteLogin.cleanSite(text)
    }

    fun cleanSite(text: String): String = SiteLogin.cleanSite(text)

    fun isValidSite(text: String): Boolean {
        val site = cleanSite(text)
        return site.contains('.') && site.none { it.isWhitespace() || it in "/\\?#" } && !site.startsWith('.')
    }

    fun match(rules: List<SiteFolderRule>, vararg urls: String?): SiteFolderRule? {
        for (url in urls) {
            val host = hostOf(url ?: continue)
            if (host.isEmpty()) continue
            rules.filter { it.matches(host) }.maxByOrNull { cleanSite(it.site).length }?.let { return it }
        }
        return null
    }

    fun encode(rules: List<SiteFolderRule>): String = json.encodeToString(serializer, rules)

    fun decode(text: String?): List<SiteFolderRule> =
        if (text.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
            .filter { isValidSite(it.site) && it.folder.isNotBlank() }

    fun samePath(a: String, b: String): Boolean =
        a.trim().trimEnd('/', '\\').replace('\\', '/').equals(b.trim().trimEnd('/', '\\').replace('\\', '/'), ignoreCase = true)
}
