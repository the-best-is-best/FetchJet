package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipCutTest {
    private val ffmpeg: String? get() = getExistingFfmpegBinary()?.absolutePath

    @Test
    fun sectionArgsOnlyForTheSectionMode() {
        assertEquals(listOf("--download-sections", "*12-17.5"), clipSectionArgs("12", "17.5", false))
        assertEquals(listOf("--download-sections", "*0-00:01:05"), clipSectionArgs(" ", "00:01:05", false))
        assertEquals(listOf("--download-sections", "*11.5-inf"), clipSectionArgs("11.5", null, false))
        assertEquals(emptyList(), clipSectionArgs("12", "17.5", true))
        assertEquals(emptyList(), clipSectionArgs(null, "", false))
        assertEquals(11_500L, clipTimeMs("11.5"))
        assertEquals(3_723_250L, clipTimeMs("01:02:03.25"))
        assertEquals(null, clipTimeMs(""))
    }

    @Test
    fun sectionDownloadIsCutAtTheExactFrame() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir)
            for ((start, end) in listOf("12" to "17.5", "11.5" to "15")) {
                val section = File(dir, "section.mp4")
                ytDlpSection(source, section, start.toDouble(), end.toDouble())
                val downloader = MediaStreamDownloader()
                val task = task(dir, start, end, cutting = false)
                downloader.enqueueTask(task)
                val result = downloader.cutClipExactly(task.id, task, section.path.toPath(), clipTimeMs(start)!!, true)
                assertClip(File(result.toString()), source, start.toDouble(), end.toDouble())
                File(result.toString()).delete()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fullDownloadIsCutAtTheExactFrame() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir)
            for ((start, end) in listOf("12" to "17.5", "11.5" to "15")) {
                val full = File(dir, "full.mp4")
                source.copyTo(full, overwrite = true)
                val downloader = MediaStreamDownloader()
                val task = task(dir, start, end, cutting = true)
                downloader.enqueueTask(task)
                val result = downloader.cutClipExactly(task.id, task, full.path.toPath(), 0L, false)
                assertEquals(full.path.toPath(), result)
                assertClip(full, source, start.toDouble(), end.toDouble())
                assertTrue(dir.listFiles()!!.none { it.name.contains(".fjclip") })
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun startAtZeroCutsTheEndExactly() = runBlocking {
        if (ffmpeg == null) return@runBlocking
        val dir = workDir()
        try {
            val source = makeSource(dir)
            val full = File(dir, "full.mp4")
            source.copyTo(full, overwrite = true)
            val downloader = MediaStreamDownloader()
            val task = task(dir, "", "10", cutting = true)
            downloader.enqueueTask(task)
            val result = downloader.cutClipExactly(task.id, task, full.path.toPath(), 0L, false)
            assertEquals(300, frames(File(result.toString())))
            assertTrue(psnr(File(result.toString()), source, 0) > 35.0)
            val open = task(dir, "20", "", cutting = true)
            downloader.enqueueTask(open)
            val whole = File(dir, "whole.mp4")
            source.copyTo(whole, overwrite = true)
            val tail = downloader.cutClipExactly(open.id, open, whole.path.toPath(), 0L, false)
            assertClip(File(tail.toString()), source, 20.0, 30.0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun realDownloadPathGivesTheExactClip() {
        if (System.getenv("FJ_CLIP_E2E") != "1") return
        val realTools = getToolsDir()
        val realYtDlp = File(realTools, "yt-dlp-dir")
        val realFfmpeg = File(realTools, if (isWindows()) "ffmpeg.exe" else "ffmpeg")
        if (!realYtDlp.isDirectory || !realFfmpeg.isFile) return
        val dir = workDir()
        val oldHome = System.getProperty("user.home")
        val home = File(dir, "home")
        val tools = File(home, ".fetch_jet/tools").apply { mkdirs() }
        realYtDlp.copyRecursively(File(tools, "yt-dlp-dir"))
        File(tools, "yt-dlp-dir").walk().forEach { it.setExecutable(true) }
        realFfmpeg.copyTo(File(tools, realFfmpeg.name)).setExecutable(true)
        System.setProperty("user.home", home.path)
        val server = serve(File(dir, "video.mp4"))
        try {
            val source = makeSource(dir, "video.mp4")
            val url = "http://127.0.0.1:${server.address.port}/video.mp4"
            for (cutting in listOf(false, true)) {
                for ((start, end) in listOf("12" to "17.5", "11.5" to "15")) {
                    val out = File(dir, "out-$cutting-$start").apply { mkdirs() }
                    val downloader = MediaStreamDownloader()
                    val task = task(out, start, end, cutting).copy(url = url, fileName = "clip.mp4", status = DownloadStatus.QUEUED)
                    downloader.enqueueTask(task)
                    runBlocking { downloader.executeMediaDownload(task.id) }
                    val done = downloader.tasksState.value[task.id]!!
                    assertEquals(DownloadStatus.COMPLETED, done.status, "cutting=$cutting $start-$end: ${done.errorMessage}")
                    val clip = File(out, done.fileName)
                    assertEquals("clip.mp4", clip.name)
                    assertClip(clip, source, start.toDouble(), end.toDouble())
                }
            }
        } finally {
            server.stop(0)
            System.setProperty("user.home", oldHome)
            dir.deleteRecursively()
        }
    }

    private fun assertClip(clip: File, source: File, start: Double, end: Double) {
        val first = (start * FPS).toInt()
        val at = psnr(clip, source, first)
        val before = psnr(clip, source, first - 1)
        val after = psnr(clip, source, first + 1)
        assertTrue(at > 35.0 && at > before && at > after, "${clip.name} $start: psnr at $at, before $before, after $after")
        val expected = Math.round((end - start) * FPS).toInt()
        assertEquals(expected, frames(clip), "${clip.name} $start-$end frames")
    }

    private fun task(dir: File, start: String, end: String, cutting: Boolean) = DownloadTask(
        id = "clip-${System.nanoTime()}", url = "http://127.0.0.1/v.mp4", fileName = "v.mp4",
        destinationPath = dir.path, engineType = EngineType.MEDIA_STREAM, status = DownloadStatus.DOWNLOADING,
        clipStart = start, clipEnd = end, clipByCutting = cutting
    )

    private fun ytDlpSection(source: File, out: File, start: Double, end: Double) {
        run("-y", "-v", "error", "-ss", start.toString(), "-t", (end - start).toString(), "-i", source.path, "-c", "copy", "-f", "mp4", out.path)
        assertTrue(out.length() > 0L)
    }

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

    private fun makeSource(dir: File, name: String = "src.mp4"): File {
        val file = File(dir, name)
        run(
            "-hide_banner", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=$FPS:duration=30",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=30", "-shortest",
            "-c:v", "libx264", "-g", "150", "-keyint_min", "150", "-sc_threshold", "0", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-movflags", "+faststart", file.path
        )
        assertEquals(900, frames(file))
        return file
    }

    private fun serve(file: File): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val bytes = file.readBytes()
            val range = ex.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.split('-')
            val from = range?.getOrNull(0)?.toLongOrNull() ?: 0L
            val to = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(bytes.size - 1L) ?: (bytes.size - 1L)
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            val body = bytes.copyOfRange(from.toInt(), to.toInt() + 1)
            if (range != null) ex.responseHeaders.add("Content-Range", "bytes $from-$to/${bytes.size}")
            if (ex.requestMethod == "HEAD") {
                ex.responseHeaders.add("Content-Length", body.size.toString())
                ex.sendResponseHeaders(if (range != null) 206 else 200, -1)
            } else {
                ex.sendResponseHeaders(if (range != null) 206 else 200, body.size.toLong())
                runCatching { ex.responseBody.use { it.write(body) } }
            }
            ex.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        return server
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    private fun workDir(): File = File(System.getProperty("java.io.tmpdir"), "fetchjet-clipcut-test-${System.nanoTime()}").apply { mkdirs() }

    private companion object {
        const val FPS = 30
    }
}
