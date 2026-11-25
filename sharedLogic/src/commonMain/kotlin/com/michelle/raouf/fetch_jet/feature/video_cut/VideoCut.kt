package com.michelle.raouf.fetch_jet.feature.video_cut

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.media_extractor.runLocalFfmpeg
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toPath

enum class VideoCutMode { TIME, TIMELINE }

data class CutClip(
    val taskId: String,
    val path: String,
    val name: String,
    val bytes: Long,
    val startMs: Long,
    val endMs: Long
)

data class VideoCutProgress(val fraction: Float, val elapsedMs: Long, val etaMs: Long)

data class VideoEncodePlan(val extension: String, val videoArgs: List<String>, val audioArgs: List<String>)

expect object PlatformVideoCutter {
    fun outputExtension(sourceExtension: String): String?
    suspend fun durationMs(input: String): Long?
    suspend fun cut(
        input: String,
        output: String,
        startMs: Long,
        endMs: Long,
        onProgress: (VideoCutProgress) -> Unit
    ): Boolean?
}

class CutEta(private val startedAtMs: Long = currentEpochMs()) {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var smoothedEta = -1.0

    fun update(fraction: Float, nowMs: Long = currentEpochMs()): VideoCutProgress {
        val done = fraction.coerceIn(0f, 1f)
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
        samples.addLast(nowMs to done)
        while (samples.size > 2 && nowMs - samples.first().first > WINDOW_MS) samples.removeFirst()
        if (done >= 1f) return VideoCutProgress(1f, elapsed, 0L)
        if (done < MIN_FRACTION || elapsed < MIN_ELAPSED_MS) return VideoCutProgress(done, elapsed, -1L)
        val overall = done / elapsed.toDouble()
        val first = samples.first()
        val span = nowMs - first.first
        val recent = if (span >= MIN_WINDOW_MS && done > first.second) (done - first.second) / span.toDouble() else overall
        val rate = (overall + recent) / 2.0
        if (rate <= 0.0) return VideoCutProgress(done, elapsed, if (smoothedEta < 0) -1L else smoothedEta.toLong())
        val raw = (1.0 - done) / rate
        smoothedEta = if (smoothedEta < 0) raw else {
            val ticked = (smoothedEta - (nowMs - lastMs)).coerceAtLeast(0.0)
            ticked + (raw - ticked) * SMOOTHING
        }
        lastMs = nowMs
        return VideoCutProgress(done, elapsed, smoothedEta.toLong().coerceAtLeast(0L))
    }

    private var lastMs = startedAtMs

    private companion object {
        const val WINDOW_MS = 8_000L
        const val MIN_WINDOW_MS = 1_500L
        const val MIN_FRACTION = 0.01f
        const val MIN_ELAPSED_MS = 1_200L
        const val SMOOTHING = 0.25
    }
}

object VideoCut {
    const val MIN_LENGTH_MS = 100L

    private val KEEP_CONTAINER = setOf("mp4", "m4v", "mov", "mkv", "ts")
    private val FASTSTART = setOf("mp4", "m4v", "mov")
    private val H264_ENCODERS = listOf("libx264", "h264_videotoolbox", "mpeg4")
    private val usable = mutableMapOf<String, Boolean>()
    private val probeLock = Mutex()

    fun canCut(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
                !task.hidden &&
                task.engineType != EngineType.BITTORRENT &&
                task.postProcessLabel == null &&
                !isClip(task) &&
                MediaPlayback.isVideoName(task.fileName)

    fun isClip(task: DownloadTask): Boolean = task.cutFromTaskId.isNotBlank()

    fun sourcePath(task: DownloadTask): String = AudioCompress.sourcePath(task)

    fun clipName(sourceName: String, startMs: Long, endMs: Long, extension: String = ""): String {
        val ext = extension.ifBlank { sourceName.substringAfterLast('.', "mp4") }.trimStart('.')
        val base = (if (sourceName.contains('.')) sourceName.substringBeforeLast('.') else sourceName)
            .map { if (it in UNSAFE || it.code < 32) '_' else it }.joinToString("").trim().ifBlank { "video" }
        return "$base (cut ${nameTime(startMs)} - ${nameTime(endMs)}).$ext"
    }

    private const val UNSAFE = "\\/:*?\"<>|"

    private fun nameTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3_600_000L
        val m = (total % 3_600_000L) / 60_000L
        val s = (total % 60_000L) / 1000L
        val milli = total % 1000L
        val main = if (h > 0) "$h-${pad(m)}-${pad(s)}" else "$m-${pad(s)}"
        return if (milli == 0L) main else "$main.${milli.toString().padStart(3, '0')}"
    }

