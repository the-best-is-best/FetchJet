package com.michelle.raouf.fetch_jet.core.more

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

data class MoreLink(val key: String, val url: String)

object MoreLinks {
    const val SITE = "https://the-best-is-best.github.io/FetchJet/"
    const val SHARE_TEXT = "FetchJet - download manager"
    const val SHARE_LINK = SITE

    val community: List<MoreLink> = listOf(
        MoreLink("facebook", "https://www.facebook.com/share/1JDiWEtQyR/"),
        MoreLink("issues", "https://github.com/the-best-is-best/FetchJet/issues"),
        MoreLink("source", "https://github.com/the-best-is-best/FetchJet"),
        MoreLink("privacy", "https://the-best-is-best.github.io/FetchJet/privacy.html"),
        MoreLink("stats", "https://fetchjet.the-best-is-best.workers.dev/dashboard")
    )

    fun communityTitle(key: String): String {
        val s = LocalizationManager.Strings
        return when (key) {
            "facebook" -> s.moreFacebook
            "issues" -> s.moreIssues
            "source" -> s.moreSource
            "privacy" -> s.morePrivacy
            else -> s.moreStats
        }
    }

    fun shareText(): String = "$SHARE_TEXT\n$SHARE_LINK"

    var systemShare: ((String) -> Boolean)? = null

    fun share(copyFallback: (String) -> Unit): Boolean {
        val text = shareText()
        val shared = runCatching { systemShare?.invoke(text) == true }.getOrDefault(false)
        if (!shared) copyFallback(text)
        return shared
    }
}
