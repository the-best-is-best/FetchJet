package com.michelle.raouf.fetch_jet.domain.model

enum class DownloadStatus {
    IDLE,
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class SpeedLimit(
    val maxDownloadBytesPerSec: Long = 0L,
    val maxUploadBytesPerSec: Long = 0L
)

data class DownloadChunk(
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val isCompleted: Boolean = false
)

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val size: Long,
    val isSelected: Boolean = true,
    val downloadedBytes: Long = 0L,
    val isCompleted: Boolean = false,
    val priority: Int = 0 // 0: Normal, 1: High, -1: Low
)

data class DownloadTask(
    val id: String,
    val url: String,
    val fileName: String,
    val destinationPath: String,
    val engineType: EngineType,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val disableUpload: Boolean = false,
    val progress: Float = 0f,
    val chunks: List<DownloadChunk> = emptyList(),
    val priority: Int = 0,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long = 0L,
    val torrentFiles: List<TorrentFileEntry> = emptyList(),
    val webSeedUrls: List<String> = emptyList(),
    val maxDownloadSpeedBytesPerSec: Long = 0L,
    val maxUploadSpeedBytesPerSec: Long = 0L,
    val formatId: String? = null,
    val originalUrl: String? = null,
    val currentStepText: String? = null,
    val clipStart: String? = null,
    val clipEnd: String? = null,
    /** Clip mode chosen when the task was added: full-speed download + local cut (see enqueueTask). */
    val clipByCutting: Boolean = false,
    val approximateTotalBytes: Long = 0L,
    /**
     * Request headers the browser extension captured for this link (Cookie, Referer,
     * User-Agent). Sent with every HTTP request of the task so logged-in / hot-linked
     * downloads work exactly as they did in the browser.
     */
    val requestHeaders: Map<String, String> = emptyMap(),
    /**
     * Second progress line for the work after the download (merging video + audio, MP3
     * conversion, moving the file into place). null = no such step running.
     */
    val postProcessLabel: String? = null,
    /** 0..1, or -1 while the step runs but its length is unknown. */
    val postProcessProgress: Float = -1f,
    val postProcessStartedAtMs: Long = 0L,
    /** HTTP only: whether the server accepts byte ranges (null = not connected yet). */
    val serverSupportsResume: Boolean? = null
)

/**
 * Whether Pause keeps what was downloaded (true), throws it away so Resume starts from zero
 * (false), or is not known yet (null: an HTTP link before the first connection).
 */
fun DownloadTask.canResume(): Boolean? = when (engineType) {
    EngineType.BITTORRENT -> true
    // yt-dlp continues its .part files; a clip fetched as a section (ffmpeg) restarts.
    EngineType.MEDIA_STREAM -> clipByCutting || (clipStart.isNullOrBlank() && clipEnd.isNullOrBlank())
    // Needs "Accept-Ranges: bytes" and a known length, else the file is streamed again from 0.
    EngineType.HTTP_DIRECT -> serverSupportsResume
}

/** Seconds left at the current speed, null when it cannot be estimated. */
fun DownloadTask.etaSeconds(): Long? {
    if (status != DownloadStatus.DOWNLOADING || postProcessLabel != null) return null
    if (currentStepText?.let {
            it.contains(
                "Merging",
                true
            ) || it.contains("Step 3 of 3")
        } == true) return null
    val speed = downloadSpeedBytesPerSec
    if (speed <= 0L) return null
    val remaining = when {
        // A section clip has no real total: extrapolate from the share of the clip time done.
        engineType == EngineType.MEDIA_STREAM && canResume() == false ->
            if (progress in 0.02f..0.999f && downloadedBytes > 0L) (downloadedBytes * (1f - progress) / progress).toLong() else return null

        totalBytes > 0L -> (totalBytes - downloadedBytes).coerceAtLeast(0L)
        else -> return null
    }
    return remaining / speed
}