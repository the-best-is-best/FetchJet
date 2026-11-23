package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleFilesTest {
    private fun track(code: String, ext: String = "srt", auto: Boolean = false) =
        SubtitleTrack(code, code, ext, "https://example.test/$code", isAuto = auto, isSelected = true)

    private fun tempDir() = Files.createTempDirectory("fjsubs").toString().toPath()

    @Test
    fun asksOneLanguageAtATimeWithAPauseBetween() = runBlocking {
        val waits = mutableListOf<Long>()
        val asked = mutableListOf<String>()
        val result = fetchSubtitleFiles(
            tracks = listOf(track("en"), track("ar"), track("fr")),
            tempDir = tempDir(),
            fetch = { asked.add(it.langCode); SubtitleAnswer.Text("1\n00:00:00,000 --> 00:00:01,000\nhi\n") },
            convert = { _, _ -> true },
            sleep = { waits.add(it) }
        )
        assertEquals(listOf("en", "ar", "fr"), asked)
        assertEquals(listOf(SUBTITLE_PAUSE_MS, SUBTITLE_PAUSE_MS), waits)
        assertEquals(listOf("en", "ar", "fr"), result.files.map { it.first.langCode })
        assertTrue(result.refused.isEmpty() && result.failed.isEmpty())
    }

    @Test
    fun aRefusedLanguageIsAskedAgainAfterWaiting() = runBlocking {
        val waits = mutableListOf<Long>()
        var arTries = 0
        val result = fetchSubtitleFiles(
            tracks = listOf(track("ar")),
            tempDir = tempDir(),
            fetch = {
                arTries++
                if (arTries < 3) SubtitleAnswer.TooManyRequests else SubtitleAnswer.Text("x")
            },
            convert = { _, _ -> true },
            sleep = { waits.add(it) }
        )
        assertEquals(3, arTries)
        assertEquals(SUBTITLE_RETRY_WAITS_MS.take(2), waits)
        assertEquals(listOf("ar"), result.files.map { it.first.langCode })
    }

    @Test
    fun stillRefusedAfterEveryRetryIsReportedAndTheOthersAreKept() = runBlocking {
        val result = fetchSubtitleFiles(
            tracks = listOf(track("en"), track("de"), track("es")),
            tempDir = tempDir(),
            fetch = {
                when (it.langCode) {
                    "de" -> SubtitleAnswer.TooManyRequests
                    "es" -> throw IllegalStateException("network")
                    else -> SubtitleAnswer.Text("x")
                }
            },
            convert = { _, _ -> true },
            sleep = {}
        )
        assertEquals(listOf("en"), result.files.map { it.first.langCode })
        assertEquals(listOf("de"), result.refused.map { it.langCode })
        assertEquals(listOf("es"), result.failed.map { it.langCode })
    }

    @Test
    fun aVttTrackIsConvertedToSrt() = runBlocking {
        val dir = tempDir()
        val result = fetchSubtitleFiles(
            tracks = listOf(track("en", ext = "vtt")),
            tempDir = dir,
            fetch = { SubtitleAnswer.Text("WEBVTT\n") },
            convert = { _, to -> to.toFile().writeText("1\n"); true },
            sleep = {}
        )
        assertEquals("en.srt", result.files.single().second.name)
    }

    @Test
    fun aVttTrackStaysVttWhenVttIsWanted() = runBlocking {
        var converted = false
        val result = fetchSubtitleFiles(
            tracks = listOf(track("en", ext = "vtt")),
            tempDir = tempDir(),
            fetch = { SubtitleAnswer.Text("WEBVTT") },
            convert = { _, _ -> converted = true; true },
            format = "vtt",
            sleep = {}
        )
        assertEquals("en.vtt", result.files.single().second.name)
        assertTrue(!converted)
    }

    @Test
    fun anSrtTrackIsConvertedWhenVttIsWanted() = runBlocking {
        val result = fetchSubtitleFiles(
            tracks = listOf(track("en", ext = "srt")),
            tempDir = tempDir(),
            fetch = { SubtitleAnswer.Text("1") },
            convert = { _, to -> to.toFile().writeText("WEBVTT"); true },
            format = "vtt",
            sleep = {}
        )
        assertEquals("en.vtt", result.files.single().second.name)
        assertEquals("clip.en.vtt", subtitleSideFileName("clip.mp4", track("en"), "vtt"))
    }

    @Test
    fun sideFileNamesKeepAutoCaptionsApart() {
        assertEquals("clip.ar.srt", subtitleSideFileName("clip.mp4", track("ar")))
        assertEquals("clip.en.auto.srt", subtitleSideFileName("clip.mp4", track("en", auto = true)))
    }

    @Test
    fun newTracksAreNumberedAfterTheOnesAlreadyInTheFile() {
        val args = subtitleArgs("v.mp4", listOf(track("fr") to "fr.srt"), existing = 2, output = "out.mp4")
        val joined = args.joinToString(" ")
        assertTrue("-c:s:2 mov_text" in joined, joined)
        assertTrue("-metadata:s:s:2 language=fre" in joined, joined)
        assertTrue("-map 0 -map 1" in joined, joined)
        assertTrue("-c:s:0" !in joined, joined)
    }
}
