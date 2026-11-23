package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okio.Path
import okio.Path.Companion.toPath

sealed interface SubtitleAnswer {
    data class Text(val body: String) : SubtitleAnswer
    data object TooManyRequests : SubtitleAnswer
    data object Failed : SubtitleAnswer
}

class SubtitleFetchResult(
    val files: List<Pair<SubtitleTrack, Path>>,
    val refused: List<SubtitleTrack>,
    val failed: List<SubtitleTrack>,
)

const val SUBTITLE_PAUSE_MS = 1_500L
val SUBTITLE_RETRY_WAITS_MS = listOf(5_000L, 15_000L, 30_000L)

val SUBTITLE_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v")

suspend fun fetchSubtitleFiles(
    tracks: List<SubtitleTrack>,
    tempDir: Path,
    fetch: suspend (SubtitleTrack) -> SubtitleAnswer,
    convert: suspend (from: Path, to: Path) -> Boolean,
    format: String = "srt",
    pauseMs: Long = SUBTITLE_PAUSE_MS,
    retryWaitsMs: List<Long> = SUBTITLE_RETRY_WAITS_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
    onTrackDone: (done: Int) -> Unit = {},
): SubtitleFetchResult {
    val files = mutableListOf<Pair<SubtitleTrack, Path>>()
    val refused = mutableListOf<SubtitleTrack>()
    val failed = mutableListOf<SubtitleTrack>()
    tracks.forEachIndexed { index, track ->
        if (index > 0) sleep(pauseMs)
        var answer = safeFetch(fetch, track)
        var attempt = 0
        while (answer == SubtitleAnswer.TooManyRequests && attempt < retryWaitsMs.size) {
            sleep(retryWaitsMs[attempt])
            attempt++
            answer = safeFetch(fetch, track)
        }
        when (answer) {
            is SubtitleAnswer.Text -> {
                val saved = runCatching { saveAs(track, answer.body, tempDir, format, convert) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                if (saved != null) files.add(track to saved) else failed.add(track)
            }
            SubtitleAnswer.TooManyRequests -> refused.add(track)
            SubtitleAnswer.Failed -> failed.add(track)
        }
        onTrackDone(index + 1)
    }
    return SubtitleFetchResult(files, refused, failed)
}

private suspend fun safeFetch(fetch: suspend (SubtitleTrack) -> SubtitleAnswer, track: SubtitleTrack): SubtitleAnswer =
    try {
        fetch(track)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SubtitleAnswer.Failed
    }

private suspend fun saveAs(
    track: SubtitleTrack,
    body: String,
    tempDir: Path,
    format: String,
    convert: suspend (Path, Path) -> Boolean,
): Path? {
    if (body.isBlank()) return null
    val rawExt = track.ext.ifBlank { "vtt" }.lowercase()
    val key = subtitleFileKey(track)
    val rawPath = tempDir / "$key.$rawExt"
    AppFileSystem.write(rawPath) { writeUtf8(body) }
    val wanted = format.lowercase().ifBlank { "srt" }
    if (rawExt == wanted) return rawPath
    val target = tempDir / "$key.$wanted"
    val ok = convert(rawPath, target)
    return if (ok && AppFileSystem.exists(target)) target else rawPath
}

fun subtitleFileKey(track: SubtitleTrack): String =
    track.langCode.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "sub" } +
            if (track.isAuto) ".auto" else ""

fun subtitleSideFileName(videoName: String, track: SubtitleTrack, ext: String = "srt"): String =
    "${videoName.substringBeforeLast('.', videoName)}.${subtitleFileKey(track)}.$ext"

private val iso639_1to2 = mapOf(
    "en" to "eng", "de" to "ger", "ar" to "ara", "fr" to "fre", "es" to "spa",
    "pt" to "por", "it" to "ita", "ru" to "rus", "tr" to "tur", "id" to "ind",
    "zh" to "chi", "ja" to "jpn", "ko" to "kor", "hi" to "hin", "fa" to "per",
    "nl" to "dut", "pl" to "pol", "sv" to "swe", "no" to "nor", "da" to "dan",
    "fi" to "fin", "cs" to "cze", "el" to "gre", "he" to "heb", "th" to "tha",
    "vi" to "vie", "uk" to "ukr", "ro" to "rum", "hu" to "hun", "bg" to "bul",
    "hr" to "hrv", "sr" to "srp", "sk" to "slo", "ms" to "may", "bn" to "ben",
    "ta" to "tam", "te" to "tel", "ur" to "urd", "fil" to "fil", "ca" to "cat"
)

fun iso639_2(langCode: String): String {
    val base = langCode.substringBefore('-').lowercase()
    return iso639_1to2[base] ?: base.take(3).ifBlank { "und" }
}

