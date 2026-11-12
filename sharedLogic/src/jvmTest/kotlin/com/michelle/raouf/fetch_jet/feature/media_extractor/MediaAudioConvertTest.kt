package com.michelle.raouf.fetch_jet.feature.media_extractor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

class MediaAudioConvertTest {
    private fun container(codecId: String): String {
        val file = File.createTempFile("fj-audio", ".mka")
        file.deleteOnExit()
        val head = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x86.toByte()) +
            ByteArray(300) { (it * 7).toByte() } + codecId.encodeToByteArray() + ByteArray(2000) { 0x55 }
        file.writeBytes(head)
        return file.absolutePath
    }

    @Test
    fun codecIsReadFromTheContainerHead() {
        assertEquals("aac", audioCodecOf(container("A_AAC").toPath()))
        assertEquals("mp3", audioCodecOf(container("A_MPEG/L3").toPath()))
        assertEquals("opus", audioCodecOf(container("A_OPUS").toPath()))
        assertEquals("flac", audioCodecOf(container("A_FLAC").toPath()))
        assertNull(audioCodecOf(container("V_VP9").toPath()))
        assertNull(audioCodecOf("no-such-file.mka".toPath()))
    }

    @Test
    fun aacInMatroskaIsNotCopiedIntoMp3() {
        assertFalse(canCopyAudio("mka", "mp3", "aac"))
        assertTrue(canCopyAudio("mka", "mp3", "mp3"))
        assertTrue(canCopyAudio("mka", "opus", "opus"))
        assertFalse(canCopyAudio("webm", "opus", "vorbis"))
        assertTrue(canCopyAudio("mka", "mp3", null))
        assertTrue(canCopyAudio("m4a", "m4a", "aac"))
    }

    @Test
    fun opusAndVorbisAreCopiedIntoOgg() {
        assertTrue(canCopyAudio("webm", "ogg", "opus"))
        assertTrue(canCopyAudio("mka", "ogg", "vorbis"))
        assertFalse(canCopyAudio("webm", "ogg", null))
        assertFalse(canCopyAudio("mka", "ogg", "aac"))
        assertFalse(canCopyAudio("m4a", "ogg", "opus"))
    }

    @Test
    fun realFilesWhenGiven() {
        val folder = System.getenv("FETCHJET_AUDIO_SAMPLES") ?: return
        assertEquals("aac", audioCodecOf("$folder/aac.mka".toPath()))
        assertEquals("mp3", audioCodecOf("$folder/mp3.mka".toPath()))
        assertEquals("opus", audioCodecOf("$folder/opus.mka".toPath()))
    }
}
