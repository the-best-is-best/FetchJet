package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HostLimitsTest {
    private val megabyte = 1024L * 1024

    private fun chunks(count: Int): List<DownloadChunk> = (0 until count).map {
        DownloadChunk(it, it * megabyte, (it + 1) * megabyte - 1, 0L)
    }

    @Test
    fun nothingIsKnownAtFirst() {
        val limits = HostLimits { 0L }
        assertNull(limits.capFor("files.example.com"))
        assertNull(limits.capFor(""))
    }

    @Test
    fun learnsACap() {
        val limits = HostLimits { 0L }
        limits.learn("files.example.com", 2)
        assertEquals(2, limits.capFor("files.example.com"))
        assertNull(limits.capFor("other.example.com"))
    }

    @Test
    fun keepsTheSmallerOfTwo() {
        val limits = HostLimits { 0L }
        limits.learn("h", 4)
        limits.learn("h", 2)
        assertEquals(2, limits.capFor("h"))
        limits.learn("h", 6)
        assertEquals(2, limits.capFor("h"))
        limits.learn("h", 0)
        assertEquals(1, limits.capFor("h"))
    }

    @Test
    fun anEmptyHostIsNeverRemembered() {
        val limits = HostLimits { 0L }
        limits.learn("", 2)
        assertNull(limits.capFor(""))
    }

    @Test
    fun forgetsAfterADay() {
        var now = 1_000L
        val limits = HostLimits { now }
        limits.learn("h", 2)
        now += HostLimits.EXPIRE_MS - 1
        assertEquals(2, limits.capFor("h"))
        now += 1
        assertNull(limits.capFor("h"))
        limits.learn("h", 5)
        assertEquals(5, limits.capFor("h"))
    }

    @Test
    fun aNewRefusalRestartsTheDay() {
        var now = 0L
        val limits = HostLimits { now }
        limits.learn("h", 2)
        now = HostLimits.EXPIRE_MS - 10
        limits.learn("h", 3)
        now = HostLimits.EXPIRE_MS + 10
        assertEquals(2, limits.capFor("h"))
    }

    @Test
    fun climbsByOneWhenMoreConnectionsWorked() {
        var now = 0L
        val limits = HostLimits { now }
        limits.learn("h", 2)
        limits.climb("h", 2)
        assertEquals(2, limits.capFor("h"))
        limits.climb("h", 5)
        assertEquals(3, limits.capFor("h"))
        limits.climb("h", 5)
        assertEquals(4, limits.capFor("h"))
        limits.climb("unknown", 5)
        assertNull(limits.capFor("unknown"))
        now = HostLimits.EXPIRE_MS
        assertNull(limits.capFor("h"))
    }

    @Test
    fun aCapThatClimbsToTheTopIsForgotten() {
        val limits = HostLimits { 0L }
        limits.learn("h", ChunkLayout.MAX_CONNECTIONS - 1)
        limits.climb("h", ChunkLayout.MAX_CONNECTIONS)
        assertNull(limits.capFor("h"))
    }

    @Test
    fun hostOfAUrl() {
        assertEquals("127.0.0.1:18200", HostLimits.hostOf("http://127.0.0.1:18200/file/8MB.bin?key=a&maxconn=2"))
        assertEquals("example.com", HostLimits.hostOf("https://User:p@ss@Example.com/a/b.zip"))
        assertEquals("files.example.com", HostLimits.hostOf("ftp://files.example.com"))
        assertEquals("example.com", HostLimits.hostOf("https://example.com?x=1"))
        assertEquals("", HostLimits.hostOf("C:\\Users\\x\\a.bin"))
        assertEquals("", HostLimits.hostOf(""))
    }

    @Test
    fun aLayoutWithNothingLearnedHandsOutEveryPiece(): Unit = runBlocking {
        val layout = ChunkLayout(chunks(8), "h", HostLimits { 0L }, { 0L })
        repeat(8) { assertNotNull(layout.next()) }
        assertNull(layout.next())
    }

    @Test
    fun aLayoutStartsWithTheLearnedCap(): Unit = runBlocking {
        val limits = HostLimits { 0L }
        limits.learn("h", 2)
        val layout = ChunkLayout(chunks(8), "h", limits, { 0L })
        val first = assertNotNull(layout.next())
        assertNotNull(layout.next())
        assertNull(layout.next())
        assertNull(layout.next())
        layout.release(first)
        assertNotNull(layout.next())
        assertNull(layout.next())
        assertEquals(2, layout.snapshot().count { it.isActive })
    }

    @Test
    fun aLayoutOfAnotherHostIsNotLimited(): Unit = runBlocking {
        val limits = HostLimits { 0L }
        limits.learn("h", 2)
        val layout = ChunkLayout(chunks(8), "other", limits, { 0L })
        repeat(8) { assertNotNull(layout.next()) }
    }

    @Test
    fun aBusyAnswerIsRememberedForTheHost(): Unit = runBlocking {
        val limits = HostLimits { 0L }
        val layout = ChunkLayout(chunks(8), "h", limits, { 0L })
        val taken = (0 until 6).map { assertNotNull(layout.next()) }
        layout.serverBusy(taken[5], "HTTP 429")
        assertEquals(5, limits.capFor("h"))
        layout.serverBusy(taken[4], "HTTP 429")
        layout.serverBusy(taken[3], "HTTP 429")
        layout.serverBusy(taken[2], "HTTP 429")
        assertEquals(2, limits.capFor("h"))
        assertNull(layout.next())
    }

    @Test
    fun theLearnedCapClimbsWhileEveryConnectionWorks(): Unit = runBlocking {
        var now = 0L
        val limits = HostLimits { now }
        limits.learn("h", 2)
        val layout = ChunkLayout(chunks(8), "h", limits, { now })
        assertNotNull(layout.next())
        assertNotNull(layout.next())
        assertNull(layout.next())

        now = ChunkLayout.CAP_RAISE_MS
        assertNotNull(layout.next())
        assertNull(layout.next())
        assertEquals(2, limits.capFor("h"))

        now = ChunkLayout.CAP_RAISE_MS * 2
        val fourth = assertNotNull(layout.next())
        assertEquals(3, limits.capFor("h"))

        layout.serverBusy(fourth, "HTTP 429")
        assertEquals(3, limits.capFor("h"))

        now = ChunkLayout.CAP_RAISE_MS * 3
        val again = assertNotNull(layout.next())
        assertEquals(3, limits.capFor("h"))
        layout.serverBusy(again, "HTTP 429")
        assertEquals(3, limits.capFor("h"))
    }

    @Test
    fun noClimbWhileConnectionsAreIdle(): Unit = runBlocking {
        var now = 0L
        val limits = HostLimits { now }
        limits.learn("h", 3)
        val layout = ChunkLayout(chunks(2), "h", limits, { now })
        assertNotNull(layout.next())
        assertNotNull(layout.next())
        now = ChunkLayout.CAP_RAISE_MS
        assertNull(layout.next())
        now = ChunkLayout.CAP_RAISE_MS * 2
        assertNull(layout.next())
        assertEquals(3, limits.capFor("h"))
    }
}
