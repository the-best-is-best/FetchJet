package com.michelle.raouf.fetch_jet.feature.torrent_engine

import platform.Foundation.NSData

/** One file inside a torrent, as reported by the native engine. */
class IosTorrentFileInfo(
    val index: Int,
    val path: String,
    val size: Long
)

/** Metadata the native engine resolved for a magnet link or parsed from a .torrent. */
class IosTorrentMetadata(
    val name: String,
    val infoHash: String,
    val totalSize: Long,
    val files: List<IosTorrentFileInfo>,
    /** Raw bencoded .torrent so the download can be started without fetching again. */
    val torrentData: NSData
)

/** Snapshot of a running torrent. Mirrors [TorrentProgress] with Swift-friendly types. */
class IosTorrentProgress(
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
 * Native BitTorrent engine for iOS, implemented in Swift on top of libtorrent
 * (`NativeTorrentBridge.swift`, using the LibTorrent-Swift wrapper). Same contract as
 * [TorrentBackend]; errors are reported through [lastError] instead of exceptions so the
 * Swift side stays simple.
 */
interface IosTorrentBridge {
    fun isAvailable(): Boolean

    /** One line for Settings: ready / why not. */
    fun statusText(): String

    /**
     * Resolves metadata for a magnet (DHT / trackers, blocking up to [timeoutSeconds]) or
     * parses [torrentData]. Returns null on failure (see [lastError]).
     */
    fun fetchMetadata(
        magnetUri: String?,
        torrentData: NSData?,
        timeoutSeconds: Int
    ): IosTorrentMetadata?

    /**
     * Adds the torrent to the session and starts it. [selected] = indices of files to
     * download; [priorityIndexes]/[priorityValues] are parallel lists: file index ->
     * -1 (low) / 0 (normal) / 1 (high). (Plain lists, not a Map, to keep the Kotlin->Swift
     * bridging trivial.) Returns false on failure (see [lastError]).
     */
    fun add(
        taskId: String,
        torrentData: NSData,
        savePath: String,
        selected: List<Int>,
        priorityIndexes: List<Int>,
        priorityValues: List<Int>,
        disableUpload: Boolean,
        downloadLimitBytesPerSec: Long,
        uploadLimitBytesPerSec: Long
    ): Boolean

    fun pause(taskId: String)
    fun resume(taskId: String)
    fun remove(taskId: String, deleteFiles: Boolean)
    fun setFileSelection(
        taskId: String,
        selected: List<Int>,
        priorityIndexes: List<Int>,
        priorityValues: List<Int>
    )

    fun setLimits(taskId: String, downloadLimitBytesPerSec: Long, uploadLimitBytesPerSec: Long)
    fun progress(taskId: String): IosTorrentProgress?
    fun lastError(): String?
    fun shutdown()
}

/** Set by the Swift app at startup (`IosTorrentBridgeRegistry.shared.bridge = ...`). */
object IosTorrentBridgeRegistry {
    var bridge: IosTorrentBridge? = null
}
