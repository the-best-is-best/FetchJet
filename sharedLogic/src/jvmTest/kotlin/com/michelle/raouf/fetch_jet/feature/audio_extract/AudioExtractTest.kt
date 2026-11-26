package com.michelle.raouf.fetch_jet.feature.audio_extract

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.getExistingFfmpegBinary
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenu
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuEntry
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuOptions
import com.michelle.raouf.fetch_jet.feature.row_menu.section
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioExtractTest {
    private fun task(
        name: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        from: String = "",
        hidden: Boolean = false,
        engine: EngineType = EngineType.HTTP_DIRECT
    ) = DownloadTask(
        id = "t", url = "http://x/$name", fileName = name, destinationPath = "/d",
        engineType = engine, status = status, hidden = hidden, cutFromTaskId = from
    )

    @Test
    fun extensionFollowsTheAudioCodec() {
        val expected = mapOf(
            "aac" to "m4a", "alac" to "m4a", "mp3" to "mp3", "opus" to "opus", "vorbis" to "ogg",
            "flac" to "flac", "ac3" to "ac3", "eac3" to "eac3", "pcm" to "wav",
            "pcm_be" to "mka", "dts" to "mka", "truehd" to "mka", "mp2" to "mka", "anything" to "mka"
        )
        expected.forEach { (codec, ext) -> assertEquals(ext, AudioExtract.extensionFor(codec), codec) }
        assertEquals("ipod", AudioExtract.targetFor("aac").muxer)
        assertEquals("matroska", AudioExtract.targetFor("dts").muxer)
    }

    @Test
    fun matroskaCodecIdsMapToCodecs() {
        val expected = mapOf(
            "A_AAC" to "aac", "A_AAC/MPEG4/LC" to "aac", "A_MPEG/L3" to "mp3", "A_MPEG/L2" to "mp2",
            "A_OPUS" to "opus", "A_VORBIS" to "vorbis", "A_FLAC" to "flac", "A_ALAC" to "alac",
            "A_AC3" to "ac3", "A_EAC3" to "eac3", "A_PCM/INT/LIT" to "pcm", "A_PCM/FLOAT/IEEE" to "pcm",
            "A_PCM/INT/BIG" to "pcm_be", "A_DTS" to "dts", "A_TRUEHD" to "truehd"
        )
        expected.forEach { (id, codec) -> assertEquals(codec, AudioExtract.codecOfMatroskaId(id), id) }
    }

    @Test
    fun codecIdIsReadFromTheMatroskaBytes() {
        val bytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0x86.toByte(), 0x81.toByte(), 0x01) +
                byteArrayOf(0x86.toByte(), 0x86.toByte()) + "A_OPUS".encodeToByteArray() + byteArrayOf(0x56, 0xAA.toByte())
        assertEquals("A_OPUS", AudioExtract.matroskaAudioCodecId(bytes))
    }

    @Test
    fun onlyFinishedPlainVideosCanBeExtracted() {
        assertTrue(AudioExtract.canExtract(task("a.mp4")))
        assertTrue(AudioExtract.canExtract(task("a.webm")))
        assertFalse(AudioExtract.canExtract(task("a.mp3")))
        assertFalse(AudioExtract.canExtract(task("a.zip")))
        assertFalse(AudioExtract.canExtract(task("a.mp4", DownloadStatus.DOWNLOADING)))
        assertFalse(AudioExtract.canExtract(task("a.mp4", from = "src")))
        assertFalse(AudioExtract.canExtract(task("a.mp4", hidden = true)))
        assertFalse(AudioExtract.canExtract(task("a.mp4", engine = EngineType.BITTORRENT)))
    }

    @Test
    fun nameKeepsTheVideoName() {
        assertEquals("My clip (audio).m4a", AudioExtract.outputName("My clip.mp4", "m4a"))
        assertEquals("a.b (audio).opus", AudioExtract.outputName("a.b.webm", "opus"))
    }

    @Test
    fun rowMenuOffersExtractAudioForVideosAndGroupsBySection() {
        val video = RowMenu.entries(task("a.mp4"), RowMenuOptions(selectEntry = true))
        assertTrue(RowMenuEntry.EXTRACT_AUDIO in video)
        assertEquals(video.indexOf(RowMenuEntry.CUT) + 1, video.indexOf(RowMenuEntry.EXTRACT_AUDIO))
        assertFalse(RowMenuEntry.EXTRACT_AUDIO in RowMenu.entries(task("a.mp4"), RowMenuOptions(canExtractAudio = false)))
        assertFalse(RowMenuEntry.EXTRACT_AUDIO in RowMenu.entries(task("a.mp4", from = "src")))
        assertFalse(RowMenuEntry.EXTRACT_AUDIO in RowMenu.entries(task("a.mp3")))
        val ordinals = video.map { it.section.ordinal }
        assertEquals(ordinals.sorted(), ordinals)
    }

    private val ffmpeg: String? get() = getExistingFfmpegBinary()?.absolutePath

    private fun run(vararg args: String): String {
        val p = ProcessBuilder(listOf(ffmpeg!!) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    private fun audioMd5(file: File, packets: Boolean = true): String =
        run(*(listOf("-hide_banner", "-v", "error", "-i", file.path, "-map", "0:a") +
                (if (packets) listOf("-c", "copy") else emptyList()) + listOf("-f", "md5", "-")).toTypedArray())
            .lineSequence().first { it.startsWith("MD5=") }

    private fun workDir(): File =
        File(System.getProperty("java.io.tmpdir"), "fetchjet-audioextract-test-${System.nanoTime()}").apply { mkdirs() }

    private fun makeVideo(dir: File, name: String, video: List<String>, audio: List<String>?): File {
        val out = File(dir, name)
        val args = mutableListOf("-hide_banner", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=size=160x120:rate=10")
        if (audio != null) args += listOf("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000")
        args += listOf("-t", "3") + video + (audio ?: listOf("-an")) + out.path
        run(*args.toTypedArray())
        assertTrue(out.length() > 0L, "source $name was not made")
        return out
    }

    private fun checkLossless(source: File, expectedExt: String, expectedCodec: String, packets: Boolean = true) = runBlocking {
        val made = AudioExtract.extractDetailed(source.path, source.parent, source.name)
        assertNotNull(made)
        val out = File(made.path)
        assertEquals(expectedCodec, made.codec)
        assertEquals(expectedExt, out.extension)
        assertEquals(source.nameWithoutExtension + " (audio)", out.nameWithoutExtension)
        assertEquals(audioMd5(source, packets), audioMd5(out, packets))
        assertTrue(made.durationMs in 2_500L..3_500L, "duration ${made.durationMs}")
        assertTrue(run("-hide_banner", "-i", out.path).lines().none { it.contains("Video:") })
        val second = AudioExtract.extractDetailed(source.path, source.parent, source.name)
        assertNotNull(second)
        assertEquals(source.nameWithoutExtension + " (audio) (1)", File(second.path).nameWithoutExtension)
        assertFalse(source.parentFile.listFiles()!!.any { it.name.contains(".part.") })
    }

    @Test
    fun aacInMp4IsCopiedBitForBitToM4a() {
        if (ffmpeg == null) return
        val dir = workDir()
        try {
            checkLossless(makeVideo(dir, "clip.mp4", listOf("-c:v", "mpeg4"), listOf("-c:a", "aac")), "m4a", "aac")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun opusInWebmIsCopiedBitForBitToOpus() {
        if (ffmpeg == null) return
        val dir = workDir()
        try {
            checkLossless(makeVideo(dir, "clip.webm", listOf("-c:v", "libvpx"), listOf("-c:a", "libopus")), "opus", "opus")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun adtsAacInTsBecomesAPlayableM4a() {
        if (ffmpeg == null) return
        val dir = workDir()
        try {
            checkLossless(makeVideo(dir, "clip.ts", listOf("-c:v", "mpeg4"), listOf("-c:a", "aac")), "m4a", "aac", packets = false)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aVideoWithoutSoundSaysSo() {
        if (ffmpeg == null) return
        val dir = workDir()
        try {
            val source = makeVideo(dir, "silent.mp4", listOf("-c:v", "mpeg4"), null)
            assertFailsWith<AudioExtractNoAudioException> {
                runBlocking { AudioExtract.extractDetailed(source.path, source.parent, source.name) }
            }
            assertEquals(listOf("silent.mp4"), dir.listFiles()!!.map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }
}
