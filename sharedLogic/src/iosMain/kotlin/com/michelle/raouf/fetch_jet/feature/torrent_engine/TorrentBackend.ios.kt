@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * BitTorrent engine for iOS: delegates to the Swift [IosTorrentBridge] (libtorrent via
 * LibTorrent-Swift). The FULL edition registers that bridge at startup; the store edition
 * does not, so [isSupported] stays false and the UI hides the engine.
 *
 * Note: libtorrent runs in-process, so torrents only progress while the app is in the
 * foreground (iOS suspends sockets otherwise) - same limitation as iTorrent.
 */
private class IosTorrentBackend : TorrentBackend {

    private val bridge: IosTorrentBridge? get() = IosTorrentBridgeRegistry.bridge

    override val isSupported: Boolean get() = bridge?.isAvailable() == true

    override fun engineStatus(): String =
        bridge?.statusText() ?: "BitTorrent engine is not available in this build"

    override suspend fun installEngine(): Result<Unit> =
        if (isSupported) Result.success(Unit)
        else Result.failure(UnsupportedOperationException(engineStatus()))

    private fun requireBridge(): IosTorrentBridge =
        bridge?.takeIf { it.isAvailable() }
            ?: throw UnsupportedOperationException(engineStatus())

    private fun failure(b: IosTorrentBridge, fallback: String): Nothing =
        throw IllegalStateException(b.lastError()?.takeIf { it.isNotBlank() } ?: fallback)

    // ---- metadata -------------------------------------------------------------------------

    override suspend fun fetchMetadata(
        magnetUri: String?,
        torrentBytes: ByteArray?
    ): TorrentMetadata = withContext(Dispatchers.IO) {
        val b = requireBridge()
        if (torrentBytes == null && magnetUri.isNullOrBlank()) {
            throw IllegalArgumentException("A magnet link or a .torrent file is required")
        }
        val magnet = magnetUri?.trim()?.ifBlank { null }?.let { withPublicTrackers(it) }
        val meta = b.fetchMetadata(magnet, torrentBytes?.toNSData(), MAGNET_TIMEOUT_SECONDS)
            ?: failure(
                b,
                "Could not fetch torrent metadata for this magnet link (no peers answered within " +
                        "$MAGNET_TIMEOUT_SECONDS s). Check the link or your network."
            )
        val files = meta.files.map { f ->
            // Each segment is sanitized: a crafted torrent cannot point outside the save folder.
            val path = f.path.replace('\\', '/')
                .split('/')
                .map { sanitizePathSegment(it) }
                .filter { it.isNotBlank() }
                .joinToString("/")
            TorrentFileEntry(index = f.index, path = path, size = f.size, isSelected = true)
        }
        TorrentMetadata(
            name = sanitizePathSegment(meta.name).ifBlank { "Torrent_Download" },
            files = files,
            totalSize = meta.totalSize,
            infoHash = meta.infoHash,
            torrentBytes = torrentBytes ?: meta.torrentData.toByteArray()
        )
    }

    // ---- lifecycle ------------------------------------------------------------------------

    override suspend fun add(
        taskId: String,
        torrentBytes: ByteArray,
        savePath: String,
        selected: Set<Int>,
        priorities: Map<Int, Int>,
        disableUpload: Boolean,
        downloadLimitBytesPerSec: Long,
        uploadLimitBytesPerSec: Long
    ) = withContext(Dispatchers.IO) {
        val b = requireBridge()
        val ok = b.add(
            taskId = taskId,
            torrentData = torrentBytes.toNSData(),
            savePath = savePath,
            selected = selected.toList(),
            priorityIndexes = priorities.keys.toList(),
            priorityValues = priorities.keys.map { priorities.getValue(it) },
            disableUpload = disableUpload,
            downloadLimitBytesPerSec = downloadLimitBytesPerSec,
            uploadLimitBytesPerSec = uploadLimitBytesPerSec
        )
        if (!ok) failure(b, "libtorrent did not accept the torrent")
    }

    override fun pause(taskId: String) {
        bridge?.pause(taskId)
    }

    override fun resume(taskId: String) {
        bridge?.resume(taskId)
    }

    override fun remove(taskId: String, deleteFiles: Boolean) {
        bridge?.remove(taskId, deleteFiles)
    }

    override fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>) {
        bridge?.setFileSelection(
            taskId,
            selected.toList(),
            priorities.keys.toList(),
            priorities.keys.map { priorities.getValue(it) }
        )
    }

    override fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long) {
        bridge?.setLimits(taskId, downloadBytesPerSec, uploadBytesPerSec)
    }

    override fun progress(taskId: String): TorrentProgress? {
        val p = bridge?.progress(taskId) ?: return null
        return TorrentProgress(
            downloadedBytes = p.downloadedBytes,
            wantedBytes = p.wantedBytes,
            downloadRate = p.downloadRate,
            uploadRate = p.uploadRate,
            peers = p.peers,
            seeds = p.seeds,
            isFinished = p.isFinished,
            isSeeding = p.isSeeding,
            isPaused = p.isPaused,
            hasMetadata = p.hasMetadata,
            fileProgress = p.fileProgress,
            stateText = p.stateText,
            error = p.error
        )
    }

    override fun shutdown() {
        bridge?.shutdown()
    }

    /**
     * Magnets copied from a browser often carry no trackers; metadata then depends on DHT
     * alone, which can take minutes. Adding a few public trackers makes it seconds.
     */
    private fun withPublicTrackers(magnet: String): String {
        if (magnet.contains("&tr=", ignoreCase = true) ||
            magnet.contains("?tr=", ignoreCase = true)
        ) return magnet
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce"
        )
        return magnet + trackers.joinToString("") { "&tr=" + urlEncode(it) }
    }

    private fun urlEncode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%')
                append(HEX[c shr 4])
                append(HEX[c and 0x0F])
            }
        }
    }

    private companion object {
        const val MAGNET_TIMEOUT_SECONDS = 60
        const val HEX = "0123456789ABCDEF"
    }
}

actual fun createTorrentBackend(): TorrentBackend = IosTorrentBackend()

// ---- NSData <-> ByteArray ---------------------------------------------------------------

internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    if (n == 0) return ByteArray(0)
    val out = ByteArray(n)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