    private fun pad(v: Long) = v.toString().padStart(2, '0')

    fun clampRange(startMs: Long, endMs: Long, durationMs: Long): Pair<Long, Long> {
        if (durationMs in 1 until MIN_LENGTH_MS) return 0L to durationMs
        val top = if (durationMs > 0L) durationMs else Long.MAX_VALUE / 4
        val start = startMs.coerceIn(0L, top - MIN_LENGTH_MS)
        val end = endMs.coerceIn(start + MIN_LENGTH_MS, top)
        return start to end
    }

    fun isValidRange(startMs: Long, endMs: Long, durationMs: Long): Boolean =
        startMs >= 0L && endMs - startMs >= MIN_LENGTH_MS && (durationMs <= 0L || endMs <= durationMs)

    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3_600_000L
        val m = (total % 3_600_000L) / 60_000L
        val s = (total % 60_000L) / 1000L
        val milli = total % 1000L
        return "${pad(h)}:${pad(m)}:${pad(s)}.${milli.toString().padStart(3, '0')}"
    }

    fun parseTime(text: String): Long? {
        val clean = text.trim().replace(',', '.')
        if (clean.isEmpty()) return null
        val parts = clean.split(':')
        if (parts.size > 3) return null
        val secondsPart = parts.last()
        val whole = secondsPart.substringBefore('.')
        val fraction = secondsPart.substringAfter('.', "")
        if (fraction.length > 3 || fraction.any { !it.isDigit() }) return null
        val seconds = (if (whole.isEmpty()) 0L else whole.toLongOrNull()) ?: return null
        val milli = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').toLong()
        val upper = parts.dropLast(1).map { it.toLongOrNull() ?: return null }
        val minutes = upper.lastOrNull() ?: 0L
        val hours = if (upper.size == 2) upper[0] else 0L
        if (seconds < 0 || minutes < 0 || hours < 0) return null
        if (parts.size > 1 && seconds >= 60) return null
        if (parts.size > 2 && minutes >= 60) return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000L + milli
    }

    fun rangeMessage(startMs: Long, endMs: Long, durationMs: Long): String? {
        val strings = LocalizationManager.Strings
        val full = formatTime(durationMs)
        return when {
            endMs - startMs < MIN_LENGTH_MS -> strings.clipStartAfterEnd(formatTime(startMs), formatTime(endMs))
            durationMs > 0L && startMs >= durationMs -> strings.clipStartExceedsVideo(full)
            durationMs > 0L && endMs > durationMs -> strings.clipEndExceedsVideo(formatTime(endMs), full)
            else -> null
        }
    }

    fun clipsOf(sourceTaskId: String, tasks: List<DownloadTask>): List<CutClip> =
        tasks.filter { it.cutFromTaskId == sourceTaskId }
            .sortedBy { it.createdAtEpochMs }
            .map { clip ->
                val path = sourcePath(clip)
                val size = runCatching { AppFileSystem.metadataOrNull(path.toPath())?.size }.getOrNull()
                CutClip(clip.id, path, clip.fileName, size ?: clip.totalBytes, clip.cutStartMs, clip.cutEndMs)
            }

    fun clipTask(
        id: String,
        source: DownloadTask,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        bytes: Long
    ): DownloadTask = DownloadTask(
        id = id,
        url = "",
        fileName = outputPath.toPath().name,
        destinationPath = source.destinationPath,
        engineType = EngineType.HTTP_DIRECT,
        status = DownloadStatus.COMPLETED,
        totalBytes = bytes,
        downloadedBytes = bytes,
        progress = 1f,
        createdAtEpochMs = currentEpochMs(),
        cutFromTaskId = source.id,
        cutStartMs = startMs,
        cutEndMs = endMs
    )

    suspend fun durationMs(input: String): Long {
        PlatformVideoCutter.durationMs(input)?.takeIf { it > 0L }?.let { return it }
        val head = packetSpan(input, listOf("-copyts", "-i", input, "-map", "0:v:0", "-c", "copy", "-frames:v", "16"))
        val tail = packetSpan(input, listOf("-sseof", "-5", "-copyts", "-i", input, "-map", "0:v:0", "-c", "copy"))
        if (head != null && tail != null && tail.second > head.first) return tail.second - head.first
        return streamCopyMs(input)
    }

    private suspend fun packetSpan(input: String, args: List<String>): Pair<Long, Long>? {
        val file = progressFile().toString() + ".framemd5"
        val ok = runLocalFfmpeg(listOf("-y", "-nostdin", "-v", "error") + args + listOf("-f", "framemd5", file))
        val text = runCatching { AppFileSystem.read(file.toPath()) { readUtf8() } }.getOrNull()
        runCatching { AppFileSystem.delete(file.toPath()) }
        if (!ok || text == null) return null
        return packetSpanOf(text)
    }

    fun packetSpanOf(framemd5: String): Pair<Long, Long>? {
        val base = framemd5.lineSequence().firstOrNull { it.startsWith("#tb 0:") }
            ?.substringAfter(':')?.trim()?.split('/') ?: return null
        val num = base.getOrNull(0)?.trim()?.toLongOrNull() ?: return null
        val den = base.getOrNull(1)?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        var first = Long.MAX_VALUE
        var end = Long.MIN_VALUE
        framemd5.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val cells = line.split(',').map { it.trim() }
            if (cells.getOrNull(0) != "0") return@forEach
            val pts = cells.getOrNull(2)?.toLongOrNull() ?: return@forEach
            val duration = cells.getOrNull(3)?.toLongOrNull() ?: 0L
            first = minOf(first, pts)
            end = maxOf(end, pts + duration)
        }
        if (first == Long.MAX_VALUE) return null
        return first * 1000L * num / den to end * 1000L * num / den
    }

    private suspend fun streamCopyMs(input: String): Long {
        val progress = progressFile()
        val ok = runLocalFfmpeg(
            listOf(
                "-y", "-nostdin", "-v", "error", "-progress", progress.toString(),
                "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-f", "null", "-"
            )
        )
        val reached = readOutTimeMs(progress)
        runCatching { AppFileSystem.delete(progress) }
        return if (ok) reached else 0L
    }

    suspend fun outputExtension(input: String, sourceName: String): String {
        val ext = sourceName.substringAfterLast('.', "").lowercase()
        PlatformVideoCutter.outputExtension(ext)?.let { return it }
        return encodePlan(input, ext)?.extension ?: "mp4"
    }

    suspend fun encodePlan(input: String, sourceExtension: String): VideoEncodePlan? {
        val ext = sourceExtension.lowercase()
        if (ext == "webm" && works(input, "libvpx-vp9")) {
            return VideoEncodePlan(
                "webm",
                listOf("-c:v", "libvpx-vp9", "-crf", "28", "-b:v", "0", "-deadline", "good", "-cpu-used", "3", "-row-mt", "1"),
                listOf("-c:a", "libopus", "-b:a", "160k")
            )
        }
        val target = if (ext in KEEP_CONTAINER) ext else "mp4"
        val encoder = H264_ENCODERS.firstOrNull { works(input, it) } ?: return null
        val video = when (encoder) {
            "libx264" -> listOf("-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p")
            "h264_videotoolbox" -> listOf("-c:v", "h264_videotoolbox", "-b:v", "${sourceBitrateKbps(input)}k", "-pix_fmt", "yuv420p")
            else -> listOf("-c:v", "mpeg4", "-q:v", "2")
        }
        return VideoEncodePlan(target, video, listOf("-c:a", "aac", "-b:a", "192k"))
    }

    private suspend fun sourceBitrateKbps(input: String): Long {
        val bytes = runCatching { AppFileSystem.metadataOrNull(input.toPath())?.size }.getOrNull() ?: 0L
        val duration = durationMs(input)
        if (bytes <= 0L || duration <= 0L) return 8000L
        return (bytes * 8L / duration).coerceIn(1500L, 80_000L)
    }

    private suspend fun works(input: String, encoder: String): Boolean = probeLock.withLock {
        usable[encoder]?.let { return@withLock it }
        val ok = runCatching {
            runLocalFfmpeg(
                listOf(
                    "-y", "-nostdin", "-v", "error", "-t", "0.5", "-i", input,
                    "-map", "0:v:0", "-frames:v", "2", "-c:v", encoder, "-f", "null", "-"
                )
            )
        }.getOrDefault(false)
        if (ok) usable[encoder] = true
        ok
    }

    suspend fun cut(
        input: String,
        output: String,
        startMs: Long,
        endMs: Long,
        onProgress: (VideoCutProgress) -> Unit,
        sourceDurationMs: Long = 0L
    ): Boolean {
        if (startMs < 0L || endMs - startMs < MIN_LENGTH_MS) return false
        PlatformVideoCutter.cut(input, output, startMs, endMs, onProgress)?.let { return it }
        val ext = output.substringAfterLast('.', "").lowercase()
        val plan = encodePlan(input, ext)?.takeIf { it.extension == ext } ?: return false
        val toEnd = sourceDurationMs > 0L && endMs >= sourceDurationMs
        val lengthMs = (if (toEnd) sourceDurationMs else endMs) - startMs
        return coroutineScope {
            val progress = progressFile()
            val partial = output.substringBeforeLast('.') + ".part.$ext"
            val args = buildList {
                addAll(listOf("-y", "-nostdin", "-v", "error", "-progress", progress.toString()))
                if (startMs > 0L) addAll(listOf("-ss", seconds(startMs)))
                addAll(listOf("-accurate_seek", "-i", input))
                if (!toEnd) addAll(listOf("-t", seconds(endMs - startMs)))
                addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-map_metadata", "0", "-map_chapters", "-1"))
                addAll(plan.videoArgs)
                addAll(plan.audioArgs)
                if (ext in FASTSTART) addAll(listOf("-movflags", "+faststart"))
                add(partial)
            }
            val eta = CutEta()
            onProgress(eta.update(0f))
            val watcher = launch {
                while (isActive) {
                    delay(400)
                    val reached = readOutTimeMs(progress)
                    onProgress(eta.update(if (lengthMs > 0L) reached.toFloat() / lengthMs else 0f))
                }
            }
            var ok = false
            try {
                ok = runLocalFfmpeg(args, CUT_TIMEOUT_MS)
                val made = partial.toPath()
                ok = ok && (AppFileSystem.metadataOrNull(made)?.size ?: 0L) > 0L &&
                        runCatching { AppFileSystem.atomicMove(made, output.toPath()) }.isSuccess
                if (ok) onProgress(eta.update(1f))
                ok
            } finally {
                watcher.cancel()
                runCatching { AppFileSystem.delete(progress) }
                if (!ok) withContext(NonCancellable) { deleteWhenFree(partial) }
            }
        }
    }

    suspend fun deleteWhenFree(path: String) {
        val file = path.toPath()
        repeat(25) {
            if (runCatching { AppFileSystem.delete(file) }.isSuccess && !AppFileSystem.exists(file)) return
            delay(200)
        }
    }

    private const val CUT_TIMEOUT_MS = 12L * 60 * 60 * 1000

    private fun seconds(ms: Long): String = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"

    private fun progressFile(): Path =
        "${PathResolver().getTempDirectory().trimEnd('/', '\\')}/fetchjet-cut-${currentEpochMs()}-${kotlin.random.Random.nextInt(1000, 9999)}.progress".toPath()

    private fun readOutTimeMs(file: Path): Long {
        val text = runCatching { AppFileSystem.read(file) { readUtf8() } }.getOrNull() ?: return 0L
        val micros = text.lineSequence()
            .filter { it.startsWith("out_time_us=") || it.startsWith("out_time_ms=") }
            .mapNotNull { it.substringAfter('=').trim().toLongOrNull() }
            .lastOrNull() ?: return 0L
        return (micros / 1000L).coerceAtLeast(0L)
    }
}
