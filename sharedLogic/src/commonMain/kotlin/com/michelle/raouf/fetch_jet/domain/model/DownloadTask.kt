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
    val approximateTotalBytes: Long = 0L,
    /**
     * Request headers the browser extension captured for this link (Cookie, Referer,
     * User-Agent). Sent with every HTTP request of the task so logged-in / hot-linked
     * downloads work exactly as they did in the browser.
     */
    val requestHeaders: Map<String, String> = emptyMap()
)