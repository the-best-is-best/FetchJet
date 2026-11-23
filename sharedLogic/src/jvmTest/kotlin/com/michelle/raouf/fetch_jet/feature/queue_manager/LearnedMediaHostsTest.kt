package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.estimateStreamBytes
import com.michelle.raouf.fetch_jet.domain.model.sizeIsEstimate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearnedMediaHostsTest {
    private lateinit var file: File

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("learned", ".txt").also { it.delete() }
        LearnedMediaHosts.resetForTest(file.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        LearnedMediaHosts.resetForTest(null)
        file.delete()
    }

    private val unknown = "https://www.videosite-example.com/view_video.php?viewkey=abc"

    @Test
    fun anUnknownHostIsDirectUntilLearned() {
        assertEquals(EngineType.HTTP_DIRECT, DetectLinkTypeUseCase()(unknown))
        assertTrue(LearnedMediaHosts.learn(unknown))
        assertEquals(EngineType.MEDIA_STREAM, DetectLinkTypeUseCase()(unknown))
        assertEquals(EngineType.MEDIA_STREAM, DetectLinkTypeUseCase()("https://m.videosite-example.com/watch/2"))
        assertEquals(EngineType.HTTP_DIRECT, DetectLinkTypeUseCase()("https://other-example.com/a"))
    }

    @Test
    fun aLearnedHostStillOffersTheEngineChoice() {
        LearnedMediaHosts.learn(unknown)
        val s = com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState(
            isAddDialogVisible = true,
            inputUrl = unknown,
            detectedEngine = DetectLinkTypeUseCase()(unknown),
            linkKind = com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKind.VIDEO_PAGE,
            linkProbeUrl = unknown
        )
        assertEquals(EngineType.MEDIA_STREAM, s.detectedEngine)
        assertTrue(s.engineChoiceShown)
    }

    @Test
    fun learnedHostsSurviveARestart() {
        LearnedMediaHosts.learn(unknown)
        LearnedMediaHosts.resetForTest(file.absolutePath)
        assertTrue(LearnedMediaHosts.knows("videosite-example.com"))
        LearnedMediaHosts.forget("videosite-example.com")
        LearnedMediaHosts.resetForTest(file.absolutePath)
        assertFalse(LearnedMediaHosts.knows("videosite-example.com"))
    }

    @Test
    fun fileLinksAndAddressesAreNeverLearned() {
        for (u in listOf("https://files.example.com/a.mp4", "https://files.example.com/a.zip?x=1", "http://192.168.1.5/page", "http://localhost/x", "magnet:?xt=urn:btih:1", "https://x.example.com/list.m3u8")) {
            assertFalse(LearnedMediaHosts.learn(u), u)
        }
    }

    @Test
    fun hlsSizeIsAnEstimateFromBitrateAndDuration() {
        assertEquals(239_351_000L, estimateStreamBytes(2_072_311.0, 924) / 1000 * 1000)
        assertEquals(-1L, estimateStreamBytes(0.0, 924))
        assertEquals(-1L, estimateStreamBytes(1000.0, 0))
        assertTrue(MediaQualityEntry(0, "720p", "https://h/x/index.m3u8?a=1", "mp4", 1L).sizeIsEstimate)
        assertTrue(MediaQualityEntry(0, "720p", "https://h/x", "mp4", 1L, formatId = "hls-2072").sizeIsEstimate)
        assertFalse(MediaQualityEntry(0, "720p", "https://h/x.mp4", "mp4", 1L, formatId = "137").sizeIsEstimate)
    }
}
