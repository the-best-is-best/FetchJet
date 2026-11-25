package com.michelle.raouf.fetch_jet.feature.video_cut

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.getExistingFfmpegBinary
import com.michelle.raouf.fetch_jet.feature.queue_manager.TaskCache
import com.michelle.raouf.fetch_jet.feature.queue_manager.toTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoCutTest {
    private fun task(name: String, status: DownloadStatus = DownloadStatus.COMPLETED, from: String = "") = DownloadTask(
        id = "t1", url = "https://x/y", fileName = name, destinationPath = "/d",
        engineType = EngineType.MEDIA_STREAM, status = status, cutFromTaskId = from
    )

    @Test
    fun clipNameIsSafeAndReadable() {
        assertEquals("My_ video (cut 0-12 - 0-45).mp4", VideoCut.clipName("My: video.mp4", 12_000, 45_000))
        assertEquals("a (cut 1-02-03.456 - 1-02-05).mkv", VideoCut.clipName("a.mkv", 3_723_456, 3_725_000))
        assertEquals("a (cut 0-00 - 0-01.500).mp4", VideoCut.clipName("a.webm", 0, 1_500, "mp4"))
        assertFalse(VideoCut.clipName("x?y*z.mov", 0, 1000).any { it in "\\/:*?\"<>|" })
    }

    @Test
    fun rangeIsClamped() {
        assertEquals(0L to 100L, VideoCut.clampRange(-50, 20, 10_000))
        assertEquals(9_900L to 10_000L, VideoCut.clampRange(12_000, 15_000, 10_000))
        assertEquals(2_000L to 2_100L, VideoCut.clampRange(2_000, 1_000, 10_000))
        assertEquals(1_000L to 10_000L, VideoCut.clampRange(1_000, 99_000, 10_000))
        assertTrue(VideoCut.isValidRange(0, 100, 10_000))
        assertFalse(VideoCut.isValidRange(0, 99, 10_000))
        assertFalse(VideoCut.isValidRange(0, 10_001, 10_000))
    }

    @Test
    fun timeTextRoundTrips() {
        assertEquals("01:02:03.456", VideoCut.formatTime(3_723_456))
        assertEquals(3_723_456L, VideoCut.parseTime("01:02:03.456"))
        assertEquals(3_723_000L, VideoCut.parseTime("01:02:03"))
        assertEquals(90_500L, VideoCut.parseTime("1:30,5"))
        assertEquals(12_000L, VideoCut.parseTime("12"))
        assertNull(VideoCut.parseTime("1:75"))
        assertNull(VideoCut.parseTime("a:b"))
        assertNull(VideoCut.parseTime(""))
    }

    @Test
    fun onlyFinishedVideosThatAreNotClipsCanBeCut() {
        assertTrue(VideoCut.canCut(task("a.mp4")))
        assertTrue(VideoCut.canCut(task("a.webm")))
        assertFalse(VideoCut.canCut(task("a.mp3")))
        assertFalse(VideoCut.canCut(task("a.mp4", DownloadStatus.PAUSED)))
        assertFalse(VideoCut.canCut(task("a.mp4").copy(hidden = true)))
        assertFalse(VideoCut.canCut(task("a.mp4", from = "src")))
        assertTrue(VideoCut.isClip(task("a.mp4", from = "src")))
        assertFalse(VideoCut.isClip(task("a.mp4")))
    }

    @Test
    fun clipMarkerSurvivesTheTaskCacheAndOldFilesStillLoad() {
        val clip = VideoCut.clipTask("c1", task("a.mp4"), "/d/a (cut 0-01 - 0-02).mp4", 1_000, 2_000, 1234)
        val back = TaskCache.decode(TaskCache.encode(listOf(clip))).single().toTask("/x")
        assertEquals("t1", back.cutFromTaskId)
        assertEquals(1_000L, back.cutStartMs)
        assertEquals(2_000L, back.cutEndMs)
        assertEquals(DownloadStatus.COMPLETED, back.status)
        val old = TaskCache.decode("""[{"id":"o","fileName":"v.mp4","status":"COMPLETED"}]""").single().toTask("/x")
        assertEquals("", old.cutFromTaskId)
        assertTrue(VideoCut.clipsOf("t1", listOf(back, old)).single().let { it.taskId == "c1" && it.startMs == 1_000L })
    }

    @Test
    fun packetTimesGiveTheRealEnd() {
        val text = listOf("#tb 0: 1/12800", "#stream#, dts, pts, duration, size, hash", "0, -1024, 0, 512, 9, a", "0, 125440, 125952, 512, 3, b", "0, 126464, 127488, 512, 3, c", "1, 0, 999999, 512, 3, d").joinToString("\n")
        assertEquals(0L to 10_000L, VideoCut.packetSpanOf(text))
        assertNull(VideoCut.packetSpanOf("0, 1, 2, 3, 4, x"))
    }

    @Test
    fun etaFollowsASteadyRate() {
        val eta = CutEta(startedAtMs = 0L)
        var last: VideoCutProgress? = null
        for (t in 400L..20_000L step 400L) last = eta.update(t / 40_000f, t)
        val left = last!!.etaMs
        assertTrue(abs(left - 20_000L) < 1_000L, "eta $left")
        assertEquals(-1L, CutEta(startedAtMs = 0L).update(0.001f, 300L).etaMs)
    }

    private val ffmpeg: String? get() = getExistingFfmpegBinary()?.absolutePath

    private fun run(vararg args: String): String {
        val p = ProcessBuilder(listOf(ffmpeg!!) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    private fun frames(file: File): Int =
        Regex("""frame=\s*(\d+)""").findAll(run("-hide_banner", "-i", file.path, "-map", "0:v:0", "-f", "null", "-"))
            .last().groupValues[1].toInt()

    private fun psnr(clip: File, source: File, sourceFrame: Int): Double {
        val out = run(
            "-hide_banner", "-i", clip.path, "-i", source.path, "-filter_complex",
            "[0:v]select=eq(n\\,0),setpts=PTS-STARTPTS[a];[1:v]select=eq(n\\,$sourceFrame),setpts=PTS-STARTPTS[b];[a][b]psnr",
            "-frames:v", "1", "-f", "null", "-"
        )
        val value = Regex("""average:([0-9.]+|inf)""").find(out)?.groupValues?.get(1) ?: return 0.0
        return if (value == "inf") 999.0 else value.toDouble()
    }

    private fun workDir(): File = File(System.getProperty("java.io.tmpdir"), "fetchjet-videocut-test-${System.nanoTime()}").apply { mkdirs() }

    private fun makeSource(dir: File, name: String, seconds: Int, size: String, extra: List<String>): File {
        val file = File(dir, name)
        run(
            *(listOf(
                "-hide_banner", "-y", "-f", "lavfi", "-i", "testsrc2=size=$size:rate=25:duration=$seconds",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=$seconds", "-shortest"
            ) + extra + file.path).toTypedArray()
        )
        return file
    }

    @Test
    fun cutIsFrameAccurateOnDesktopFfmpeg() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir, "src.mp4", 10, "640x360", listOf("-c:v", "libx264", "-g", "250", "-c:a", "aac"))
            assertEquals(250, frames(source))
            val duration = VideoCut.durationMs(source.path)
            assertEquals(10_000L, duration)
            assertEquals("mp4", VideoCut.outputExtension(source.path, source.name))

            val clip = File(dir, VideoCut.clipName(source.name, 2_345, 7_890))
            var seen = 0
            val ok = VideoCut.cut(source.path, clip.path, 2_345, 7_890, { seen++ }, duration)
            assertTrue(ok && clip.length() > 0L)
            assertFalse(File(dir, clip.nameWithoutExtension + ".part.mp4").exists())
            assertEquals(139, frames(clip))
            val at = psnr(clip, source, 59)
            assertTrue(at > psnr(clip, source, 58) && at > psnr(clip, source, 60) && at > 30.0, "first frame psnr $at")
            assertTrue(seen > 0)

            val tail = File(dir, "tail.mp4")
            assertTrue(VideoCut.cut(source.path, tail.path, 7_000, duration, {}, duration))
            assertEquals(75, frames(tail))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun webmStaysWebmWhenVp9IsThere() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir, "src.webm", 4, "320x240", listOf("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-c:a", "libopus"))
            assertEquals("webm", VideoCut.outputExtension(source.path, source.name))
            val clip = File(dir, "clip.webm")
            assertTrue(VideoCut.cut(source.path, clip.path, 1_000, 2_000, {}, VideoCut.durationMs(source.path)))
            assertEquals(25, frames(clip))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelStopsFfmpegAndLeavesNoFile() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir, "long.mp4", 60, "1920x1080", listOf("-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac"))
            val clip = File(dir, "clip.mp4")
            val started = System.currentTimeMillis()
            val job = launch { VideoCut.cut(source.path, clip.path, 1_000, 59_000, {}, 60_000) }
            delay(1_500)
            job.cancel()
            job.join()
            assertTrue(System.currentTimeMillis() - started < 6_000)
            Thread.sleep(500)
            assertFalse(clip.exists())
            assertFalse(File(dir, "clip.part.mp4").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
