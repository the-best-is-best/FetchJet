package com.michelle.raouf.fetch_jet.feature.media_player

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerSubtitlesTest {
    private val dir: File = Files.createTempDirectory("fjsubs").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
        PlayerSubtitles.resetForTest(null)
    }

    @Test
    fun srtCues() {
        val text = "﻿1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>Hello</i>\r\nworld\r\n\r\n2\r\n00:00:04,000 --> 00:00:05,250\r\nبص هنا &amp; هناك\r\n"
        val cues = PlayerSubtitles.parseCues(text, "srt")
        assertEquals(listOf(SubtitleCue(1500, 3000, "Hello\nworld"), SubtitleCue(4000, 5250, "بص هنا & هناك")), cues)
        assertEquals("", PlayerSubtitles.cueAt(cues, 1499))
        assertEquals("Hello\nworld", PlayerSubtitles.cueAt(cues, 2000))
        assertEquals("", PlayerSubtitles.cueAt(cues, 3000))
        assertEquals("بص هنا & هناك", PlayerSubtitles.cueAt(cues, 5000))
    }

    @Test
    fun vttCues() {
        val text = "WEBVTT\n\nNOTE x\n\n00:01.000 --> 00:02.000 align:start\nOne\n\n01:00:00.000 --> 01:00:01.500\nTwo\n"
        val cues = PlayerSubtitles.parseCues(text, "vtt")
        assertEquals(listOf(SubtitleCue(1000, 2000, "One"), SubtitleCue(3_600_000, 3_601_500, "Two")), cues)
    }

    @Test
    fun assCues() {
        val text = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:02.10,0:00:04.00,Default,,0,0,0,,{\\an8}Top, line\\Nsecond\n"
        assertEquals(listOf(SubtitleCue(2100, 4000, "Top, line\nsecond")), PlayerSubtitles.parseCues(text, "ass"))
    }

    @Test
    fun sideFilesAndChoice() {
        File(dir, "movie.mp4").writeText("v")
        File(dir, "movie.en.srt").writeText("x")
        File(dir, "movie.ar.vtt").writeText("x")
        File(dir, "movie.srt").writeText("x")
        File(dir, "other.srt").writeText("x")
        val files = PlayerSubtitles.sideFiles(File(dir, "movie.mp4").path, "ar")
        assertEquals(listOf("ar", "en", "SRT"), files.map { it.label })
        PlayerSubtitles.resetForTest(File(dir, "subs.json").path)
        assertEquals(SubtitleChoice.File(files[0].path), PlayerSubtitles.initialChoice("t1", files, "ar"))
        assertEquals(SubtitleChoice.File(files[1].path), PlayerSubtitles.initialChoice("t1", files, "en"))
        assertEquals(SubtitleChoice.File(files[0].path), PlayerSubtitles.initialChoice("t1", files, "fr"))
        PlayerSubtitles.remember("t1", SubtitleChoice.Off)
        PlayerSubtitles.resetForTest(File(dir, "subs.json").path)
        assertEquals(SubtitleChoice.Off, PlayerSubtitles.initialChoice("t1", files, "ar"))
        PlayerSubtitles.remember("t1", SubtitleChoice.Track("2"))
        assertEquals(SubtitleChoice.Track("2"), PlayerSubtitles.savedChoice("t1"))
        assertNull(PlayerSubtitles.initialChoice("t2", emptyList(), "ar"))
    }
}
