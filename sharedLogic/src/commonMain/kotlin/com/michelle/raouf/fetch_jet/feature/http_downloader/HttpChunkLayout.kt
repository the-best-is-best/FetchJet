package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * The live piece map of one download (IDM-style). Pieces are byte ranges; a worker that
 * finishes its piece takes half of the biggest piece still being downloaded ([split]), so
 * every connection stays busy until the last byte. Each piece writes its own `.partN` file;
 * the layout is saved next to them ([manifest]) so a resume continues the same pieces.
 */
internal class ChunkLayout(initial: List<DownloadChunk>) {
    class Piece(val index: Int, val start: Long, initialEnd: Long, initialDownloaded: Long) {
        @Volatile
        var end: Long = initialEnd

        @Volatile
        var downloaded: Long = initialDownloaded

        @Volatile
        var running = false

        @Volatile
        var speedBytesPerSec = 0L

        /** Last failure ("HTTP 429"); shown as "retrying" until the piece gets data again. */
        @Volatile
        var lastError: String? = null

        /** Do not retry this piece before this moment (monotonic ms). */
        @Volatile
        var retryAtMs = 0L
        val length: Long get() = end - start + 1
        val isCompleted: Boolean get() = downloaded >= length
        val remaining: Long get() = (length - downloaded).coerceAtLeast(0L)
    }

    private val mutex = Mutex()
    private val pieces = ArrayList<Piece>()
    private var changed = false
    private val clock = TimeSource.Monotonic.markNow()
    fun nowMs(): Long = clock.elapsedNow().inWholeMilliseconds

    /** When any piece last received data (monotonic ms). */
    @Volatile
    var lastProgressMs: Long = 0L

    init {
        initial.forEach {
            pieces.add(
                Piece(
                    it.chunkIndex,
                    it.startByte,
                    it.endByte,
                    it.downloadedBytes
                )
            )
        }
    }

    suspend fun total(): Long = mutex.withLock { pieces.sumOf { min(it.downloaded, it.length) } }

    suspend fun snapshot(): List<DownloadChunk> = mutex.withLock {
        pieces.sortedBy { it.start }.map {
            DownloadChunk(
                chunkIndex = it.index,
                startByte = it.start,
                endByte = it.end,
                downloadedBytes = min(it.downloaded, it.length),
                isCompleted = it.isCompleted,
                isActive = it.running && !it.isCompleted,
                speedBytesPerSec = if (it.running) it.speedBytesPerSec else 0L,
                note = if (!it.isCompleted && !it.running) it.lastError else null
            )
        }
    }

    /** Ordered by position, for assembling the file. */
    suspend fun ordered(): List<Piece> = mutex.withLock { pieces.sortedBy { it.start } }

    /** Next piece for a free worker: an idle unfinished one, else half of the biggest running one. */
    suspend fun next(): Piece? = mutex.withLock {
        val now = nowMs()
        if (connectionCap != Int.MAX_VALUE && now - lastBusyMs >= CAP_RAISE_MS) {
            connectionCap++; lastBusyMs = now
        }
        if (pieces.count { it.running } >= connectionCap) return@withLock null
        pieces.firstOrNull { !it.running && !it.isCompleted && it.retryAtMs <= now }
            ?.also { it.running = true }
            ?: split()?.also { it.running = true }
    }

    /** Unfinished pieces exist that no worker holds (they are waiting out a retry delay). */
    suspend fun hasPending(): Boolean = mutex.withLock {
        pieces.any { !it.running && !it.isCompleted } ||
                (connectionCap != Int.MAX_VALUE && pieces.any { it.running })
    }

    /** Give a failed piece back, to be retried after [delayMs]. */
    suspend fun fail(piece: Piece, error: String, delayMs: Long) = mutex.withLock {
        piece.running = false; piece.speedBytesPerSec = 0L
        piece.lastError = error; piece.retryAtMs = nowMs() + delayMs
    }

    suspend fun serverBusy(piece: Piece, error: String) = mutex.withLock {
        piece.running = false; piece.speedBytesPerSec = 0L
        piece.lastError = error; piece.retryAtMs = nowMs() + BUSY_RETRY_MS
        connectionCap = pieces.count { it.running }.coerceAtLeast(1)
        lastBusyMs = nowMs()
    }

    private var connectionCap = Int.MAX_VALUE
    private var lastBusyMs = 0L

    private fun split(): Piece? {
        val victim = pieces.filter { it.running && it.remaining >= MIN_SPLIT_BYTES * 2 }
            .maxByOrNull { it.remaining } ?: return null
        val pos = victim.start + victim.downloaded
        val mid = pos + victim.remaining / 2
        val piece = Piece(pieces.maxOf { it.index } + 1, mid, victim.end, 0L)
        victim.end = mid - 1
        pieces.add(piece)
        changed = true
        return piece
    }

    suspend fun release(piece: Piece) =
        mutex.withLock { piece.running = false; piece.speedBytesPerSec = 0L }

    /** Layout as text (one `index start end` per line); null when nothing changed since the last call. */
    suspend fun manifestIfChanged(): String? = mutex.withLock {
        if (!changed) return@withLock null
        changed = false
        manifestLocked()
    }

    suspend fun manifest(): String = mutex.withLock { manifestLocked() }

    private fun manifestLocked() = pieces.joinToString("\n") { "${it.index} ${it.start} ${it.end}" }

    companion object {
        const val MIN_SPLIT_BYTES = 1L * 1024 * 1024
        const val MAX_CONNECTIONS = 32
        const val CAP_RAISE_MS = 10_000L
        const val BUSY_RETRY_MS = 1_000L

        /** Fail the download when no piece has received data for this long. */
        const val STALL_FAIL_MS = 180_000L

        /** Rebuilds a layout from [manifest] text, taking downloaded bytes from the part files. */
        fun fromManifest(manifest: String, partSize: (Int) -> Long): List<DownloadChunk>? {
            val chunks = manifest.lines().mapNotNull { line ->
                val f = line.trim().split(' ')
                if (f.size != 3) return@mapNotNull null
                val idx = f[0].toIntOrNull() ?: return@mapNotNull null
                val start = f[1].toLongOrNull() ?: return@mapNotNull null
                val end = f[2].toLongOrNull() ?: return@mapNotNull null
                val len = end - start + 1
                val have = min(partSize(idx), len)
                DownloadChunk(idx, start, end, have, have >= len)
            }
            return chunks.takeIf { it.isNotEmpty() }
        }
    }
}
