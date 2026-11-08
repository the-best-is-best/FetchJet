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

private fun audioCodecChoices(targetExt: String): List<List<String>> = when (targetExt.lowercase()) {
    "mp3" -> listOf(listOf("-c:a", "libmp3lame", "-q:a", "2"))
    "m4a" -> listOf(listOf("-c:a", "aac", "-b:a", "192k"))
    "opus" -> listOf(
        listOf("-c:a", "libopus", "-b:a", "160k"),
        listOf("-c:a", "opus", "-strict", "-2", "-ar", "48000", "-b:a", "160k")
    )
    "ogg" -> listOf(
        listOf("-c:a", "libvorbis", "-q:a", "5"),
        listOf("-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "5")
    )
    "flac" -> listOf(listOf("-c:a", "flac"))
    "wav" -> listOf(listOf("-c:a", "pcm_s16le"))
    else -> listOf(listOf("-c", "copy"))
}

private fun audioArgs(input: String, output: String, codec: List<String>): List<String> =
    listOf("-y", "-i", input, "-vn", "-map_metadata", "0") + codec + listOf(output)

private fun canCopyAudio(sourceExt: String, targetExt: String): Boolean = when (targetExt) {
    "mp3", "opus", "flac" -> sourceExt == "mka" || sourceExt == "webm" || sourceExt == "ogg"
    "m4a" -> sourceExt in listOf("m4a", "mp4", "aac")
    else -> false
}

private fun fileSize(path: Path): Long = try {
    if (FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.metadata(path).size ?: 0L else 0L
} catch (_: Exception) {
    0L
}

private suspend fun ffmpegTo(output: Path, args: List<String>): Boolean = try {
    runLocalFfmpeg(args) && fileSize(output) > 1024L
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    false
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
    val input = source.toString()
    val attempts = buildList {
        if (canCopyAudio(sourceExt, targetExt)) add(listOf("-c:a", "copy"))
        addAll(audioCodecChoices(targetExt))
    }
    val ok = attempts.any { ffmpegTo(output, audioArgs(input, output.toString(), it)) }
    reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
    if (!ok) {
        try {
            FileSystem.SYSTEM.delete(output)
        } catch (_: Exception) {
        }
        if (sourceExt == "mka") {
            val m4a = "$cacheDir/${fileName.substringBeforeLast('.')}.m4a".toPath()
            if (ffmpegTo(m4a, audioArgs(input, m4a.toString(), listOf("-c:a", "copy")))) {
                com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: converting to $targetExt failed, saved as .m4a")
                try {
                    FileSystem.SYSTEM.delete(source)
                } catch (_: Exception) {
                }
                return m4a
            }
        }
        com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: converting to $targetExt failed, keeping .$sourceExt")
        return source
    }
    try {
        FileSystem.SYSTEM.delete(source)
    } catch (_: Exception) {
    }
    return output
}
