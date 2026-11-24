package com.michelle.raouf.fetch_jet.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SiteCookieJarTest {
    @Test
    fun anAddressOrLocalhostKeepsItsFullNameAndPort() {
        assertEquals("127.0.0.1:18300", SiteCookieJar.siteOf("http://127.0.0.1:18300/login"))
        assertEquals("127.0.0.1:18200", SiteCookieJar.siteOf("http://127.0.0.1:18200/file/1MB.bin"))
        assertEquals("10.0.0.1", SiteCookieJar.siteOf("http://10.0.0.1/a"))
        assertEquals("192.168.0.1:8080", SiteCookieJar.siteOf("https://192.168.0.1:8080/x?y=1"))
        assertEquals("localhost:3000", SiteCookieJar.siteOf("http://localhost:3000/"))
        assertEquals("[::1]:80", SiteCookieJar.siteOf("http://[::1]:80/"))
        assertNotEquals(SiteCookieJar.siteOf("http://10.0.0.1/a"), SiteCookieJar.siteOf("http://127.0.0.1/a"))
    }

    @Test
    fun namedSitesKeepTheirOldGrouping() {
        assertEquals("udemy.com", SiteCookieJar.siteOf("https://www.udemy.com/course/x"))
        assertEquals("youtube.com", SiteCookieJar.siteOf("https://m.youtube.com/watch?v=1"))
        assertEquals("youtube.com", SiteCookieJar.siteOf("https://youtu.be/abc"))
        assertEquals("example.com", SiteCookieJar.siteOf("https://files.cdn.example.com/a.zip"))
        assertEquals("someone.github.io", SiteCookieJar.siteOf("https://someone.github.io/page"))
    }

    @Test
    fun aCookieRememberedForOnePortIsNotPastedOnAnotherAddress() {
        SiteCookieJar.remember("http://127.0.0.1:18300/login", mapOf("Cookie" to "sess=abc", "Referer" to "http://127.0.0.1:18300/"))
        assertEquals(emptyMap(), SiteCookieJar.headersFor("http://127.0.0.1:18200/file/1MB.bin"))
        assertEquals(emptyMap(), SiteCookieJar.headersFor("http://10.0.0.1/file.bin"))
        assertEquals("sess=abc", SiteCookieJar.headersFor("http://127.0.0.1:18300/secret.bin")["Cookie"])
    }
}
