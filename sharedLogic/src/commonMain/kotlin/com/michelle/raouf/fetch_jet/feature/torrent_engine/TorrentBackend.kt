package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry

/** Metadata of a torrent (from a .torrent file or fetched for a magnet link). */
data class TorrentMetadata(
    val name: String,
    val files: List<TorrentFileEntry>,
    val totalSize: Long,
    val infoHash: String,
    /** Raw bencoded torrent so the download can be started without fetching again. */
    val torrentBytes: ByteArray
)

/** A snapshot of a running torrent, as reported by the native engine. */
data class TorrentProgress(
    val downloadedBytes: Long,
    val wantedBytes: Long,
    val downloadRate: Long,
    val uploadRate: Long,
    val peers: Int,
    val seeds: Int,
    val isFinished: Boolean,
    val isSeeding: Boolean,
    val isPaused: Boolean,
    val hasMetadata: Boolean,
    val fileProgress: List<Long>,
    val stateText: String,
    val error: String?
)

/**
 * The real BitTorrent engine behind [TorrentDownloader]. Implemented with libtorrent4j on
 * JVM desktop and Android; iOS reports [isSupported] = false (App Store forbids torrent
 * clients and no native library is bundled there).
 */
interface TorrentBackend {
    val isSupported: Boolean

    /** One line for the Settings screen: ready / missing / failed to load (with the reason). */
    fun engineStatus(): String

    /** "Download Tools": fetch and install whatever the engine is missing. */
    suspend fun installEngine(): Result<Unit>

    /**
     * Resolves metadata for a magnet link (DHT/peer metadata exchange, may take a while)
     * or parses the given .torrent bytes.
     */
    suspend fun fetchMetadata(magnetUri: String?, torrentBytes: ByteArray?): TorrentMetadata

    /**
     * Adds the torrent to the session and starts it. [selected] holds the indices of files to
     * download; [priorities] maps file index -> -1 (low) / 0 (normal) / 1 (high).
     */
    suspend fun add(
        taskId: String,
        torrentBytes: ByteArray,
        savePath: String,
        selected: Set<Int>,
        priorities: Map<Int, Int>,
        disableUpload: Boolean,
        downloadLimitBytesPerSec: Long,
        uploadLimitBytesPerSec: Long
    )

    fun pause(taskId: String)
    fun resume(taskId: String)

    /** Removes the torrent from the session; files on disk are kept unless [deleteFiles]. */
    fun remove(taskId: String, deleteFiles: Boolean)
    fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>)
    fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long)
    fun progress(taskId: String): TorrentProgress?
    fun shutdown()
}

expect fun createTorrentBackend(): TorrentBackend
