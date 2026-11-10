package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import okio.Path
import okio.Path.Companion.toPath

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

private val CODEC_MARKERS = listOf(
    "A_MPEG/L3" to "mp3",
    "A_AAC" to "aac",
    "A_OPUS" to "opus",
    "OpusHead" to "opus",
    "A_FLAC" to "flac",
    "fLaC" to "flac",
    "A_VORBIS" to "vorbis",
    "\u0001vorbis" to "vorbis"
)

internal fun audioCodecOf(path: Path): String? = try {
    val head = AppFileSystem.read(path) {
        val buffer = okio.Buffer()
        while (buffer.size < 65_536L && read(buffer, 65_536L - buffer.size) != -1L) Unit
        buffer.readByteArray()
    }
    val text = head.decodeToString()
    CODEC_MARKERS.map { (marker, codec) -> text.indexOf(marker) to codec }
        .filter { it.first >= 0 }
        .minByOrNull { it.first }?.second
} catch (_: Exception) {
    null
}

internal fun canCopyAudio(sourceExt: String, targetExt: String, sourceCodec: String?): Boolean = when (targetExt) {
    "mp3", "opus", "flac" -> (sourceExt == "mka" || sourceExt == "webm" || sourceExt == "ogg") &&
            (sourceCodec == null || sourceCodec == targetExt)
    "m4a" -> sourceExt in listOf("m4a", "mp4", "aac")
    else -> false
}

private fun fileSize(path: Path): Long = try {
    if (AppFileSystem.exists(path)) AppFileSystem.metadata(path).size ?: 0L else 0L
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
        val codec = if (sourceExt in listOf("mka", "webm", "ogg")) audioCodecOf(source) else null
        if (canCopyAudio(sourceExt, targetExt, codec)) add(listOf("-c:a", "copy"))
        addAll(audioCodecChoices(targetExt))
    }
    val ok = attempts.any { ffmpegTo(output, audioArgs(input, output.toString(), it)) }
    reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
    if (!ok) {
        try {
            AppFileSystem.delete(output)
        } catch (_: Exception) {
        }
        if (sourceExt == "mka") {
            val m4a = "$cacheDir/${fileName.substringBeforeLast('.')}.m4a".toPath()
            if (ffmpegTo(m4a, audioArgs(input, m4a.toString(), listOf("-c:a", "copy")))) {
                com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: converting to $targetExt failed, saved as .m4a")
                try {
                    AppFileSystem.delete(source)
                } catch (_: Exception) {
                }
                return m4a
            }
        }
        com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: converting to $targetExt failed, keeping .$sourceExt")
        return source
    }
    try {
        AppFileSystem.delete(source)
    } catch (_: Exception) {
    }
    return output
}
