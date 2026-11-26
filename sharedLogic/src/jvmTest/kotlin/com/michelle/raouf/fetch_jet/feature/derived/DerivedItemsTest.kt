package com.michelle.raouf.fetch_jet.feature.derived

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.SubtitleShift
import com.michelle.raouf.fetch_jet.feature.media_extractor.canAddSubtitles
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DerivedItemsTest {
    private val source = DownloadTask(
        id = "src", url = "https://www.youtube.com/watch?v=abc", fileName = "Movie.mp4", destinationPath = "/d",
        engineType = EngineType.MEDIA_STREAM, status = DownloadStatus.COMPLETED, totalBytes = 1000, downloadedBytes = 1000,
        originalUrl = "https://www.youtube.com/watch?v=abc", requestHeaders = mapOf("Cookie" to "a=b"), formatId = "137+140",
        playlistId = "pl", playlistTitle = "List", embedSubtitles = true
    )

    private fun clip(id: String, from: String, start: Long = 1_000, end: Long = 5_000) =
        VideoCut.clipTask(id, source.copy(id = from), "/d/$id.mp4", start, end, 10)

    @Test
    fun countsClipsAndCopies() {
        val tasks = listOf(source, clip("c1", "src"), clip("c2", "other"))
        assertEquals(1, DerivedItems.count("src", tasks, emptyMap()))
        assertEquals(3, DerivedItems.count("src", tasks, mapOf("src" to listOf("/d/a.mp3", "/d/b.mp3"))))
        assertEquals(0, DerivedItems.count("c1", tasks, emptyMap()))
    }

    @Test
    fun removalTakesClipsTheirCopiesAndSourceCopies() {
        val tasks = listOf(source, clip("c1", "src"), clip("c2", "src"), clip("x", "other"))
        val copies = mapOf("src" to listOf("/d/s.mp3"), "c1" to listOf("/d/c1.mp3"), "other" to listOf("/d/o.mp3"))
        val plan = DerivedItems.removalPlan(listOf("src"), tasks, copies)
        assertEquals(listOf("c1", "c2"), plan.clipIds)
        assertEquals(setOf("src", "c1"), plan.copies.keys)
        assertEquals(4, plan.extraCount)
    }

    @Test
    fun removingAClipAloneLeavesItsSource() {
        val tasks = listOf(source, clip("c1", "src"), clip("c2", "src"))
        val plan = DerivedItems.removalPlan(listOf("c1"), tasks, mapOf("src" to listOf("/d/s.mp3")))
        assertTrue(plan.clipIds.isEmpty())
        assertTrue(plan.copies.isEmpty())
        assertEquals(0, plan.extraCount)
    }

    @Test
    fun removingSourceAndClipTogetherCountsTheClipOnce() {
        val tasks = listOf(source, clip("c1", "src"))
        val plan = DerivedItems.removalPlan(listOf("src", "c1"), tasks, emptyMap())
        assertEquals(0, plan.extraCount)
    }

    @Test
    fun promotedClipTakesTheSourceLink() {
        val made = clip("c1", "src", 2_000, 9_000)
        val promoted = DerivedItems.promotedClip(made, source)
        assertEquals("", promoted.cutFromTaskId)
        assertEquals("c1", promoted.id)
        assertEquals("c1.mp4", promoted.fileName)
        assertEquals(source.url, promoted.url)
        assertEquals(source.originalUrl, promoted.originalUrl)
        assertEquals(EngineType.MEDIA_STREAM, promoted.engineType)
        assertEquals(source.requestHeaders, promoted.requestHeaders)
        assertEquals("137+140", promoted.formatId)
        assertEquals("pl", promoted.playlistId)
        assertEquals("List", promoted.playlistTitle)
        assertTrue(promoted.embedSubtitles)
        assertEquals(2_000L, promoted.cutStartMs)
        assertEquals(9_000L, promoted.cutEndMs)
        assertEquals(10L, promoted.totalBytes)
        assertFalse(VideoCut.isClip(promoted))
        assertTrue(VideoCut.canCut(promoted))
        assertTrue(promoted.canAddSubtitles())
        assertTrue(DerivedItems.isPromotedClip(promoted))
        assertTrue(SubtitleShift.appliesTo(promoted))
        assertFalse(SubtitleShift.appliesTo(made))
        assertFalse(SubtitleShift.appliesTo(source))
    }

    @Test
    fun cuttingAPromotedClipAddsItsOffset() {
        val first = DerivedItems.promotedClip(clip("c1", "src", 10_000, 60_000), source)
        val second = DerivedItems.promotedClip(VideoCut.clipTask("c2", first, "/d/c2.mp4", 5_000, 8_000, 5), first)
        assertEquals(15_000L, second.cutStartMs)
        assertEquals(18_000L, second.cutEndMs)
    }

    @Test
    fun compressedSourceKeepsTheTask() {
        val changed = DerivedItems.compressedSource(source.copy(fileName = "song.flac", checksumVerified = true), "song (compressed).mp3", 300)
        assertEquals("src", changed.id)
        assertEquals("song (compressed).mp3", changed.fileName)
        assertEquals(300L, changed.totalBytes)
        assertEquals(300L, changed.downloadedBytes)
        assertEquals(source.url, changed.url)
        assertEquals(null, changed.checksumVerified)
    }

    @Test
    fun srtIsShiftedTrimmedAndRenumbered() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,000\r\nbefore\r\n\r\n2\r\n00:00:09,500 --> 00:00:11,000\r\nacross start\r\n\r\n" +
                "3\r\n00:00:15,000 --> 00:00:16,250\r\n<i>inside</i>\r\n\r\n4\r\n00:00:19,000 --> 00:00:22,000\r\nacross end\r\n\r\n" +
                "5\r\n00:00:30,000 --> 00:00:31,000\r\nafter\r\n"
        val out = SubtitleShift.shift(srt, "srt", 10_000, 10_000)
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\nacross start\n\n2\n00:00:05,000 --> 00:00:06,250\n<i>inside</i>\n\n" +
                    "3\n00:00:09,000 --> 00:00:10,000\nacross end\n",
            out
        )
    }

    @Test
    fun vttKeepsHeaderAndSettings() {
        val vtt = "WEBVTT\nKind: captions\n\n00:01:05.000 --> 00:01:07.500 align:start\nhello\n\n00:00:01.000 --> 00:00:02.000\ngone\n"
        val out = SubtitleShift.shift(vtt, "vtt", 60_000, 0)
        assertEquals("WEBVTT\nKind: captions\n\n00:00:05.000 --> 00:00:07.500 align:start\nhello\n", out)
    }

    @Test
    fun assDialogueIsShifted() {
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:12.50,0:00:14.00,Default,,0,0,0,,Hi, there\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,old"
        val out = SubtitleShift.shift(ass, "ass", 10_000, 0)
        assertEquals(
            "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                    "Dialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,Hi, there",
            out
        )
    }
}
