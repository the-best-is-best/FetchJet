package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import kotlin.concurrent.Volatile

expect suspend fun resolvePlatformStream(url: String): String?
expect suspend fun inspectPlatformQualities(url: String): List<MediaQualityEntry>

/**
 * The last "ERROR:" line yt-dlp printed, per output folder (one task = one folder), so a failed
 * download can say *why* instead of "the media tool reported a failure".
 */
object MediaToolErrors {
    // Replaced as a whole on every write (no common-code lock needed; a lost line between two
    // tasks failing in the same instant is harmless).
    @Volatile
    private var byDir: Map<String, String> = emptyMap()
    fun clear(dir: String) {
        byDir = byDir - dir
    }

    fun record(dir: String, line: String) {
        val t = line.trim()
        if (t.startsWith("ERROR", ignoreCase = true) || t.contains(": error:")) byDir =
            byDir + (dir to t.take(300))
    }

    fun last(dir: String): String? = byDir[dir]
}

expect suspend fun downloadMediaWithPlatformTool(
    url: String,
    destinationDir: String,
    fileName: String,
    formatId: String?,
    clipStart: String? = null,
    clipEnd: String? = null,
    /**
     * true: download the whole video at full speed and cut it locally afterwards (yt-dlp
     * --remove-chapters with time ranges, stream copy). false: --download-sections, which only
     * fetches the part but through ffmpeg on one connection that YouTube throttles.
     */
    clipByCutting: Boolean = false,
    speedLimitBytesPerSec: Long = 0L,
    speedLimitProvider: (() -> Long)? = null,
    onProgress: (Float, Long, Long, Long, String?) -> Unit,
    /** After the download: (step label, fraction 0..1 or -1 when unknown). See PostProcessTracker. */
    onPostProcess: ((String, Float) -> Unit)? = null
): Boolean