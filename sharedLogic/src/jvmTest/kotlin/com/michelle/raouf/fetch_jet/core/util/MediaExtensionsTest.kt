package com.michelle.raouf.fetch_jet.core.util

import com.michelle.raouf.fetch_jet.core.settings.FileCategory
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaExtensionsTest {
    private fun hidden(name: String) = DownloadTask(
        id = name,
        url = "http://x/$name",
        fileName = name,
        destinationPath = "/origin",
        engineType = EngineType.HTTP_DIRECT,
        status = DownloadStatus.COMPLETED,
        hidden = true
    )

    private val all = MediaExtensions.VIDEO + MediaExtensions.AUDIO

    @Test
    fun videoAndAudioDoNotOverlap() {
        assertTrue((MediaExtensions.VIDEO intersect MediaExtensions.AUDIO).isEmpty())
        assertTrue(MediaExtensions.VIDEO.containsAll(MediaExtensions.PLAYABLE_VIDEO))
        assertTrue(MediaExtensions.AUDIO.containsAll(MediaExtensions.PLAYABLE_AUDIO))
    }

    @Test
    fun everyHiddenMediaFileIsPlayedInTheAppOrOpenedBySystemApp() {
        for (ext in all) {
            val name = "a.$ext"
            assertTrue(HiddenMedia.isMedia(name), ext)
            val playable = ext in MediaExtensions.PLAYABLE_VIDEO || ext in MediaExtensions.PLAYABLE_AUDIO
            assertEquals(playable, MediaPlayback.canPlay(hidden(name), allowHidden = true), ext)
        }
        assertTrue(MediaPlayback.canPlay(hidden("a.MKV"), allowHidden = true))
        assertFalse(MediaPlayback.canPlay(hidden("a.wma"), allowHidden = true))
        assertFalse(HiddenMedia.isMedia("a.zip"))
    }

    @Test
    fun audioMeansTheSameEverywhere() {
        for (ext in all) {
            val name = "Song.${ext.uppercase()}"
            val audio = ext in MediaExtensions.AUDIO
            assertEquals(audio, AudioCompress.isAudioFile(name), ext)
            assertEquals(audio, VideoCut.isAudioName(name), ext)
            assertEquals(if (audio) "Music" else "Video", FileCategory.folderFor(name), ext)
        }
    }
}
