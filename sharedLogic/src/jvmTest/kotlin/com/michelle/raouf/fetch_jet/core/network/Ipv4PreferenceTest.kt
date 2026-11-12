package com.michelle.raouf.fetch_jet.core.network

import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ipv4PreferenceTest {
    private var time = 0L
    private val savedNow = Ipv4Preference.now
    private val savedSupported = Ipv4Preference.supported
    private val savedOnAdded = Ipv4Preference.onAdded

    private val v4 = InetAddress.getByAddress("files.example.com", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
    private val v6 = InetAddress.getByAddress(
        "files.example.com",
        byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
    )

    @BeforeTest
    fun prepare() {
        time = 1_000L
        Ipv4Preference.now = { time }
        Ipv4Preference.supported = true
        Ipv4Preference.onAdded = null
        Ipv4Preference.clear()
    }

    @AfterTest
    fun restore() {
        Ipv4Preference.clear()
        Ipv4Preference.now = savedNow
        Ipv4Preference.supported = savedSupported
        Ipv4Preference.onAdded = savedOnAdded
    }

    @Test
    fun addedHostIsPreferredWhateverTheCase() {
        assertFalse(Ipv4Preference.prefers("files.example.com"))
        Ipv4Preference.add("Files.Example.com.")
        assertTrue(Ipv4Preference.prefers("files.example.com"))
        assertTrue(Ipv4Preference.prefers("FILES.EXAMPLE.COM"))
    }

    @Test
    fun otherHostsAndSubDomainsAreNotTouched() {
        Ipv4Preference.add("files.example.com")
        assertFalse(Ipv4Preference.prefers("example.com"))
        assertFalse(Ipv4Preference.prefers("a.files.example.com"))
    }

    @Test
    fun removedHostIsForgotten() {
        Ipv4Preference.add("files.example.com")
        Ipv4Preference.remove("FILES.example.com")
        assertFalse(Ipv4Preference.prefers("files.example.com"))
    }

    @Test
    fun preferenceEndsAfterADay() {
        Ipv4Preference.add("files.example.com")
        time += Ipv4Preference.KEEP_MS - 1
        assertTrue(Ipv4Preference.prefers("files.example.com"))
        time += 1
        assertFalse(Ipv4Preference.prefers("files.example.com"))
        assertTrue(Ipv4Preference.canTry("files.example.com"))
    }

    @Test
    fun canTryOnlyOncePerHostAndOnlyWhenSupported() {
        assertTrue(Ipv4Preference.canTry("files.example.com"))
        Ipv4Preference.add("files.example.com")
        assertFalse(Ipv4Preference.canTry("files.example.com"))
        Ipv4Preference.remove("files.example.com")
        Ipv4Preference.supported = false
        assertFalse(Ipv4Preference.canTry("files.example.com"))
    }

    @Test
    fun addressesAreNotHosts() {
        assertFalse(Ipv4Preference.canTry("93.184.216.34"))
        assertFalse(Ipv4Preference.canTry("2001:db8::1"))
        assertFalse(Ipv4Preference.canTry(""))
        Ipv4Preference.add("93.184.216.34")
        assertFalse(Ipv4Preference.prefers("93.184.216.34"))
    }

    @Test
    fun addTellsTheListener() {
        var calls = 0
        Ipv4Preference.onAdded = { calls++ }
        Ipv4Preference.add("files.example.com")
        assertEquals(1, calls)
    }

    @Test
    fun ipv4OnlyKeepsTheIpv4Addresses() {
        assertEquals(listOf(v4), ipv4Only(listOf(v6, v4)))
    }

    @Test
    fun ipv4OnlyKeepsEverythingWhenThereIsNoIpv4() {
        assertEquals(listOf(v6), ipv4Only(listOf(v6)))
    }

    @Test
    fun dnsFiltersOnlyThePreferredHosts() {
        val dns = Ipv4PreferringDns { listOf(v6, v4) }
        assertEquals(listOf(v6, v4), dns.lookup("files.example.com"))
        Ipv4Preference.add("files.example.com")
        assertEquals(listOf(v4), dns.lookup("files.example.com"))
        assertEquals(listOf(v6, v4), dns.lookup("other.example.com"))
        Ipv4Preference.remove("files.example.com")
        assertEquals(listOf(v6, v4), dns.lookup("files.example.com"))
    }

    @Test
    fun dnsFallsBackToIpv6WhenTheHostHasNoIpv4() {
        val dns = Ipv4PreferringDns { listOf(v6) }
        Ipv4Preference.add("files.example.com")
        assertEquals(listOf(v6), dns.lookup("files.example.com"))
    }
}
