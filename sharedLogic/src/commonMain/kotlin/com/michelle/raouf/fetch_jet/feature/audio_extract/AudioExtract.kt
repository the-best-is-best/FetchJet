package com.michelle.raouf.fetch_jet.feature.audio_extract

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.media_extractor.runLocalFfmpeg
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath

class AudioExtractNoAudioException : Exception("no audio stream")

data class ExtractedAudio(val path: String, val durationMs: Long, val codec: String)

data class AudioTarget(val extension: String, val muxer: String)

object AudioExtract {
    private val MKA_MUXERS = listOf("matroska", "matroska_audio")

    private val TARGETS: Map<String, AudioTarget> = mapOf(
        "aac" to AudioTarget("m4a", "ipod"),
        "alac" to AudioTarget("m4a", "ipod"),
        "mp3" to AudioTarget("mp3", "mp3"),
        "opus" to AudioTarget("opus", "opus"),
        "vorbis" to AudioTarget("ogg", "ogg"),
        "flac" to AudioTarget("flac", "flac"),
        "ac3" to AudioTarget("ac3", "ac3"),
        "eac3" to AudioTarget("eac3", "eac3"),
        "pcm" to AudioTarget("wav", "wav")
    )

    val FALLBACK = AudioTarget("mka", "matroska")

