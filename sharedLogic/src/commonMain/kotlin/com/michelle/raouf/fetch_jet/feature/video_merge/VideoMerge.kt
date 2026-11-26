package com.michelle.raouf.fetch_jet.feature.video_merge

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.media_extractor.runLocalFfmpeg
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.video_cut.CutEta
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutProgress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath

data class MergeStreamInfo(
    val extension: String,
    val videoCodec: String,
    val dimensions: String,
    val audioCodec: String,
    val sampleRate: String,
    val channels: String
)

expect object PlatformMergeProbe {
    suspend fun info(path: String): MergeStreamInfo?
}

object VideoMerge {
    private val FASTSTART = setOf("mp4", "m4v", "mov")
    private const val UNSAFE = "\\/:*?\"<>|"
    private const val MERGE_TIMEOUT_MS = 6L * 60 * 60 * 1000

    fun canMerge(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
                !task.hidden &&
                task.engineType != EngineType.BITTORRENT &&
                task.postProcessLabel == null &&
                MediaPlayback.isVideoName(task.fileName)

    fun canMergeAll(tasks: List<DownloadTask>): Boolean = tasks.size >= 2 && tasks.all { canMerge(it) }

    fun sourcePath(task: DownloadTask): String = AudioCompress.sourcePath(task)

    fun mergedName(firstName: String): String {
        val ext = firstName.substringAfterLast('.', "mp4")
        val base = (if (firstName.contains('.')) firstName.substringBeforeLast('.') else firstName)
            .map { if (it in UNSAFE || it.code < 32) '_' else it }.joinToString("").trim().ifBlank { "video" }
        return "$base (merged).$ext"
    }

    fun withExtension(name: String, extension: String): String {
        val clean = name.trim().map { if (it in UNSAFE || it.code < 32) '_' else it }.joinToString("").trim()
        if (clean.isBlank()) return ""
        return if (clean.substringAfterLast('.', "").equals(extension, ignoreCase = true)) clean else "$clean.$extension"
    }

    fun infoOf(extension: String, framemd5: String): MergeStreamInfo? {
        val fields = HashMap<String, String>()
        framemd5.lineSequence().filter { it.startsWith("#") && ':' in it }.forEach { line ->
            val key = line.substringBefore(':').removePrefix("#").trim()
            fields[key] = line.substringAfter(':').trim()
        }
        val streams = fields.keys.mapNotNull { key -> key.substringAfter("media_type ", "").takeIf { it.isNotEmpty() } }
        val video = streams.firstOrNull { fields["media_type $it"] == "video" } ?: return null
        val audio = streams.firstOrNull { fields["media_type $it"] == "audio" }
        return MergeStreamInfo(
            extension = extension.lowercase(),
            videoCodec = fields["codec_id $video"].orEmpty(),
            dimensions = fields["dimensions $video"].orEmpty(),
            audioCodec = audio?.let { fields["codec_id $it"] }.orEmpty(),
            sampleRate = audio?.let { fields["sample_rate $it"] }.orEmpty(),
            channels = audio?.let { fields["channel_layout_name $it"] }.orEmpty()
        )
    }

    fun firstMismatch(infos: List<MergeStreamInfo?>): Int? {
        val first = infos.firstOrNull() ?: return 0
        if (first.videoCodec.isBlank()) return 0
        val index = infos.indexOfFirst { it == null || it != first }
        return index.takeIf { it >= 0 }
    }

    fun concatList(paths: List<String>): String =
        paths.joinToString("\n", postfix = "\n") { "file '" + it.replace("'", "'\\''") + "'" }

    suspend fun info(path: String): MergeStreamInfo? {
        PlatformMergeProbe.info(path)?.let { return it }
        val file = tempFile("framemd5")
        return try {
            val ok = runLocalFfmpeg(
                listOf(
                    "-y", "-nostdin", "-v", "error", "-i", path, "-map", "0:v:0", "-map", "0:a:0?",
                    "-c", "copy", "-frames:v", "1", "-f", "framemd5", file.toString()
                )
            )
            val text = if (ok) runCatching { AppFileSystem.read(file) { readUtf8() } }.getOrNull() else null
            text?.let { infoOf(path.substringAfterLast('.', ""), it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { AppFileSystem.delete(file) }
        }
    }

    suspend fun merge(
        inputs: List<String>,
        output: String,
        totalDurationMs: Long,
        onProgress: (VideoCutProgress) -> Unit
    ): Boolean {
        if (inputs.size < 2) return false
        val ext = output.substringAfterLast('.', "").lowercase()
        return coroutineScope {
            val list = tempFile("txt")
            val progress = tempFile("progress")
            val partial = output.substringBeforeLast('.') + ".part.$ext"
            val eta = CutEta()
            onProgress(eta.update(0f))
            val watcher = launch {
                while (isActive) {
                    delay(400)
                    val reached = readOutTimeMs(progress)
                    onProgress(eta.update(if (totalDurationMs > 0L) reached.toFloat() / totalDurationMs else 0f))
                }
            }
            var ok = false
            try {
                AppFileSystem.write(list) { writeUtf8(concatList(inputs)) }
                val args = buildList {
                    addAll(listOf("-y", "-nostdin", "-v", "error", "-progress", progress.toString()))
                    addAll(listOf("-f", "concat", "-safe", "0", "-i", list.toString()))
                    addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-map_metadata", "-1"))
                    if (ext in FASTSTART) addAll(listOf("-movflags", "+faststart"))
                    add(partial)
                }
                ok = runLocalFfmpeg(args, MERGE_TIMEOUT_MS)
                val made = partial.toPath()
                ok = ok && (AppFileSystem.metadataOrNull(made)?.size ?: 0L) > 0L &&
                        runCatching { AppFileSystem.atomicMove(made, output.toPath()) }.isSuccess
                if (ok) onProgress(eta.update(1f))
                ok
            } finally {
                watcher.cancel()
                runCatching { AppFileSystem.delete(progress) }
                runCatching { AppFileSystem.delete(list) }
                if (!ok) withContext(NonCancellable) { VideoCut.deleteWhenFree(partial) }
            }
        }
    }

    fun mergedTask(id: String, first: DownloadTask, outputPath: String, bytes: Long): DownloadTask = DownloadTask(
        id = id,
        url = "",
        fileName = outputPath.toPath().name,
        destinationPath = first.destinationPath,
        engineType = EngineType.HTTP_DIRECT,
        status = DownloadStatus.COMPLETED,
        totalBytes = bytes,
        downloadedBytes = bytes,
        progress = 1f,
        createdAtEpochMs = currentEpochMs()
    )

    private fun tempFile(extension: String): Path =
        "${PathResolver().getTempDirectory().trimEnd('/', '\\')}/fetchjet-merge-${currentEpochMs()}-${kotlin.random.Random.nextInt(1000, 9999)}.$extension".toPath()

    private fun readOutTimeMs(file: Path): Long {
        val text = runCatching { AppFileSystem.read(file) { readUtf8() } }.getOrNull() ?: return 0L
        val micros = text.lineSequence()
            .filter { it.startsWith("out_time_us=") || it.startsWith("out_time_ms=") }
            .mapNotNull { it.substringAfter('=').trim().toLongOrNull() }
            .lastOrNull() ?: return 0L
        return (micros / 1000L).coerceAtLeast(0L)
    }
}