suspend fun countSubtitleStreams(video: String, max: Int = 32): Int {
    for (k in 0 until max) {
        val has = runCatching {
            runLocalFfmpeg(
                listOf("-nostdin", "-v", "error", "-i", video, "-map", "0:s:$k", "-c", "copy", "-f", "null", "-")
            )
        }.getOrDefault(false)
        if (!has) return k
    }
    return max
}

fun subtitleArgs(
    video: String,
    files: List<Pair<SubtitleTrack, String>>,
    existing: Int,
    output: String,
): List<String> {
    val ext = video.substringAfterLast('.', "mp4").lowercase()
    val codec = when (ext) {
        "webm" -> "webvtt"
        "mkv" -> "srt"
        else -> "mov_text"
    }
    return buildList {
        addAll(listOf("-y", "-nostdin", "-i", video))
        files.forEach { (_, p) -> add("-i"); add(p) }
        add("-map"); add("0")
        files.indices.forEach { i -> add("-map"); add("${i + 1}") }
        add("-c"); add("copy")
        files.forEachIndexed { i, (track, _) ->
            val n = existing + i
            add("-c:s:$n"); add(codec)
            add("-metadata:s:s:$n"); add("language=${iso639_2(track.langCode)}")
            add("-metadata:s:s:$n"); add("title=${track.langName}")
        }
        add(output)
    }
}

suspend fun embedSubtitleFiles(
    videoPath: Path,
    files: List<Pair<SubtitleTrack, Path>>,
    tempDir: Path,
): Boolean {
    val ext = videoPath.name.substringAfterLast('.', "").lowercase()
    if (ext !in SUBTITLE_VIDEO_EXTENSIONS || files.isEmpty()) return false
    val existing = countSubtitleStreams(videoPath.toString())
    val muxed = tempDir / "muxed.$ext"
    val ok = runCatching {
        runLocalFfmpeg(subtitleArgs(videoPath.toString(), files.map { it.first to it.second.toString() }, existing, muxed.toString()))
    }.getOrDefault(false)
    val size = if (ok) AppFileSystem.metadataOrNull(muxed)?.size ?: 0L else 0L
    if (size <= 1024L) return false
    return runCatching {
        AppFileSystem.delete(videoPath)
        AppFileSystem.copy(muxed, videoPath)
        true
    }.getOrDefault(false)
}

fun placeSubtitleFilesBeside(videoPath: Path, files: List<Pair<SubtitleTrack, Path>>): List<SubtitleTrack> {
    val dir = videoPath.parent ?: return emptyList()
    val placed = mutableListOf<SubtitleTrack>()
    for ((track, p) in files) {
        runCatching {
            val ext = p.name.substringAfterLast('.', "srt").lowercase().takeIf { it == "vtt" } ?: "srt"
            val out = dir / subtitleSideFileName(videoPath.name, track, ext)
            if (AppFileSystem.exists(out)) AppFileSystem.delete(out)
            AppFileSystem.copy(p, out)
            placed.add(track)
        }
    }
    return placed
}

fun subtitleTempDir(destDir: String, key: String): Path = "${destDir.trimEnd('/', '\\')}/.fjsubs_$key".toPath()

fun com.michelle.raouf.fetch_jet.domain.model.DownloadTask.canAddSubtitles(): Boolean =
    (engineType == com.michelle.raouf.fetch_jet.domain.model.EngineType.MEDIA_STREAM ||
            (subtitlesFromPage && !originalUrl.isNullOrBlank())) &&
            status == com.michelle.raouf.fetch_jet.domain.model.DownloadStatus.COMPLETED &&
            fileName.substringAfterLast('.', "").lowercase() in SUBTITLE_VIDEO_EXTENSIONS

fun com.michelle.raouf.fetch_jet.domain.model.DownloadTask.subtitleSideFiles(): List<Path> {
    val dir = destinationPath.trimEnd('/', '\\')
    val base = fileName.substringBeforeLast('.', fileName)
    return runCatching {
        AppFileSystem.list(dir.toPath()).filter { p ->
            val n = p.name
            n.startsWith("$base.") && n.length > base.length + 5 &&
                    (n.endsWith(".srt", ignoreCase = true) || n.endsWith(".vtt", ignoreCase = true))
        }
    }.getOrDefault(emptyList())
}

fun com.michelle.raouf.fetch_jet.domain.model.DownloadTask.subtitleKeysPresent(): Set<String> {
    val keys = subtitleTracks.filter { it.isSelected }.map { subtitleFileKey(it) }.toMutableSet()
    val base = fileName.substringBeforeLast('.', fileName)
    subtitleSideFiles().forEach { keys.add(it.name.removePrefix("$base.").dropLast(4)) }
    return keys
}
