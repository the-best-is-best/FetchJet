package com.michelle.raouf.fetch_jet.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteFolderRulesTest {
    private val rules = listOf(
        SiteFolderRule("example.com", "/a"),
        SiteFolderRule("files.example.com", "/b"),
        SiteFolderRule("other.org", "/c"),
    )

    @Test
    fun theSiteAndItsSubDomainsMatch() {
        assertEquals("/a", SiteFolderRules.match(rules, "https://example.com/x.zip")?.folder)
        assertEquals("/a", SiteFolderRules.match(rules, "https://www.example.com/x.zip")?.folder)
        assertEquals("/a", SiteFolderRules.match(rules, "http://cdn.example.com:8080/x.zip")?.folder)
    }

    @Test
    fun theMostSpecificSiteWins() {
        assertEquals("/b", SiteFolderRules.match(rules, "https://files.example.com/x.zip")?.folder)
        assertEquals("/b", SiteFolderRules.match(rules, "https://dl.files.example.com/x.zip")?.folder)
    }

    @Test
    fun lookAlikesAndOtherSitesDoNot() {
        assertNull(SiteFolderRules.match(rules, "https://notexample.com/x.zip"))
        assertNull(SiteFolderRules.match(rules, "https://example.com.evil.net/x.zip"))
        assertNull(SiteFolderRules.match(rules, "magnet:?xt=urn:btih:abc"))
        assertNull(SiteFolderRules.match(rules, "", null))
    }

    @Test
    fun thePageIsTriedWhenTheLinkDoesNotMatch() {
        assertEquals("/c", SiteFolderRules.match(rules, "https://cdn.somewhere.net/v.mp4", "https://other.org/watch")?.folder)
    }

    @Test
    fun sitesAreCleanedAndChecked() {
        assertEquals("example.com", SiteFolderRules.cleanSite("https://www.Example.com/page?x=1"))
        assertTrue(SiteFolderRules.isValidSite("example.com"))
        assertFalse(SiteFolderRules.isValidSite("localhost"))
        assertFalse(SiteFolderRules.isValidSite("not a site"))
    }

    @Test
    fun encodeAndDecode() {
        val text = SiteFolderRules.encode(rules)
        assertEquals(rules, SiteFolderRules.decode(text))
        assertEquals(emptyList(), SiteFolderRules.decode("not json"))
        assertEquals(emptyList(), SiteFolderRules.decode(null))
    }
}
