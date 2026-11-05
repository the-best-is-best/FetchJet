package com.michelle.raouf.fetch_jet.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaQualityAudioTest {
    private fun q(label: String, format: String) =
        MediaQualityEntry(index = 0, qualityLabel = label, directUrl = "", format = format)

    @Test
    fun soundCloudTrackIsAudio() {
        assertTrue(listOf(q("Audio Only (M4A)", "m4a"), q("Audio Only (MP3)", "mp3")).isAllAudio())
    }

    @Test
    fun youTubeWithAnAudioOptionIsStillVideo() {
        assertFalse(
            listOf(
                q("1080p", "mp4"),
                q("720p", "mp4"),
                q("Audio Only (M4A)", "m4a")
            ).isAllAudio()
        )
    }

    @Test
    fun noQualitiesIsNotAudio() {
        assertFalse(emptyList<MediaQualityEntry>().isAllAudio())
    }
}
