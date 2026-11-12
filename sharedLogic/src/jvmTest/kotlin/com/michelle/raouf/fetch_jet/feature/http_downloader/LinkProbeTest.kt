package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkProbeTest {
    private fun failed(result: LinkProbeResult): LinkProbeResult.Failed = result as LinkProbeResult.Failed

    @Test
    fun fileWithRangesIsResumable() {
        val result = classifyHttpProbe(200, "application/octet-stream", 3L * 1024 * 1024, acceptRanges = true)
        assertEquals(LinkProbeResult.Ok(3L * 1024 * 1024, true, ""), result)
    }

    @Test
    fun fileWithoutRangesIsNotResumable() {
        val result = classifyHttpProbe(200, "application/zip", 500L, acceptRanges = false, fileName = "a.zip")
        assertEquals(LinkProbeResult.Ok(500L, false, "a.zip"), result)
    }

    @Test
    fun unknownSizeIsNeverResumable() {
        val result = classifyHttpProbe(206, "video/mp4", -1L, acceptRanges = true)
        assertEquals(LinkProbeResult.Ok(-1L, false, ""), result)
    }

    @Test
    fun statusCodesBecomeCodes() {
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.REFUSED, 403), classifyHttpProbe(403, "text/plain", 10L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.NOT_FOUND, 404), classifyHttpProbe(404, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.NOT_FOUND, 410), classifyHttpProbe(410, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.LOGIN_NEEDED, 401), classifyHttpProbe(401, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.BUSY, 429), classifyHttpProbe(429, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.BUSY, 503), classifyHttpProbe(503, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.SERVER_ERROR, 500), classifyHttpProbe(500, "", -1L, false))
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.HTTP_ERROR, 418), classifyHttpProbe(418, "", -1L, false))
    }

    @Test
    fun htmlIsAWebPage() {
        val result = classifyHttpProbe(200, "text/html; charset=utf-8", 2000L, true, pageText = "<html><body>Hello</body></html>")
        assertEquals(LinkProbeResult.Failed(LinkProbeCode.WEB_PAGE, 200), result)
    }

    @Test
    fun robotPageIsARobotCheckWhateverTheStatus() {
        val page = "<html><body><h1>Are you a robot?</h1><p>Please verify you are human.</p></body></html>"
        assertEquals(LinkProbeCode.ROBOT, failed(classifyHttpProbe(200, "text/html", 100L, false, pageText = page)).code)
        assertEquals(LinkProbeCode.ROBOT, failed(classifyHttpProbe(403, "text/html", 100L, false, pageText = "<title>Just a moment...</title>")).code)
        assertEquals(LinkProbeCode.REFUSED, failed(classifyHttpProbe(403, "text/html", 100L, false, pageText = "<h1>Forbidden</h1>")).code)
        assertEquals(LinkProbeCode.REFUSED, failed(classifyHttpProbe(403, "application/json", 100L, false, pageText = "captcha")).code)
    }

    @Test
    fun exceptionsBecomeCodes() {
        assertEquals(LinkProbeCode.TIMEOUT, probeErrorCode("Connect timeout has expired [url=http://x, connect_timeout=60000 ms]"))
        assertEquals(LinkProbeCode.TIMEOUT, probeErrorCode("Read timed out"))
        assertEquals(LinkProbeCode.REDIRECTS, probeErrorCode("Too many redirects (10)"))
        assertEquals(LinkProbeCode.SECURE_FAILED, probeErrorCode("PKIX path building failed: unable to find valid certificate"))
        assertEquals(LinkProbeCode.NO_CONNECTION, probeErrorCode("Connection refused"))
        assertEquals(LinkProbeCode.NO_CONNECTION, probeErrorCode(""))
    }

    @Test
    fun ftpRepliesBecomeCodes() {
        assertEquals(LinkProbeCode.FTP_LOGIN, ftpProbeCode(530, "The FTP server refused the login: 530 Login incorrect."))
        assertEquals(LinkProbeCode.BUSY, ftpProbeCode(421, "The FTP server is not ready: 421 Too many connections"))
        assertEquals(LinkProbeCode.NOT_FOUND, ftpProbeCode(550, "550 No such file"))
        assertEquals(LinkProbeCode.SECURE_FAILED, ftpProbeCode(0, "The secure (TLS) connection to the FTP server failed: x"))
        assertEquals(LinkProbeCode.NO_CONNECTION, ftpProbeCode(0, "The FTP server closed the connection"))
        assertEquals(LinkProbeCode.FTP_ERROR, ftpProbeCode(500, "The FTP server cannot send binary files: 500 x"))
    }

    @Test
    fun ftpToolExitCodesBecomeCodes() {
        assertEquals(LinkProbeCode.NO_CONNECTION, ftpToolProbeCode(7))
        assertEquals(LinkProbeCode.NOT_FOUND, ftpToolProbeCode(78))
        assertEquals(LinkProbeCode.TIMEOUT, ftpToolProbeCode(28))
        assertEquals(LinkProbeCode.SECURE_FAILED, ftpToolProbeCode(60))
        assertEquals(LinkProbeCode.FTP_LOGIN, ftpToolProbeCode(67))
        assertEquals(LinkProbeCode.FTP_ERROR, ftpToolProbeCode(1))
    }

    @Test
    fun serverNameComesFromTheHeaderOrTheRedirect() {
        assertEquals("real name.zip", serverFileName("http://a.com/dl?id=5", "http://a.com/dl?id=5", "attachment; filename=\"real name.zip\""))
        assertEquals("", serverFileName("http://a.com/x.bin", "http://a.com/x.bin", null))
        assertEquals("setup v2.exe", serverFileName("http://a.com/get/latest", "http://cdn.a.com/files/setup%20v2.exe?t=1", null))
        assertEquals("", serverFileName("http://a.com/x.bin?a=1", "http://cdn.a.com/x.bin?b=2", null))
        assertEquals("", serverFileName("http://a.com/get", "http://a.com/get/", null))
    }

    @Test
    fun onlyDirectLinksCanBeProbed() {
        assertTrue(canProbeLink("https://a.com/x.bin"))
        assertTrue(canProbeLink(" HTTP://a.com/x.bin "))
        assertTrue(canProbeLink("ftp://fj:secret@127.0.0.1:2121/x/2MB.bin"))
        assertTrue(canProbeLink("ftps://a.com/x.bin"))
        assertTrue(canProbeLink("ftpes://a.com/x.bin"))
        assertFalse(canProbeLink("magnet:?xt=urn:btih:abc"))
        assertFalse(canProbeLink("C:\\files\\a.torrent"))
        assertFalse(canProbeLink("https://a.com/x.bin https://a.com/y.bin"))
        assertFalse(canProbeLink(""))
    }
}