    fun canExtract(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
                !task.hidden &&
                task.engineType != EngineType.BITTORRENT &&
                task.postProcessLabel == null &&
                !VideoCut.isClip(task) &&
                MediaPlayback.isVideoName(task.fileName)

    fun targetFor(codec: String): AudioTarget = TARGETS[codec.lowercase()] ?: FALLBACK

    fun extensionFor(codec: String): String = targetFor(codec).extension

    fun codecOfMatroskaId(codecId: String): String {
        val id = codecId.uppercase()
        return when {
            id.startsWith("A_AAC") -> "aac"
            id == "A_MPEG/L3" -> "mp3"
            id.startsWith("A_MPEG/") -> "mp2"
            id == "A_OPUS" -> "opus"
            id == "A_VORBIS" -> "vorbis"
            id == "A_FLAC" -> "flac"
            id == "A_ALAC" -> "alac"
            id == "A_AC3" -> "ac3"
            id == "A_EAC3" -> "eac3"
            id == "A_PCM/INT/LIT" || id == "A_PCM/FLOAT/IEEE" -> "pcm"
            id.startsWith("A_PCM") -> "pcm_be"
            id.startsWith("A_DTS") -> "dts"
            id.startsWith("A_TRUEHD") -> "truehd"
            else -> id.removePrefix("A_").lowercase()
        }
    }

    fun matroskaAudioCodecId(bytes: ByteArray): String? {
        var i = 0
        while (i < bytes.size - 3) {
            if (bytes[i] == 0x86.toByte()) {
                val size = bytes[i + 1].toInt() and 0xFF
                val length = size and 0x7F
                if (size and 0x80 != 0 && length in 3..40 && i + 2 + length <= bytes.size &&
                    bytes[i + 2] == 'A'.code.toByte() && bytes[i + 3] == '_'.code.toByte()
                ) {
                    val text = bytes.copyOfRange(i + 2, i + 2 + length).decodeToString().trimEnd('\u0000')
                    if (text.all { it.code in 0x21..0x7E }) return text
                }
            }
            i++
        }
        return null
    }

    fun outputName(sourceName: String, extension: String): String {
        val base = (if (sourceName.contains('.')) sourceName.substringBeforeLast('.') else sourceName).trim().ifBlank { "audio" }
        return "$base (audio).$extension"
    }

    fun extractArgs(input: String, output: String, codec: String, target: AudioTarget, progress: String? = null): List<String> = buildList {
        addAll(listOf("-y", "-nostdin", "-v", "error"))
        if (progress != null) addAll(listOf("-progress", progress))
        addAll(listOf("-i", input, "-vn", "-sn", "-dn", "-map", "0:a:0", "-c:a", "copy"))
        if (target.muxer == "ipod" || target.muxer == "mp4") {
            if (codec == "aac") addAll(listOf("-bsf:a", "aac_adtstoasc"))
            addAll(listOf("-movflags", "+faststart"))
        }
        addAll(listOf("-f", target.muxer, output))
    }

    suspend fun probeCodec(input: String): String? {
        val probe = tempFile("probe.mka")
        try {
            for (muxer in MKA_MUXERS) {
                val ok = runCatching {
                    runLocalFfmpeg(
                        listOf(
                            "-y", "-nostdin", "-v", "error", "-t", "1", "-i", input,
                            "-vn", "-sn", "-dn", "-map", "0:a:0", "-c:a", "copy", "-f", muxer, probe.toString()
                        )
                    )
                }.getOrDefault(false)
                if (!ok) continue
                val bytes = runCatching { AppFileSystem.read(probe) { readByteArray() } }.getOrNull() ?: continue
                val id = matroskaAudioCodecId(bytes) ?: continue
                return codecOfMatroskaId(id)
            }
            return null
        } finally {
            runCatching { AppFileSystem.delete(probe) }
        }
    }

    private suspend fun hasReadableVideo(input: String): Boolean {
        val probe = tempFile("video.mkv")
        try {
            return MKA_MUXERS.any { muxer ->
                runCatching {
                    runLocalFfmpeg(
                        listOf(
                            "-y", "-nostdin", "-v", "error", "-i", input,
                            "-map", "0:v:0", "-c", "copy", "-frames:v", "1", "-f", muxer, probe.toString()
                        )
                    )
                }.getOrDefault(false)
            }
        } finally {
            runCatching { AppFileSystem.delete(probe) }
        }
    }

    suspend fun extract(input: String, folder: String, sourceName: String): String? =
        extractDetailed(input, folder, sourceName)?.path

    suspend fun extractDetailed(input: String, folder: String, sourceName: String): ExtractedAudio? {
        val codec = probeCodec(input)
        if (codec == null) {
            if (hasReadableVideo(input)) throw AudioExtractNoAudioException()
            return null
        }
        val preferred = targetFor(codec)
        val targets = buildList {
            add(preferred)
            if (preferred != FALLBACK) add(FALLBACK)
            add(AudioTarget("mka", "matroska_audio"))
        }
        for (target in targets) {
            val output = AudioCompress.freePath(folder.trimEnd('/', '\\'), outputName(sourceName, target.extension))
            val duration = runOnce(input, output, codec, target) ?: continue
            return ExtractedAudio(output, duration, codec)
        }
        return null
    }

    private suspend fun runOnce(input: String, output: String, codec: String, target: AudioTarget): Long? {
        val progress = tempFile("progress")
        val partial = output.substringBeforeLast('.') + ".part." + target.extension
        var ok = false
        try {
            ok = runLocalFfmpeg(extractArgs(input, partial, codec, target, progress.toString()), EXTRACT_TIMEOUT_MS)
            val made = partial.toPath()
            ok = ok && (AppFileSystem.metadataOrNull(made)?.size ?: 0L) > 0L &&
                    runCatching { AppFileSystem.atomicMove(made, output.toPath()) }.isSuccess
            return if (ok) readOutTimeMs(progress) else null
        } finally {
            runCatching { AppFileSystem.delete(progress) }
            if (!ok) withContext(NonCancellable) { VideoCut.deleteWhenFree(partial) }
        }
    }

    private const val EXTRACT_TIMEOUT_MS = 2L * 60 * 60 * 1000

    private fun tempFile(suffix: String): Path =
        "${PathResolver().getTempDirectory().trimEnd('/', '\\')}/fetchjet-audio-extract-${currentEpochMs()}-${kotlin.random.Random.nextInt(1000, 9999)}.$suffix".toPath()

    private fun readOutTimeMs(file: Path): Long {
        val text = runCatching { AppFileSystem.read(file) { readUtf8() } }.getOrNull() ?: return 0L
        val micros = text.lineSequence()
            .filter { it.startsWith("out_time_us=") || it.startsWith("out_time_ms=") }
            .mapNotNull { it.substringAfter('=').trim().toLongOrNull() }
            .lastOrNull() ?: return 0L
        return (micros / 1000L).coerceAtLeast(0L)
    }
}
