package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.DetectLinkTypeUseCase
import com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineChoiceTest {
    private fun state(url: String, kind: LinkKind = LinkKind.UNKNOWN, probed: Boolean = true, engine: EngineType? = null) = DashboardUiState(
        isAddDialogVisible = true,
        inputUrl = url,
        detectedEngine = engine ?: DetectLinkTypeUseCase()(url),
        linkKind = kind,
        linkProbeUrl = if (probed) url else ""
    )

    @Test
    fun aMagnetOrATorrentFileIsTorrentWithNoChoice() {
        for (url in listOf("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", "https://example.com/a.torrent")) {
            val s = state(url)
            assertEquals(EngineType.BITTORRENT, s.detectedEngine)
            assertFalse(s.engineChoiceShown)
            assertFalse(state(url, LinkKind.UNSURE).engineChoiceShown)
        }
    }

    @Test
    fun aKnownMediaHostIsMediaWithNoChoice() {
        val s = state("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        assertEquals(EngineType.MEDIA_STREAM, s.detectedEngine)
        assertFalse(s.engineChoiceShown)
        assertFalse(s.canGrabPage)
    }

    @Test
    fun aFileLinkIsDirectWithNoChoice() {
        val byName = state("https://example.com/files/a.zip")
        assertEquals(EngineType.HTTP_DIRECT, byName.detectedEngine)
        assertFalse(byName.engineChoiceShown)
        assertFalse(byName.canGrabPage)
        val byProbe = state("https://example.com/site/bundle", LinkKind.FILE)
        assertFalse(byProbe.engineChoiceShown)
        assertFalse(byProbe.canGrabPage)
    }

    @Test
    fun aPageWithAVideoShowsTheCardAndTheChoice() {
        val s = state("https://example.com/site/video.html", LinkKind.VIDEO_PAGE)
        assertTrue(s.canGrabPage)
        assertTrue(s.engineChoiceShown)
        assertEquals(EngineType.HTTP_DIRECT, s.detectedEngine)
    }

    @Test
    fun choosingMediaOnAVideoPageKeepsTheChoiceAndHidesTheCard() {
        val s = state("https://example.com/site/video.html", LinkKind.VIDEO_PAGE, engine = EngineType.MEDIA_STREAM)
        assertFalse(s.canGrabPage)
        assertTrue(s.engineChoiceShown)
    }

    @Test
    fun aPlainPageShowsTheCardAndTheEngineChoice() {
        val s = state("https://example.com/site/start.html", LinkKind.PAGE)
        assertTrue(s.canGrabPage)
        assertTrue(s.engineChoiceShown)
    }

    @Test
    fun aFailedCheckShowsTheCardAndTheChoice() {
        val s = state("https://example.com/site/start", LinkKind.UNSURE)
        assertTrue(s.canGrabPage)
        assertTrue(s.engineChoiceShown)
    }

    @Test
    fun whileCheckingThereIsNoCardAndNoChoice() {
        for (probed in listOf(false, true)) {
            val s = state("https://example.com/site/start", LinkKind.UNKNOWN, probed = probed)
            assertTrue(s.checkingLink)
            assertFalse(s.canGrabPage)
            assertFalse(s.engineChoiceShown)
        }
        val stale = state("https://example.com/site/start", LinkKind.VIDEO_PAGE, probed = false)
        assertTrue(stale.checkingLink)
        assertFalse(stale.engineChoiceShown)
    }

    @Test
    fun aLinkListOrAScanInProgressShowsNoChoice() {
        val entry = LinkListEntry(url = "https://example.com/a.zip", engine = EngineType.HTTP_DIRECT)
        val list = state("https://example.com/a.zip\nhttps://example.com/b.zip", LinkKind.UNSURE).copy(linkList = listOf(entry, entry))
        assertFalse(list.engineChoiceShown)
        val grabbed = state("https://example.com/site/start.html", LinkKind.VIDEO_PAGE).copy(
            linkList = listOf(entry.copy(folder = "Archives"))
        )
        assertFalse(grabbed.engineChoiceShown)
        val running = state("https://example.com/site/start.html", LinkKind.VIDEO_PAGE).copy(siteGrab = SiteGrabUi(running = true))
        assertFalse(running.engineChoiceShown)
    }
}
