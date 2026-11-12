package com.michelle.raouf.fetch_jet.feature.audio_compress

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.runLocalFfmpeg
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath

enum class AudioCompressMode { SIMPLE, ADVANCED }

data class AudioCompressSettings(
    val mode: AudioCompressMode = AudioCompressMode.SIMPLE,
    val level: Int = AudioCompress.DEFAULT_LEVEL,
    val bitrateKbps: Int = 64,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val format: String = ""
)

data class AudioEncode(val bitrateKbps: Int, val sampleRate: Int, val channels: Int)

data class CompressedCopy(val path: String, val name: String, val bytes: Long)

object AudioCompress {
    const val DEFAULT_LEVEL = 3
    const val OUTPUT_EXTENSION = "m4a"

    val FORMATS: List<String> = listOf("m4a", "mp3", "opus", "ogg", "flac", "wav")

    private val LOSSY: Set<String> = setOf("m4a", "mp3", "opus", "ogg")

    private val LOSSLESS: Set<String> = setOf("flac", "wav")

    private val encodable = mutableMapOf(OUTPUT_EXTENSION to true)

    fun defaultFormat(sourceName: String): String =
        sourceName.substringAfterLast('.', "").lowercase().takeIf { it in LOSSY } ?: OUTPUT_EXTENSION

    fun formatFor(settings: AudioCompressSettings, sourceName: String, available: List<String>): String =
        settings.format.takeIf { it in available }
            ?: defaultFormat(sourceName).takeIf { it in available }
            ?: OUTPUT_EXTENSION

    private fun codecArgs(format: String, encode: AudioEncode): List<String> = buildList {
        val codec = when (format) {
            "mp3" -> "libmp3lame"
            "opus", "ogg" -> "libopus"
            "flac" -> "flac"
            "wav" -> "pcm_s16le"
            else -> "aac"
        }
        addAll(listOf("-vn", "-map", "0:a:0", "-c:a", codec))
        if (format !in LOSSLESS) addAll(listOf("-b:a", "${encode.bitrateKbps}k"))
        if (format == "opus" || format == "ogg") addAll(listOf("-vbr", "constrained"))
        if (encode.sampleRate > 0 && format != "opus" && format != "ogg") addAll(listOf("-ar", encode.sampleRate.toString()))
        if (encode.channels > 0) addAll(listOf("-ac", encode.channels.toString()))
        if (format == OUTPUT_EXTENSION) addAll(listOf("-movflags", "+faststart"))
    }

    suspend fun availableFormats(input: String): List<String> = FORMATS.filter { format ->
        encodable[format] ?: run {
            val sample = "${PathResolver().getTempDirectory().trimEnd('/', '\\')}/fetchjet-audio-${nowEpochMs()}.$format"
            val ok = runCatching {
                runLocalFfmpeg(
                    listOf("-y", "-nostdin", "-v", "error", "-t", "0.5", "-i", input) +
                            codecArgs(format, AudioEncode(64, 0, 0)) + sample
                )
            }.getOrDefault(false) && (AppFileSystem.metadataOrNull(sample.toPath())?.size ?: 0L) > 0L
            runCatching { AppFileSystem.delete(sample.toPath()) }
            encodable[format] = ok
            ok
        }
    }

    val LEVELS: List<AudioEncode> = listOf(
        AudioEncode(160, 0, 0),
        AudioEncode(128, 0, 0),
        AudioEncode(96, 0, 0),
        AudioEncode(64, 0, 0),
        AudioEncode(48, 0, 0),
        AudioEncode(32, 0, 1)
    )

    val BITRATES: List<Int> = listOf(16, 24, 32, 48, 64, 96, 128, 160, 192, 256)
    val SAMPLE_RATES: List<Int> = listOf(0, 48000, 44100, 32000, 24000, 22050, 16000)
    val CHANNELS: List<Int> = listOf(0, 1, 2)

    private val AUDIO_EXTENSIONS = setOf(
        "m4a", "mp3", "opus", "ogg", "oga", "wav", "flac", "aac", "wma", "weba", "mka", "aiff", "aif"
    )

    fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun canCompress(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
                task.engineType != EngineType.BITTORRENT &&
                isAudioFile(task.fileName)

    fun sourcePath(task: DownloadTask): String {
        val folder = task.destinationPath.trimEnd('/', '\\')
        val separator = if (folder.contains('\\')) "\\" else "/"
        return "$folder$separator${task.fileName}"
    }

    fun encodeFor(settings: AudioCompressSettings): AudioEncode {
        val chosen = if (settings.mode == AudioCompressMode.SIMPLE)
            LEVELS[(settings.level - 1).coerceIn(0, LEVELS.lastIndex)]
        else
            AudioEncode(settings.bitrateKbps, settings.sampleRate, settings.channels)
        if (chosen.sampleRate != 0) {
            val channels = if (chosen.channels == 0) 2 else chosen.channels
            val most = (6L * chosen.sampleRate * channels / 1000L).toInt()
            return chosen.copy(bitrateKbps = minOf(chosen.bitrateKbps, most))
        }
        val fitting = when {
            chosen.bitrateKbps <= 24 -> 16000
            chosen.bitrateKbps <= 32 -> 24000
            chosen.bitrateKbps <= 48 -> 32000
            else -> 0
        }
        return chosen.copy(sampleRate = fitting)
    }

    fun estimateBytes(durationMs: Long, encode: AudioEncode, format: String = OUTPUT_EXTENSION): Long {
        if (durationMs <= 0L) return 0L
        if (format in LOSSLESS) {
            val rate = if (encode.sampleRate > 0) encode.sampleRate else 44100
            val channels = if (encode.channels > 0) encode.channels else 2
            val pcm = durationMs * rate * channels * 2L / 1000L + 44L
            return if (format == "wav") pcm else pcm * 55L / 100L
        }
        val audio = durationMs * encode.bitrateKbps / 8L
        return audio + audio / 50L + 4096L
    }

    fun shrinkingBitrates(originalBytes: Long, durationMs: Long, settings: AudioCompressSettings): List<Int> {
        if (originalBytes <= 0L || durationMs <= 0L) return BITRATES
        return BITRATES.filter {
            val encode = encodeFor(settings.copy(mode = AudioCompressMode.ADVANCED, bitrateKbps = it))
            estimateBytes(durationMs, encode) < originalBytes
        }
    }

    fun firstShrinkingLevel(originalBytes: Long, durationMs: Long): Int {
        if (originalBytes <= 0L || durationMs <= 0L) return 1
        val index = LEVELS.indexOfFirst {
            estimateBytes(durationMs, encodeFor(AudioCompressSettings(level = LEVELS.indexOf(it) + 1))) < originalBytes
        }
        return if (index < 0) LEVELS.size + 1 else index + 1
    }

    fun outputName(sourceName: String, encode: AudioEncode, format: String = OUTPUT_EXTENSION): String =
        sourceName.substringBeforeLast('.') + " (${encode.bitrateKbps}k)." + format

    fun freePath(folder: String, name: String): String {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.')
        var candidate = folder.toPath() / name
        var counter = 1
        while (AppFileSystem.exists(candidate)) {
            candidate = folder.toPath() / "$base ($counter).$extension"
            counter++
        }
        return candidate.toString()
    }

    fun copyOf(path: String): CompressedCopy? {
        val file = path.toPath()
        val size = runCatching { AppFileSystem.metadataOrNull(file)?.size }.getOrNull() ?: return null
        return CompressedCopy(path, file.name, size)
    }

    fun delete(path: String): Boolean = runCatching { AppFileSystem.delete(path.toPath()); true }.getOrDefault(false)

    suspend fun durationMs(input: String): Long {
        val progress = progressFile()
        val ok = runLocalFfmpeg(
            listOf(
                "-y", "-nostdin", "-v", "error", "-progress", progress.toString(),
                "-i", input, "-vn", "-map", "0:a:0", "-c", "copy", "-f", "null", "-"
            )
        )
        val reached = readOutTimeMs(progress)
        runCatching { AppFileSystem.delete(progress) }
        return if (ok) reached else 0L
    }

    suspend fun compress(
        input: String,
        output: String,
        encode: AudioEncode,
        durationMs: Long,
        format: String = OUTPUT_EXTENSION,
        onProgress: (Float) -> Unit
    ): Boolean = coroutineScope {
        val progress = progressFile()
        val partial = output.substringBeforeLast('.') + ".part.$format"
        val args = buildList {
            addAll(listOf("-y", "-nostdin", "-v", "error", "-progress", progress.toString(), "-i", input))
            addAll(codecArgs(format, encode))
            add(partial)
        }
        val watcher: Job = launch {
            while (isActive) {
                delay(400)
                if (durationMs > 0L)
                    onProgress((readOutTimeMs(progress).toFloat() / durationMs).coerceIn(0f, 1f))
            }
        }
        val ok = try {
            runLocalFfmpeg(args)
        } finally {
            watcher.cancel()
            runCatching { AppFileSystem.delete(progress) }
        }
        val made = partial.toPath()
        if (ok && (AppFileSystem.metadataOrNull(made)?.size ?: 0L) > 0L) {
            runCatching { AppFileSystem.atomicMove(made, output.toPath()) }.isSuccess
        } else {
            runCatching { AppFileSystem.delete(made) }
            false
        }
    }

    private fun progressFile(): Path =
        "${PathResolver().getTempDirectory().trimEnd('/', '\\')}/fetchjet-audio-${nowEpochMs()}.progress".toPath()

    private fun readOutTimeMs(file: Path): Long {
        val text = runCatching { AppFileSystem.read(file) { readUtf8() } }.getOrNull() ?: return 0L
        val micros = text.lineSequence()
            .filter { it.startsWith("out_time_us=") || it.startsWith("out_time_ms=") }
            .mapNotNull { it.substringAfter('=').trim().toLongOrNull() }
            .lastOrNull() ?: return 0L
        return (micros / 1000L).coerceAtLeast(0L)
    }
}
