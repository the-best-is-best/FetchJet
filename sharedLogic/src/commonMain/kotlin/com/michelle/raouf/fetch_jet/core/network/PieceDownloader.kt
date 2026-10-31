package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * Downloads one URL into one file with several connections, IDM-style: the file is cut into
 * pieces, each connection writes its piece to its own `.partN` file, a connection that
 * finishes takes half of the biggest piece still running, and the piece map is saved so a
 * later call continues where it stopped. A piece the server turns away (429 / 503) is retried
 * with a growing delay while the others keep going; a link that expired (403 / 410) is
 * renewed through [urlProvider]. Used for direct video/audio streams (YouTube & co.); the
 * plain-file engine has the same algorithm inside HttpChunkDownloader.
 */
class PieceDownloader(private val client: HttpClient) {

    class Progress(
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
        val chunks: List<DownloadChunk>,
        val connections: Int
    )

    /**
     * @param urlProvider the current URL; called with `refresh = true` after a 403/410 so the
     *   caller can resolve a fresh link (return the same URL if it cannot).
     * @param partBase path prefix for the `.partN` files and the `.chunks` map.
     * @param totalLength known size, or <= 0 to find out with a ranged request.
     */
    suspend fun download(
        urlProvider: suspend (refresh: Boolean) -> String,
        headers: Map<String, String>,
        partBase: String,
        outputFile: String,
        totalLength: Long = -1L,
        connections: Int = 8,
        rateLimiter: RateLimiter? = null,
        onProgress: (Progress) -> Unit
    ) {
        var url = urlProvider(false)
        var hdrs = headers
        // googlevideo (and other signed CDNs) sometimes answer 403 to a link that is seconds
        // old: the edge node has not seen the new signature yet. That is not a dead link — a
        // short wait, then a fresh link, almost always clears it. Only then give up.
        var probe: Probe? = null
        var attempt = 0
        while (probe == null) {
            try {
                probe = probe(url, hdrs)
            } catch (e: ExpiredLinkException) {
                attempt++
                com.michelle.raouf.fetch_jet.core.log.AppLog.e(
                    "PieceDownloader: probe ${e.message} (attempt $attempt) host=${
                        url.substringAfter(
                            "://"
                        ).substringBefore('/')
                    } " +
                            "client=${Regex("[?&]c=([^&]+)").find(url)?.groupValues?.get(1)}"
                )
                when (attempt) {
                    1, 2 -> delay(2_000L * attempt)                       // same link, a moment later
                    3 -> {
                        url = urlProvider(true); delay(1_000L)
                    }       // fresh link
                    4 -> {
                        hdrs = hdrs.filterKeys { it.equals("User-Agent", true) }; delay(3_000L)
                    }

                    5 -> {
                        url = urlProvider(true); delay(5_000L)
                    }

                    else -> throw e
                }
            }
        }
        val total = if (totalLength > 0) totalLength else probe.length
        if (total <= 0) throw Exception("The server did not say how big the stream is")

        val manifestPath = "$partBase.chunks".toPath()
        fun partPath(i: Int) = "$partBase.part$i".toPath()
        fun partSize(i: Int) = FileSystem.SYSTEM.metadataOrNull(partPath(i))?.size ?: 0L
        manifestPath.parent?.let {
            try {
                FileSystem.SYSTEM.createDirectories(it)
            } catch (_: Exception) {
            }
        }

        val wanted = connections.coerceIn(1, MAX_CONNECTIONS)
            .coerceAtMost((total / (512L * 1024)).toInt().coerceAtLeast(1))
        val n = if (probe.ranges) wanted else 1

        val resumed = try {
            if (FileSystem.SYSTEM.exists(manifestPath))
                Layout.fromManifest(FileSystem.SYSTEM.read(manifestPath) { readUtf8() }, ::partSize)
            else null
        } catch (_: Exception) {
            null
        }
        val initial = resumed ?: run {
            val size = total / n
            (0 until n).map { i ->
                val s = i * size
                val e = if (i == n - 1) total - 1 else s + size - 1
                val have = min(partSize(i), e - s + 1)
                DownloadChunk(i, s, e, have, have >= e - s + 1)
            }
        }
        val layout = Layout(initial, splittable = probe.ranges)
        try {
            FileSystem.SYSTEM.write(manifestPath) { writeUtf8(layout.manifest()) }
        } catch (_: Exception) {
        }

