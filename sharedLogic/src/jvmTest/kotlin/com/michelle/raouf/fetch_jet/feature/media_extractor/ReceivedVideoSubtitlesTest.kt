package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import com.michelle.raouf.fetch_jet.feature.device_sync.SOURCE_ENGINE_MEDIA
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SubtitleTrackWire
import com.michelle.raouf.fetch_jet.feature.device_sync.syncJson
import com.michelle.raouf.fetch_jet.feature.device_sync.wantedSubtitleLanguages
import com.michelle.raouf.fetch_jet.domain.model.canSendToDevice
import com.michelle.raouf.fetch_jet.feature.queue_manager.TaskCache
import com.michelle.raouf.fetch_jet.feature.queue_manager.toPersisted
import com.michelle.raouf.fetch_jet.feature.queue_manager.toTask
import kotlinx.serialization.Serializable
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceivedVideoSubtitlesTest {
    @Serializable
    private data class OldPayload(val url: String, val fileName: String = "", val engineType: String = "HTTP_DIRECT")

    private fun received(dir: String, from: Boolean = true, original: String? = "https://www.youtube.com/watch?v=abc") = DownloadTask(
        id = "t1",
        url = "https://rr1.googlevideo.com/videoplayback?x=1",
        fileName = "clip.mp4",
        destinationPath = dir,
        engineType = EngineType.HTTP_DIRECT,
        status = DownloadStatus.COMPLETED,
        originalUrl = original,
        subtitlesFromPage = from,
        subtitleTracks = listOf(SubtitleTrack("en", "English", "vtt", "", isSelected = true))
    )

    @Test
    fun theNewFieldsSurviveTheWire() {
        val payload = SendPayload(
            url = "u", fileName = "clip.mp4", originalUrl = "https://www.youtube.com/watch?v=abc",
            sourceEngine = SOURCE_ENGINE_MEDIA,
            subtitleTracks = listOf(SubtitleTrackWire("en", "English", "vtt"), SubtitleTrackWire("fr", "French", "srt", true)),
            embedSubtitles = true, subtitleFormat = "vtt", sideFiles = listOf("clip.en.vtt")
        )
        val back = syncJson.decodeFromString(SendPayload.serializer(), syncJson.encodeToString(SendPayload.serializer(), payload))
        assertEquals(payload, back)
    }

    @Test
    fun aPayloadFromAnOlderSenderDecodesWithDefaults() {
        val old = """{"url":"https://a/b.zip","fileName":"b.zip","engineType":"HTTP_DIRECT","totalBytes":5}"""
        val back = syncJson.decodeFromString(SendPayload.serializer(), old)
        assertEquals("", back.sourceEngine)
        assertTrue(back.subtitleTracks.isEmpty() && back.sideFiles.isEmpty())
        assertFalse(back.embedSubtitles)
        assertEquals("", back.subtitleFormat)
    }

    @Test
    fun anOlderReceiverIgnoresTheNewFields() {
        val payload = SendPayload(
            url = "u", fileName = "clip.mp4", sourceEngine = SOURCE_ENGINE_MEDIA,
            subtitleTracks = listOf(SubtitleTrackWire("en")), sideFiles = listOf("clip.en.srt")
        )
        val text = syncJson.encodeToString(SendPayload.serializer(), payload)
        val old = syncJson.decodeFromString(OldPayload.serializer(), text)
        assertEquals("clip.mp4", old.fileName)
    }

    @Test
    fun aReceivedVideoOffersSubtitlesOnlyWhenItWasAPageVideo() {
        val dir = Files.createTempDirectory("fjrecv").toString()
        assertTrue(received(dir).canAddSubtitles())
        assertFalse(received(dir, from = false).canAddSubtitles())
        assertFalse(received(dir, original = null).canAddSubtitles())
        assertFalse(received(dir).copy(status = DownloadStatus.PAUSED).canAddSubtitles())
        assertFalse(received(dir).copy(fileName = "clip.bin").canAddSubtitles())
    }

    @Test
    fun aReceivedVideoKnowsTheLanguagesInsideAndBesideIt() {
        val dir = Files.createTempDirectory("fjrecv").toString()
        Files.writeString(java.nio.file.Path.of(dir, "clip.ar.srt"), "x")
        Files.writeString(java.nio.file.Path.of(dir, "other.fr.srt"), "x")
        val task = received(dir)
        assertEquals(setOf("en", "ar"), task.subtitleKeysPresent())
        assertEquals(listOf("clip.ar.srt"), task.subtitleSideFiles().map { it.name })
    }

    @Test
    fun theFlagIsSavedAndOldFilesLoadWithoutIt() {
        val dir = Files.createTempDirectory("fjrecv").toString()
        val saved = TaskCache.decode(TaskCache.encode(listOf(received(dir)))).single().toTask("/d")
        assertTrue(saved.subtitlesFromPage)
        assertEquals("https://www.youtube.com/watch?v=abc", saved.originalUrl)
        val old = TaskCache.decode("""[{"id":"t9","url":"https://a/b.mp4","fileName":"b.mp4","engineType":"HTTP_DIRECT"}]""").single().toTask("/d")
        assertFalse(old.subtitlesFromPage)
        assertEquals(false, received(dir, from = false).toPersisted().subtitlesFromPage)
    }

    @Test
    fun anUnfinishedDownloadWithAnUnreadableLinkIsNotOfferedButAFinishedOneIs() {
        val locked = received("/d").copy(url = "fjenc1:abcdef", status = DownloadStatus.PAUSED, engineType = EngineType.MEDIA_STREAM)
        assertFalse(locked.canSendToDevice())
        assertTrue(locked.copy(status = DownloadStatus.COMPLETED).canSendToDevice())
    }

    @Test
    fun theWantedLanguagesOfAnUnfinishedVideoAreTheTrackCodes() {
        val payload = SendPayload(
            url = "u", engineType = "MEDIA_STREAM",
            subtitleTracks = listOf(SubtitleTrackWire("en"), SubtitleTrackWire("fr", isAuto = true), SubtitleTrackWire("en"), SubtitleTrackWire(""))
        )
        assertEquals("en,fr", payload.wantedSubtitleLanguages())
        assertEquals("", SendPayload(url = "u").wantedSubtitleLanguages())
    }
}
