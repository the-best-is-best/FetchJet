package com.michelle.raouf.fetch_jet.feature.media_extractor

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

val AUDIO_CONVERT_FORMATS = listOf("mp3", "m4a", "opus", "flac", "wav", "ogg")

internal fun isAudioConvertTarget(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in AUDIO_CONVERT_FORMATS

internal fun hlsOutputFor(cacheDir: String, fileName: String, cacheTargetPath: Path): Path =
    if (isAudioConvertTarget(fileName) && !fileName.endsWith(".m4a", ignoreCase = true))
        "$cacheDir/${fileName.substringBeforeLast('.')}.mka".toPath()
    else cacheTargetPath

internal fun audioEncoderArgs(input: String, output: String, targetExt: String): List<String> {
    val codec = when (targetExt.lowercase()) {
        "mp3" -> listOf("-c:a", "libmp3lame", "-q:a", "2")
        "m4a" -> listOf("-c:a", "aac", "-b:a", "192k")
        "opus" -> listOf("-c:a", "libopus", "-b:a", "160k")
        "ogg" -> listOf("-c:a", "libvorbis", "-q:a", "5")
        "flac" -> listOf("-c:a", "flac")
        "wav" -> listOf("-c:a", "pcm_s16le")
        else -> listOf("-c", "copy")
    }
    return listOf("-y", "-i", input, "-vn", "-map_metadata", "0") + codec + listOf(output)
}

private fun audioCopyArgs(input: String, output: String, sourceExt: String, targetExt: String): List<String>? {
    val copyOk = when (targetExt) {
        "mp3", "opus", "flac" -> true
        "m4a" -> sourceExt in listOf("m4a", "mp4", "aac")
        else -> false
    }
    return if (copyOk) listOf("-y", "-i", input, "-vn", "-map_metadata", "0", "-c:a", "copy", output) else null
}

internal suspend fun MediaStreamDownloader.convertAudioIfNeeded(
    taskId: String,
    source: Path,
    fileName: String,
    cacheDir: String
): Path {
    val targetExt = fileName.substringAfterLast('.', "").lowercase()
    val sourceExt = source.name.substringAfterLast('.', "").lowercase()
    if (targetExt !in AUDIO_CONVERT_FORMATS || sourceExt == targetExt) return source
    val output = "$cacheDir/${fileName.substringBeforeLast('.')}.$targetExt".toPath()
    if (output == source) return source
    reportPostProcess(taskId, "Converting to ${targetExt.uppercase()}", -1f)
    val ok = try {
        (audioCopyArgs(source.toString(), output.toString(), sourceExt, targetExt)
            ?.let { runLocalFfmpeg(it) } == true && FileSystem.SYSTEM.exists(output)) ||
                runLocalFfmpeg(audioEncoderArgs(source.toString(), output.toString(), targetExt))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
    val size = if (ok && FileSystem.SYSTEM.exists(output)) {
        try {
            FileSystem.SYSTEM.metadata(output).size ?: 0L
        } catch (_: Exception) {
            0L
        }
    } else 0L
    reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
    if (size <= 1024L) {
        com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: converting to $targetExt failed, keeping .$sourceExt")
        try {
            FileSystem.SYSTEM.delete(output)
        } catch (_: Exception) {
        }
        return source
    }
    try {
        FileSystem.SYSTEM.delete(source)
    } catch (_: Exception) {
    }
    return output
}