        val startMark = TimeSource.Monotonic.markNow()
        var lastSampleMs = 0L
        var lastSampleBytes = layout.total()
        var speed = 0L

        coroutineScope {
            val ticker = launch {
                while (isActive) {
                    delay(250)
                    val now = startMark.elapsedNow().inWholeMilliseconds
                    val got = layout.total()
                    if (now - lastSampleMs >= 1000) {
                        speed =
                            (got - lastSampleBytes) * 1000 / (now - lastSampleMs).coerceAtLeast(1)
                        lastSampleMs = now; lastSampleBytes = got
                    }
                    onProgress(Progress(got, total, speed, layout.snapshot(), n))
                    layout.manifestIfChanged()?.let { m ->
                        try {
                            FileSystem.SYSTEM.write(manifestPath) { writeUtf8(m) }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            val refreshLock = Mutex()
            var lastRefreshMs = -60_000L
            layout.lastProgressMs = layout.nowMs()
            val workers = (0 until n).map {
                async {
                    var backoff = 1_000L
                    while (true) {
                        val piece = layout.next()
                        if (piece == null) {
                            if (!layout.hasPending()) break
                            delay(300); continue
                        }
                        try {
                            downloadPiece(
                                url,
                                headers,
                                piece,
                                partPath(piece.index),
                                rateLimiter,
                                layout
                            )
                            layout.release(piece)
                            backoff = 1_000L
                        } catch (e: CancellationException) {
                            layout.release(piece); throw e
                        } catch (e: ExpiredLinkException) {
                            // The signed link ran out: ask for a new one (once a minute at most).
                            layout.fail(piece, "link expired", 500)
                            refreshLock.withLock {
                                if (layout.nowMs() - lastRefreshMs > 60_000) {
                                    lastRefreshMs = layout.nowMs()
                                    url = try {
                                        urlProvider(true)
                                    } catch (_: Exception) {
                                        url
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            val stalled = layout.nowMs() - layout.lastProgressMs
                            if (stalled > STALL_FAIL_MS) throw Exception(
                                "No data for ${stalled / 1000}s · ${
                                    short(
                                        e
                                    )
                                }"
                            )
                            layout.fail(piece, short(e), backoff)
                            delay(backoff)
                            backoff = (backoff * 2).coerceAtMost(20_000L)
                        }
                    }
                }
            }
            try {
                workers.awaitAll()
            } finally {
                ticker.cancel()
            }
        }

        // assemble
        val out = outputFile.toPath()
        out.parent?.let {
            try {
                FileSystem.SYSTEM.createDirectories(it)
            } catch (_: Exception) {
            }
        }
        FileSystem.SYSTEM.sink(out).buffer().use { sink ->
            for (piece in layout.ordered()) {
                val pp = partPath(piece.index)
                if (!FileSystem.SYSTEM.exists(pp)) throw Exception("Piece ${piece.index} is missing")
                FileSystem.SYSTEM.source(pp).buffer().use { src ->
                    var left = piece.length
                    val buf = ByteArray(256 * 1024)
                    while (left > 0) {
                        val r = src.read(buf, 0, min(buf.size.toLong(), left).toInt())
                        if (r <= 0) break
                        sink.write(buf, 0, r); left -= r
                    }
                }
            }
        }
        val size = FileSystem.SYSTEM.metadataOrNull(out)?.size ?: 0L
        if (size != total) throw Exception("Assembled $size bytes, expected $total")
        for (p in layout.ordered()) try {
            FileSystem.SYSTEM.delete(partPath(p.index))
        } catch (_: Exception) {
        }
        try {
            FileSystem.SYSTEM.delete(manifestPath)
        } catch (_: Exception) {
        }
        onProgress(
            Progress(
                total,
                total,
                0L,
                layout.snapshot().map { it.copy(isCompleted = true, isActive = false) },
                n
            )
        )
    }

    private class Probe(val length: Long, val ranges: Boolean)

    /** One tiny ranged request: tells the size and whether the server honours Range. */
    private suspend fun probe(url: String, headers: Map<String, String>): Probe {
        return client.prepareGet(url) {
            applyHeaders(headers)
            header(HttpHeaders.Range, "bytes=0-0")
        }.execute { r ->
            val code = r.status.value
            if (code == 403 || code == 410) {
                val body = try {
                    r.bodyAsText().take(200)
                } catch (_: Exception) {
                    ""
                }
                com.michelle.raouf.fetch_jet.core.log.AppLog.e(
                    "PieceDownloader: $code\n  asked : $url\n  sent  : ${r.request.url}\n  reqHdr: ${
                        r.request.headers.entries()
                            .joinToString { it.key + "=" + it.value.joinToString() }
                    }\n  respHdr: ${
                        r.headers.entries().joinToString { it.key + "=" + it.value.joinToString() }
                    }\n  diag  : ${rawProbeForDiagnostics(url, headers["User-Agent"] ?: UA)}"
                )
                throw ExpiredLinkException("HTTP $code", body)
            }
            if (code !in 200..299) throw Exception("HTTP $code")
            val range = r.headers["Content-Range"] // bytes 0-0/12345
            val len = range?.substringAfter('/')?.trim()?.toLongOrNull()
                ?: r.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            Probe(len, code == 206)
        }
    }

    private suspend fun downloadPiece(
        url: String,
        headers: Map<String, String>,
        piece: Layout.Piece,
        partPath: Path,
        rateLimiter: RateLimiter?,
        layout: Layout
    ) {
        val existing = FileSystem.SYSTEM.metadataOrNull(partPath)?.size ?: 0L
        piece.downloaded = min(existing, piece.length)
        if (piece.downloaded < existing) trim(partPath, piece.downloaded)
        if (piece.isCompleted) return
        val from = piece.start + piece.downloaded
        client.prepareGet(url) {
            applyHeaders(headers)
            if (layout.splittable || from > 0) header(HttpHeaders.Range, "bytes=$from-${piece.end}")
        }.execute { r ->
            val code = r.status.value
            if (code == 403 || code == 410) throw ExpiredLinkException("HTTP $code")
            if (code == 200 && (layout.splittable || from > 0)) throw Exception("HTTP 200 to a Range request")
            if (code != 206 && code != 200) throw Exception("HTTP $code")
            val ch: ByteReadChannel = r.bodyAsChannel()
            FileSystem.SYSTEM.appendingSink(partPath).buffer().use { sink ->
                val buf = ByteArray(64 * 1024)
                var winStart = TimeSource.Monotonic.markNow();
                var winBytes = 0L
                while (!ch.isClosedForRead) {
                    val read = ch.readAvailable(buf, 0, buf.size)
                    if (read <= 0) break
                    val allowed = piece.length - piece.downloaded
                    if (allowed <= 0) break
                    val w = min(read.toLong(), allowed).toInt()
                    sink.write(buf, 0, w)
                    piece.downloaded += w
                    piece.lastError = null
                    layout.lastProgressMs = layout.nowMs()
                    winBytes += w
                    val ms = winStart.elapsedNow().inWholeMilliseconds
                    if (ms >= 1000) {
                        piece.speed = winBytes * 1000 / ms; winStart =
                            TimeSource.Monotonic.markNow(); winBytes = 0
                    }
                    rateLimiter?.acquire(w)
                    if (piece.isCompleted) break
                }
                sink.flush()
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(extra: Map<String, String>) {
        headers[HttpHeaders.AcceptEncoding] = "identity"
        headers[HttpHeaders.Accept] = "*/*"
        if (extra.keys.none { it.equals("User-Agent", true) }) headers[HttpHeaders.UserAgent] = UA
        for ((k, v) in extra) {
            if (k.isBlank() || v.isBlank() || v.any { it == '\r' || it == '\n' }) continue
            if (k.equals("Accept-Encoding", true)) continue // we must get raw bytes
            headers[k] = v
        }
    }

    private fun trim(path: Path, keep: Long) {
        try {
            val tmp = "$path.trim".toPath()
            FileSystem.SYSTEM.source(path).buffer().use { src ->
                FileSystem.SYSTEM.sink(tmp).buffer().use { out ->
                    var left = keep
                    val buf = ByteArray(256 * 1024)
                    while (left > 0) {
                        val r = src.read(buf, 0, min(buf.size.toLong(), left).toInt())
                        if (r <= 0) break
                        out.write(buf, 0, r); left -= r
                    }
                }
            }
            FileSystem.SYSTEM.atomicMove(tmp, path)
        } catch (_: Exception) {
        }
    }

    private fun short(e: Exception) = (e.message ?: "error").substringBefore('\n').take(60)

    class ExpiredLinkException(msg: String, val body: String = "") : Exception(msg)

    /** The live piece map (see HttpChunkDownloader.ChunkLayout). */
    class Layout(initial: List<DownloadChunk>, val splittable: Boolean) {
        class Piece(val index: Int, val start: Long, initialEnd: Long, initialDownloaded: Long) {
            @Volatile
            var end: Long = initialEnd
            @Volatile
            var downloaded: Long = initialDownloaded
            @Volatile
            var running = false
            @Volatile
            var speed = 0L
            @Volatile
            var lastError: String? = null
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

        suspend fun total(): Long =
            mutex.withLock { pieces.sumOf { min(it.downloaded, it.length) } }

        suspend fun snapshot(): List<DownloadChunk> = mutex.withLock {
            pieces.sortedBy { it.start }.map {
                DownloadChunk(
                    it.index, it.start, it.end, min(it.downloaded, it.length), it.isCompleted,
                    isActive = it.running && !it.isCompleted,
                    speedBytesPerSec = if (it.running) it.speed else 0L,
                    note = if (!it.isCompleted && !it.running) it.lastError else null
                )
            }
        }

        suspend fun ordered(): List<Piece> = mutex.withLock { pieces.sortedBy { it.start } }
        suspend fun next(): Piece? = mutex.withLock {
            val now = nowMs()
            pieces.firstOrNull { !it.running && !it.isCompleted && it.retryAtMs <= now }
                ?.also { it.running = true }
                ?: (if (splittable) split()?.also { it.running = true } else null)
        }

        suspend fun hasPending(): Boolean =
            mutex.withLock { pieces.any { !it.running && !it.isCompleted } }

        suspend fun release(p: Piece) = mutex.withLock { p.running = false; p.speed = 0L }
        suspend fun fail(p: Piece, error: String, delayMs: Long) = mutex.withLock {
            p.running = false; p.speed = 0L; p.lastError = error; p.retryAtMs = nowMs() + delayMs
        }

        private fun split(): Piece? {
            val victim = pieces.filter { it.running && it.remaining >= MIN_SPLIT_BYTES * 2 }
                .maxByOrNull { it.remaining } ?: return null
            val pos = victim.start + victim.downloaded
            val mid = pos + victim.remaining / 2
            val p = Piece(pieces.maxOf { it.index } + 1, mid, victim.end, 0L)
            victim.end = mid - 1
            pieces.add(p); changed = true
            return p
        }

        suspend fun manifestIfChanged(): String? = mutex.withLock {
            if (!changed) null else {
                changed = false; text()
            }
        }

        suspend fun manifest(): String = mutex.withLock { text() }
        private fun text() = pieces.joinToString("\n") { "${it.index} ${it.start} ${it.end}" }

        companion object {
            fun fromManifest(m: String, partSize: (Int) -> Long): List<DownloadChunk>? =
                m.lines().mapNotNull { line ->
                    val f = line.trim().split(' ')
                    if (f.size != 3) return@mapNotNull null
                    val i = f[0].toIntOrNull() ?: return@mapNotNull null
                    val s = f[1].toLongOrNull() ?: return@mapNotNull null
                    val e = f[2].toLongOrNull() ?: return@mapNotNull null
                    val have = min(partSize(i), e - s + 1)
                    DownloadChunk(i, s, e, have, have >= e - s + 1)
                }.takeIf { it.isNotEmpty() }
        }
    }

    companion object {
        const val MAX_CONNECTIONS = 32
        const val MIN_SPLIT_BYTES = 1L * 1024 * 1024
        const val STALL_FAIL_MS = 180_000L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
