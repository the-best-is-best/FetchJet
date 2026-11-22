package com.michelle.raouf.fetch_jet.core.more

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoreLinksTest {
    @Test
    fun everyLinkIsHttps() {
        val urls = MoreLinks.community.map { it.url } + MoreLinks.SITE + MoreLinks.SHARE_LINK
        assertTrue(urls.all { it.startsWith("https://") }, urls.toString())
    }

    @Test
    fun communityHasNoDuplicates() {
        assertEquals(5, MoreLinks.community.size)
        assertEquals(5, MoreLinks.community.map { it.url }.toSet().size)
        assertEquals(5, MoreLinks.community.map { it.key }.toSet().size)
    }

    @Test
    fun shareCarriesTheTextAndTheSiteLink() {
        assertEquals("FetchJet - download manager\nhttps://the-best-is-best.github.io/FetchJet/", MoreLinks.shareText())
    }

    @Test
    fun shareFallsBackToCopyingWhenThereIsNoSystemShare() {
        MoreLinks.systemShare = null
        var copied: String? = null
        assertEquals(false, MoreLinks.share { copied = it })
        assertEquals(MoreLinks.shareText(), copied)
        MoreLinks.systemShare = { true }
        copied = null
        assertEquals(true, MoreLinks.share { copied = it })
        assertEquals(null, copied)
        MoreLinks.systemShare = null
    }
}
