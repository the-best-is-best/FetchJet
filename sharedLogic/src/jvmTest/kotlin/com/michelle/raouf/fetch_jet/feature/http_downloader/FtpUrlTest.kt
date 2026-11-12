package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.log.LogRedact
import com.michelle.raouf.fetch_jet.core.settings.ClipboardLinks
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.DetectLinkTypeUseCase
import com.michelle.raouf.fetch_jet.feature.torrent_engine.isLocalTorrentPath
import com.michelle.raouf.fetch_jet.presentation.dashboard.extractLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtpUrlTest {
    @Test
    fun plainFtpUsesPort21() {
        val t = parseFtpUrl("ftp://files.example.com/pub/tools/setup.zip")
        assertEquals("files.example.com", t.host)
        assertEquals(21, t.port)
        assertFalse(t.tls)
        assertFalse(t.implicitTls)
        assertNull(t.user)
        assertNull(t.password)
        assertEquals("pub/tools/setup.zip", t.path)
        assertEquals("/pub/tools/setup.zip", t.absolutePath)
        assertEquals("setup.zip", t.fileName)
        assertFalse(t.isFolder)
    }

    @Test
    fun ftpsUsesPort990WithTlsFromTheFirstByte() {
        val t = parseFtpUrl("ftps://files.example.com/a.bin")
        assertEquals(990, t.port)
        assertTrue(t.tls)
        assertTrue(t.implicitTls)
    }

    @Test
    fun ftpsOnPort21IsExplicitTls() {
        val t = parseFtpUrl("ftps://files.example.com:21/a.bin")
        assertEquals(21, t.port)
        assertTrue(t.tls)
        assertFalse(t.implicitTls)
    }

    @Test
    fun ftpesIsExplicitTlsOnPort21() {
        val t = parseFtpUrl("FTPES://files.example.com/a.bin")
        assertEquals(21, t.port)
        assertTrue(t.tls)
        assertFalse(t.implicitTls)
    }

    @Test
    fun loginIsPercentDecoded() {
        val t = parseFtpUrl("ftp://us%40er:p%3Ass%2Fw@files.example.com:2121/My%20File.txt")
        assertEquals("us@er", t.user)
        assertEquals("p:ss/w", t.password)
        assertEquals("files.example.com", t.host)
        assertEquals(2121, t.port)
        assertEquals("My File.txt", t.fileName)
    }

    @Test
    fun atSignInsideThePassword() {
        val t = parseFtpUrl("ftp://user:p@ss@files.example.com/a.bin")
        assertEquals("user", t.user)
        assertEquals("p@ss", t.password)
        assertEquals("files.example.com", t.host)
        assertEquals(21, t.port)
    }

    @Test
    fun userWithoutPassword() {
        val t = parseFtpUrl("ftp://anonymous@files.example.com/a.bin")
        assertEquals("anonymous", t.user)
        assertNull(t.password)
    }

    @Test
    fun ipv6Host() {
        val withPort = parseFtpUrl("ftp://[2001:db8::1]:2121/a.bin")
        assertEquals("2001:db8::1", withPort.host)
        assertEquals(2121, withPort.port)
        val noPort = parseFtpUrl("ftps://user:pw@[::1]/a.bin")
        assertEquals("::1", noPort.host)
        assertEquals(990, noPort.port)
        assertEquals("user", noPort.user)
        assertEquals("pw", noPort.password)
    }

    @Test
    fun folderLinks() {
        assertTrue(parseFtpUrl("ftp://files.example.com/pub/").isFolder)
        assertTrue(parseFtpUrl("ftp://files.example.com/").isFolder)
        assertTrue(parseFtpUrl("ftp://files.example.com").isFolder)
        assertEquals("pub", parseFtpUrl("ftp://files.example.com/pub/").fileName)
    }

    @Test
    fun otherSchemesAreRefused() {
        assertFailsWith<IllegalArgumentException> { parseFtpUrl("https://files.example.com/a.bin") }
        assertFailsWith<IllegalArgumentException> { parseFtpUrl("ftp:///a.bin") }
    }

    @Test
    fun ftpLinksAreRecognised() {
        assertTrue(isFtpLink("ftp://h/a"))
        assertTrue(isFtpLink("  FTPS://h/a"))
        assertTrue(isFtpLink("ftpes://h/a"))
        assertFalse(isFtpLink("https://h/ftp://x"))
        assertFalse(isFtpLink("sftp://h/a"))
        assertFalse(isFtpLink("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun fileNameForTheAddDialog() {
        assertEquals("My File.zip", ftpFileName("ftp://user:pw@h/dir/My%20File.zip"))
        assertEquals("README", ftpFileName("ftps://h/README"))
        assertEquals("", ftpFileName("ftp://h/"))
        assertEquals("", ftpFileName("https://h/a.zip"))
    }

    @Test
    fun displayUrlHidesOnlyThePassword() {
        assertEquals("ftp://user:***@h/dir/a.zip", displayUrl("ftp://user:secret@h/dir/a.zip"))
        assertEquals("ftp://user:***@h/a.zip", displayUrl("ftp://user:p@ss@h/a.zip"))
        assertEquals("ftps://u:***@[::1]:990/a", displayUrl("ftps://u:pw@[::1]:990/a"))
        assertEquals("ftp://user:***@h", displayUrl("ftp://user:secret@h"))
        assertEquals("https://u:***@h/x?a=1", displayUrl("https://u:pw@h/x?a=1"))
    }

    @Test
    fun displayUrlKeepsLinksWithoutAPassword() {
        for (url in listOf(
            "ftp://h/dir/a.zip",
            "ftp://user@h:21/a.zip",
            "ftp://[::1]:21/a.zip",
            "https://example.com:8080/@user/a:b",
            "https://example.com/x?mail=a:b@c",
            "magnet:?xt=urn:btih:abc",
            "C:\\Users\\x\\a.torrent",
            ""
        )) assertEquals(url, displayUrl(url))
    }

    @Test
    fun ftpIsAlwaysADirectDownload() {
        val detect = DetectLinkTypeUseCase()
        assertEquals(EngineType.HTTP_DIRECT, detect("ftp://h/a.zip"))
        assertEquals(EngineType.HTTP_DIRECT, detect("ftp://h/linux.torrent"))
        assertEquals(EngineType.HTTP_DIRECT, detect("FTPS://youtube.com/watch"))
        assertEquals(EngineType.HTTP_DIRECT, detect("ftpes://h/live/index.m3u8"))
        assertEquals(EngineType.BITTORRENT, detect("https://h/linux.torrent"))
        assertFalse(isLocalTorrentPath("ftp://h/linux.torrent"))
        assertTrue(isLocalTorrentPath("\"C:\\Users\\x\\linux.torrent\""))
    }

    @Test
    fun ftpLinksAreFoundInPastedText() {
        val text = "one ftp://h/a.zip, two ftps://u:pw@h/b.bin\nthree ftpes://h/c.iso and https://x.com/d.zip"
        assertEquals(
            listOf("ftp://h/a.zip", "ftps://u:pw@h/b.bin", "ftpes://h/c.iso", "https://x.com/d.zip"),
            extractLinks(text)
        )
    }

    @Test
    fun clipboardOffersFtpFiles() {
        assertEquals("ftp://h/dir/a.zip", ClipboardLinks.downloadableUrl("ftp://h/dir/a.zip"))
        assertEquals("ftps://u:pw@h/a.iso", ClipboardLinks.downloadableUrl("ftps://u:pw@h/a.iso"))
        assertNull(ClipboardLinks.downloadableUrl("ftp://h/dir/"))
        assertNull(ClipboardLinks.downloadableUrl("ftp://h"))
    }

    @Test
    fun logFilesNeverKeepTheFtpPassword() {
        assertEquals("open ftp://***@h/a.zip now", LogRedact.redact("open ftp://user:secret@h/a.zip now"))
        assertEquals("ftps://***@h:990/a.zip", LogRedact.redact("ftps://user:p@ss@h:990/a.zip"))
        assertEquals("ftp://h:21/a.zip", LogRedact.redact("ftp://h:21/a.zip"))
        assertEquals("http://h:8080/a@b", LogRedact.redact("http://h:8080/a@b"))
    }
}
