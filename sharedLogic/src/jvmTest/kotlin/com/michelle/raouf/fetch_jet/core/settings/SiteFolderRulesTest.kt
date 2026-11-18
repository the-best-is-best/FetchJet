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
    fun aRuleCanBeAPartOfALink() {
        val withPaths = listOf(
            SiteFolderRule("github.com", "/all"),
            SiteFolderRule("github.com/the-best-is-best/FetchJet", "/fj"),
            SiteFolderRule("udemy.com/course/kotlin", "/kotlin"),
        )
        assertEquals("/fj", SiteFolderRules.match(withPaths, "https://github.com/the-best-is-best/FetchJet/releases/download/v1.4.1/a.msi")?.folder)
        assertEquals("/fj", SiteFolderRules.match(withPaths, "https://github.com/The-Best-Is-Best/fetchjet")?.folder)
        assertEquals("/all", SiteFolderRules.match(withPaths, "https://github.com/the-best-is-best/FetchJetOther/a.zip")?.folder)
        assertEquals("/all", SiteFolderRules.match(withPaths, "https://github.com/other/repo/a.zip")?.folder)
        assertEquals("/kotlin", SiteFolderRules.match(withPaths, "https://cdn.udemycdn.com/v.mp4", "https://www.udemy.com/course/kotlin/learn/lecture/7?x=1")?.folder)
        assertNull(SiteFolderRules.match(withPaths, "https://www.udemy.com/course/swift/learn/lecture/7"))
        assertEquals("/fj", SiteFolderRules.match(withPaths, "https://objects.githubusercontent.com/x", "https://github.com/the-best-is-best/FetchJet/releases/tag/v1.4.1")?.folder)
    }

    @Test
    fun sitesAreCleanedAndChecked() {
        assertEquals("github.com/the-best-is-best/fetchjet", SiteFolderRules.cleanSite("https://www.GitHub.com/the-best-is-best/FetchJet/?tab=1"))
        assertTrue(SiteFolderRules.isValidSite("github.com/the-best-is-best"))
        assertEquals("example.com", SiteFolderRules.cleanSite("https://www.Example.com/?x=1"))
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
