package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.math.roundToLong

internal const val STEP_CUTTING_CLIP = "Cutting the clip"

private val COPY_FASTSTART = setOf("mp4", "m4v", "mov", "m4a")

internal fun clipTimeMs(text: String?): Long? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.let { parseTimeToSecondsOrNull(it) }
        ?.let { (it * 1000.0).roundToLong().coerceAtLeast(0L) }

internal fun clipSectionArgs(clipStart: String?, clipEnd: String?, clipByCutting: Boolean): List<String> {
    if (clipByCutting || (clipStart.isNullOrBlank() && clipEnd.isNullOrBlank())) return emptyList()
    val s = clipStart?.trim()?.ifBlank { null } ?: "0"
    val e = clipEnd?.trim()?.ifBlank { null } ?: "inf"
    return listOf("--download-sections", "*$s-$e")
}

internal fun isClipTask(task: DownloadTask): Boolean =
    !task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()

internal suspend fun MediaStreamDownloader.cutClipExactly(
    taskId: String,
    task: DownloadTask,
    file: Path,
    offsetMs: Long,
    sectionWithPreRoll: Boolean
): Path {
    val startMs = clipTimeMs(task.clipStart) ?: 0L
    val endMs = clipTimeMs(task.clipEnd)
    if (startMs <= 0L && endMs == null) return file
    val name = file.name
    val audio = VideoCut.isAudioName(name)
    if (sectionWithPreRoll && audio) return file
    val from = (startMs - offsetMs).coerceAtLeast(0L)
    val to = endMs?.let { (it - offsetMs).coerceAtLeast(0L) }
    val input = file.toString()
    val durationMs = runCatching { VideoCut.durationMs(input) }.getOrDefault(0L)
    if (from <= 0L && !sectionWithPreRoll && (to == null || (durationMs > 0L && to >= durationMs))) return file
    val end = when {
        to == null -> durationMs
        durationMs > 0L -> minOf(to, durationMs)
        else -> to
    }
    if (end - from < VideoCut.MIN_LENGTH_MS) {
        if (sectionWithPreRoll) return file
        throw Exception("The clip range is outside the video")
    }
    val base = name.substringBeforeLast('.')
    val parent = file.parent ?: return file
    val ext = runCatching { VideoCut.outputExtension(input, name) }.getOrDefault(name.substringAfterLast('.', "mp4"))
    val out = parent / "$base.fjclip.$ext"
    reportPostProcess(taskId, STEP_CUTTING_CLIP, 0f)
    val ok = try {
        VideoCut.cut(
            input,
            out.toString(),
            from,
            end,
            { reportPostProcess(taskId, STEP_CUTTING_CLIP, it.fraction.coerceIn(0f, 0.99f)) },
            if (to == null) durationMs else 0L
        )
    } catch (e: CancellationException) {
        withContext(NonCancellable) { VideoCut.deleteWhenFree(out.toString()) }
        throw e
    } catch (e: Exception) {
        AppLog.e("clip cut failed: ${e.message}")
        false
    }
    if (ok && AppFileSystem.exists(out)) {
        val target = parent / "$base.$ext"
        runCatching { AppFileSystem.delete(file) }
        AppFileSystem.atomicMove(out, target)
        reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
        return target
    }
    runCatching { AppFileSystem.delete(out) }
    AppLog.e("clip cut: the exact cut failed, using a stream copy")
    if (sectionWithPreRoll) {
        reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
        return file
    }
    return copyCut(taskId, file, from, to) ?: throw Exception("ffmpeg could not cut the clip")
}

private suspend fun MediaStreamDownloader.copyCut(taskId: String, file: Path, fromMs: Long, toMs: Long?): Path? {
    val parent = file.parent ?: return null
    val ext = file.name.substringAfterLast('.', "mp4")
    val out = parent / "${file.name.substringBeforeLast('.')}.fjclip.$ext"
    reportPostProcess(taskId, STEP_CUTTING_CLIP, -1f)
    val args = buildList {
        addAll(listOf("-y", "-nostdin", "-v", "error"))
        if (fromMs > 0L) addAll(listOf("-ss", seconds(fromMs)))
        addAll(listOf("-i", file.toString()))
        if (toMs != null) addAll(listOf("-t", seconds(toMs - fromMs)))
        addAll(listOf("-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero"))
        if (ext.lowercase() in COPY_FASTSTART) addAll(listOf("-movflags", "+faststart"))
        add(out.toString())
    }
    val ok = try {
        runLocalFfmpeg(args) && (AppFileSystem.metadataOrNull(out)?.size ?: 0L) > 0L
    } catch (e: CancellationException) {
        withContext(NonCancellable) { VideoCut.deleteWhenFree(out.toString()) }
        throw e
    }
    reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
    if (!ok) {
        runCatching { AppFileSystem.delete(out) }
        return null
    }
    runCatching { AppFileSystem.delete(file) }
    AppFileSystem.atomicMove(out, file)
    return file
}

private fun seconds(ms: Long): String {
    val v = ms.coerceAtLeast(0L)
    return "${v / 1000}.${(v % 1000).toString().padStart(3, '0')}"
}
