package com.michelle.raouf.fetch_jet.feature.audio_compress

import com.michelle.raouf.fetch_jet.feature.media_extractor.AUDIO_CONVERT_FORMATS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioCompressTest {
    @Test
    fun compressOffersEveryFormatConvertSupports() {
        assertEquals(AUDIO_CONVERT_FORMATS.toSet(), AudioCompress.FORMATS.toSet())
    }

    @Test
    fun sourceFormatIsTheDefaultOnlyWhenItIsLossy() {
        assertEquals("ogg", AudioCompress.defaultFormat("a.ogg"))
        assertEquals("opus", AudioCompress.defaultFormat("a.opus"))
        assertEquals("m4a", AudioCompress.defaultFormat("a.flac"))
        assertEquals("m4a", AudioCompress.defaultFormat("a.wav"))
        assertEquals("m4a", AudioCompress.defaultFormat("a.weba"))
    }

    @Test
    fun lossyEstimateFollowsBitrateAndDuration() {
        val sixty = AudioCompress.estimateBytes(120_000L, AudioEncode(64, 0, 0))
        val onetwentyeight = AudioCompress.estimateBytes(120_000L, AudioEncode(128, 0, 0))
        assertTrue(onetwentyeight > sixty * 19 / 10)
        assertEquals(sixty, AudioCompress.estimateBytes(120_000L, AudioEncode(64, 0, 0), "ogg"))
    }

    @Test
    fun losslessEstimateUsesRateAndChannelsNotBitrate() {
        val wav = AudioCompress.estimateBytes(120_000L, AudioEncode(64, 0, 0), "wav")
        assertEquals(21_168_044L, wav)
        assertEquals(wav, AudioCompress.estimateBytes(120_000L, AudioEncode(160, 0, 0), "wav"))
        assertEquals(wav * 55 / 100, AudioCompress.estimateBytes(120_000L, AudioEncode(64, 0, 0), "flac"))
        assertEquals(
            10_584_044L,
            AudioCompress.estimateBytes(120_000L, AudioEncode(64, 0, 1), "wav")
        )
    }
}
