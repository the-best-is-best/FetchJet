package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.http_downloader.linkCarriesAccess
import com.tbib.kmp_localization.LocalizationManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailsLinksTest {
    @BeforeTest
    fun start() {
        LocalizationManager.start()
    }

    private fun task(url: String, hidden: Boolean = false, referer: String? = null) = DownloadTask(
        id = "t", url = url, fileName = "f.bin", destinationPath = "/tmp", engineType = EngineType.HTTP_DIRECT,
        hidden = hidden, requestHeaders = if (referer != null) mapOf("Referer" to referer) else emptyMap()
    )

    @Test
    fun linksThatCarryALoginOrATokenAreDetected() {
        assertTrue(linkCarriesAccess("ftp://user:pass@host/file"))
        assertTrue(linkCarriesAccess("https://host/file?token=abc"))
        assertTrue(linkCarriesAccess("https://host/file?a=1&X-Amz-Signature=zz"))
        assertTrue(linkCarriesAccess("https://host/file?Key=1"))
        assertFalse(linkCarriesAccess("https://host/file.zip"))
        assertFalse(linkCarriesAccess("https://host/file?id=5&name=x"))
        assertFalse(linkCarriesAccess("ftp://user@host/file"))
        assertFalse(linkCarriesAccess("https://host/monkey?x=1"))
    }

    @Test
    fun aPlainLinkIsShownWithCopyAndASecretOneOnlyAsAMessage() {
        val plain = detailsLinks(task("https://example.com/file.zip", referer = "https://example.com/page"))
        assertEquals(listOf("https://example.com/file.zip", "https://example.com/page"), plain.map { it.link })
        assertTrue(plain.all { it.note.isEmpty() })
        val secret = detailsLinks(task("https://example.com/file.zip?token=abc"))
        assertEquals(1, secret.size)
        assertEquals("", secret[0].link)
        assertEquals(LocalizationManager.Strings.detailsLinkSensitive, secret[0].note)
    }

    @Test
    fun aHiddenTaskNeverShowsItsSourceLink() {
        val links = detailsLinks(task("https://example.com/file.zip", hidden = true))
        assertTrue(links.none { it.link.contains("file.zip") })
    }
}
