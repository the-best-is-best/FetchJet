package com.michelle.raouf.fetch_jet.feature.video_merge

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoMergeTest {
    private val header = """
        #format: frame checksums
        #version: 2
        #hash: MD5
        #tb 0: 1/90000
        #media_type 0: video
        #codec_id 0: h264
        #dimensions 0: 1280x720
        #sar 0: 1/1
        #tb 1: 1/44100
        #media_type 1: audio
        #codec_id 1: aac
        #sample_rate 1: 44100
        #channel_layout_name 1: stereo
        #stream#, dts,        pts, duration,     size, hash
        0,          0,          0,     3000,    12345, abc
    """.trimIndent()

    private fun task(name: String, status: DownloadStatus = DownloadStatus.COMPLETED, engine: EngineType = EngineType.HTTP_DIRECT) =
        DownloadTask(id = name, url = "u", fileName = name, destinationPath = "/d", engineType = engine, status = status)

    @Test
    fun readsTheStreamsFromTheFramemd5Header() {
        val info = VideoMerge.infoOf("MP4", header)!!
        assertEquals(MergeStreamInfo("mp4", "h264", "1280x720", "aac", "44100", "stereo"), info)
    }

    @Test
    fun aFileWithNoVideoIsNotAVideo() {
        assertNull(VideoMerge.infoOf("mp4", "#media_type 0: audio\n#codec_id 0: aac\n"))
    }

    @Test
    fun theFirstDifferentVideoIsNamed() {
        val a = VideoMerge.infoOf("mp4", header)
        val b = a!!.copy(dimensions = "1920x1080")
        assertNull(VideoMerge.firstMismatch(listOf(a, a, a)))
        assertEquals(2, VideoMerge.firstMismatch(listOf(a, a, b)))
        assertEquals(1, VideoMerge.firstMismatch(listOf(a, null)))
        assertEquals(1, VideoMerge.firstMismatch(listOf(a, a.copy(extension = "mkv"))))
        assertEquals(0, VideoMerge.firstMismatch(listOf(null, a)))
    }

    @Test
    fun theListFileQuotesEveryPath() {
        assertEquals("file '/a/one.mp4'\nfile '/a/it'\\''s.mp4'\n", VideoMerge.concatList(listOf("/a/one.mp4", "/a/it's.mp4")))
    }

    @Test
    fun names() {
        assertEquals("Trip (merged).mp4", VideoMerge.mergedName("Trip.mp4"))
        assertEquals("a_b (merged).mkv", VideoMerge.mergedName("a:b.mkv"))
        assertEquals("Holiday.mp4", VideoMerge.withExtension("Holiday", "mp4"))
        assertEquals("Holiday.MP4", VideoMerge.withExtension(" Holiday.MP4 ", "mp4"))
        assertEquals("", VideoMerge.withExtension("  ", "mp4"))
    }

    @Test
    fun onlyFinishedVideosCanBeMerged() {
        assertTrue(VideoMerge.canMergeAll(listOf(task("a.mp4"), task("b.mp4"))))
        assertFalse(VideoMerge.canMergeAll(listOf(task("a.mp4"))))
        assertFalse(VideoMerge.canMergeAll(listOf(task("a.mp4"), task("b.mp3"))))
        assertFalse(VideoMerge.canMergeAll(listOf(task("a.mp4"), task("b.mp4", DownloadStatus.PAUSED))))
        assertFalse(VideoMerge.canMergeAll(listOf(task("a.mp4"), task("b.mp4", engine = EngineType.BITTORRENT))))
    }
}
