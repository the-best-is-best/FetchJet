package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.feature.http_downloader.ChunkLayout.Companion.STALL_FAIL_MS
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal suspend fun HttpChunkDownloader.downloadParallelChunks(
    taskId: String,
    url: String,
    cachePath: String,
    finalDestPath: String,
    totalLength: Long
) {
    val destPath = cachePath
    val destOkioPath = destPath.toPath()
    val manifestPath = "$destPath.chunks".toPath()
    fun partPath(index: Int) = "${destPath}.part$index".toPath()
    fun partSize(index: Int): Long =
        if (FileSystem.SYSTEM.exists(partPath(index))) FileSystem.SYSTEM.metadataOrNull(
            partPath(
                index
            )
        )?.size ?: 0L else 0L

    try {
        destOkioPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
    } catch (_: Exception) {
    }

    val connections = connectionsFor(taskId, totalLength)

    // Resume the saved piece map if there is one; otherwise start with equal pieces.
    // (Parts without a map come from an older version, which always split equally.)
    val resumed = try {
        if (FileSystem.SYSTEM.exists(manifestPath))
            ChunkLayout.fromManifest(
                FileSystem.SYSTEM.read(manifestPath) { readUtf8() },
                ::partSize
            )
        else null
    } catch (_: Exception) {
        null
    }
    val initialChunks = resumed ?: run {
        val n =
            if (FileSystem.SYSTEM.exists(partPath(0))) calculateOptimalChunkCount(totalLength) else connections
        val chunkSize = totalLength / n
        (0 until n).map { i ->
            val start = i * chunkSize
            val end = if (i == n - 1) totalLength - 1 else start + chunkSize - 1
            val have = min(partSize(i), end - start + 1)
            DownloadChunk(i, start, end, have, have >= end - start + 1)
        }
    }
    val layout = ChunkLayout(initialChunks)
    try {
        FileSystem.SYSTEM.write(manifestPath) { writeUtf8(layout.manifest()) }
    } catch (_: Exception) {
    }

    val initialTotal = layout.total()
    updateTaskState(taskId) {
        it.copy(
            status = DownloadStatus.DOWNLOADING,
            totalBytes = totalLength,
            downloadedBytes = initialTotal,
            progress = if (totalLength > 0) initialTotal.toFloat() / totalLength.toFloat() else 0f,
            chunks = initialChunks,
            connections = connections
        )
    }

    val timeSource = TimeSource.Monotonic
    val startMark = timeSource.markNow()
    val speedometer = Speedometer(windowDurationMs = 1200L)

    val tickerJob = scope.launch {
        while (isActive) {
            delay(200)
            val nowMs = startMark.elapsedNow().inWholeMilliseconds
            val currentTotal = layout.total()
            val speed = speedometer.recordSample(nowMs, currentTotal)
            val prog =
                if (totalLength > 0) currentTotal.toFloat() / totalLength.toFloat() else 0f
            val currentChunks = layout.snapshot()
            updateTaskState(taskId) { t ->
                t.copy(
                    downloadedBytes = currentTotal,
                    progress = prog,
                    downloadSpeedBytesPerSec = speed,
                    chunks = currentChunks
                )
            }
            layout.manifestIfChanged()?.let { m ->
                try {
                    FileSystem.SYSTEM.write(manifestPath) { writeUtf8(m) }
                } catch (_: Exception) {
                }
            }
        }
    }

    val taskRateLimiter =
        com.michelle.raouf.fetch_jet.core.network.RateLimiter { speedLimitProvider(taskId) }

    // Children of the task coroutine: pause()/cancel() on the task job cancels every
    // worker. With scope.async they outlived the task and a resume appended to the same
    // .partN files while the old workers were still writing -> corrupted parts.
    coroutineScope {
        layout.lastProgressMs = layout.nowMs()
        val workers = (0 until connections).map {
            async {
                var backoff = 1_000L
                while (true) {
                    val piece = layout.next()
                    if (piece == null) {
                        // Nothing free right now: pieces waiting out a retry delay, or all done.
                        if (!layout.hasPending()) break
                        delay(300); continue
                    }
                    try {
                        downloadPiece(
                            taskId,
                            url,
                            piece,
                            partPath(piece.index),
                            taskRateLimiter,
                            layout
                        )
                        layout.release(piece)
                        backoff = 1_000L
                    } catch (e: CancellationException) {
                        layout.release(piece); throw e
                    } catch (e: RangeNotHonouredException) {
                        layout.release(piece); throw e
                    } catch (e: ServerBusyException) {
                        val stalledMs = layout.nowMs() - layout.lastProgressMs
                        if (stalledMs > STALL_FAIL_MS) throw Exception(
                            "No data for ${stalledMs / 1000}s · ${shortError(e)}"
                        )
                        layout.serverBusy(piece, shortError(e))
                        delay(ChunkLayout.BUSY_RETRY_MS.milliseconds)
                    } catch (e: Exception) {
                        // 429 / 503 / a dropped connection: the server will not take this
                        // connection right now. Hand the piece back, wait, try again; the
                        // other connections keep going. Only a download that gets no data at
                        // all for a long time fails.
                        val stalledMs = layout.nowMs() - layout.lastProgressMs
                        if (stalledMs > STALL_FAIL_MS) throw Exception(
                            "No data for ${stalledMs / 1000}s · ${
                                shortError(
                                    e
                                )
                            }"
                        )
                        layout.fail(piece, shortError(e), backoff)
                        delay(backoff.milliseconds)
                        backoff = (backoff * 2).coerceAtMost(20_000L)
                    }
                }
            }
        }
        try {
            workers.awaitAll()
        } finally {
            tickerJob.cancel()
        }
    }

    // Assemble the pieces in byte order (a part may hold a few bytes past its piece when
    // the piece was split while it was writing: copy exactly the piece length).
    val finalPieces = layout.ordered()
    val finalSink = FileSystem.SYSTEM.sink(destOkioPath).buffer()
    try {
        for (piece in finalPieces) {
            val pp = partPath(piece.index)
            if (!FileSystem.SYSTEM.exists(pp)) throw Exception("Piece ${piece.index} is missing")
            val src = FileSystem.SYSTEM.source(pp).buffer()
            try {
                var left = piece.length
                val buf = ByteArray(256 * 1024)
                while (left > 0) {
                    // Honour cancel()/pause() mid-merge: a big file (e.g. a handed-over 100%
                    // file) must not block Delete while we copy it piece by piece.
                    currentCoroutineContext().ensureActive()
                    val n = src.read(buf, 0, min(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    finalSink.write(buf, 0, n)
                    left -= n
                }
            } finally {
                src.close()
            }
        }
        finalSink.flush()
    } finally {
        finalSink.close()
    }
    for (piece in finalPieces) try {
        FileSystem.SYSTEM.delete(partPath(piece.index))
    } catch (_: Exception) {
    }
    try {
        FileSystem.SYSTEM.delete(manifestPath)
    } catch (_: Exception) {
    }

    // Never report a short file as finished.
    val assembledSize = FileSystem.SYSTEM.metadataOrNull(destOkioPath)?.size ?: 0L
    if (totalLength > 0 && assembledSize != totalLength) {
        throw Exception("Assembled file is $assembledSize bytes, expected $totalLength (a piece is missing)")
    }

    // Copy assembled file from cache to final destination
    val savedPath = copyIntoPlace(destOkioPath, finalDestPath.toPath())
    destOkioPath.parent?.let { deleteRecursively(it) }

    val doneChunks = layout.snapshot().map {
        it.copy(
            isCompleted = true,
            isActive = false,
            downloadedBytes = it.endByte - it.startByte + 1,
            speedBytesPerSec = 0L
        )
    }
    updateTaskState(taskId) {
        it.copy(
            fileName = savedPath.name,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = totalLength,
            totalBytes = totalLength,
            progress = 1.0f,
            downloadSpeedBytesPerSec = 0L,
            chunks = doneChunks
        )
    }
}

/** Downloads one piece into its part file, stopping at the piece's (possibly shrinking) end. */
internal suspend fun HttpChunkDownloader.downloadPiece(
    taskId: String,
    url: String,
    piece: ChunkLayout.Piece,
    partPath: okio.Path,
    rateLimiter: com.michelle.raouf.fetch_jet.core.network.RateLimiter,
    layout: ChunkLayout
) {
    run {
        val existing =
            if (FileSystem.SYSTEM.exists(partPath)) FileSystem.SYSTEM.metadataOrNull(partPath)?.size
                ?: 0L else 0L
        // The part file is the truth for how much of the piece is on disk.
        piece.downloaded = min(existing, piece.length)
        if (piece.downloaded < existing) {
            // Part is longer than the piece (split happened while writing): trim it.
            trimFile(partPath, piece.downloaded)
        }
        if (piece.isCompleted) return@run
        val from = piece.start + piece.downloaded
        val to = piece.end

        client.prepareGet(url) {
            applyDownloadHeaders(url, taskHeaders(taskId, url))
            header(HttpHeaders.Range, "bytes=$from-$to")
        }.execute { response ->
            // A server that ignores Range answers 200 with the WHOLE file; appending
            // that to a part would corrupt it. Ranged requests must get 206.
            if (response.status.value == 200) {
                throw RangeNotHonouredException("Piece ${piece.index}: server answered 200 to a Range request (no ranges)")
            }
            if (response.status.value == 429 || response.status.value == 503) {
                throw ServerBusyException("Piece ${piece.index}: HTTP ${response.status.value}")
            }
            if (response.status.value != 206) {
                // 416 / 429 / 5xx: a transient problem, worth the retries.
                throw Exception("Piece ${piece.index}: HTTP ${response.status.value}")
            }
            val channel: ByteReadChannel = response.bodyAsChannel()
            val sink = FileSystem.SYSTEM.appendingSink(partPath).buffer()
            val buffer = ByteArray(64 * 1024)
            var windowStart = TimeSource.Monotonic.markNow()
            var windowBytes = 0L
            try {
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    // The end may have moved closer (another worker took the tail of this piece).
                    val allowed = piece.length - piece.downloaded
                    if (allowed <= 0) break
                    val n = min(read.toLong(), allowed).toInt()
                    sink.write(buffer, 0, n)
                    piece.downloaded += n
                    piece.lastError = null
                    layout.lastProgressMs = layout.nowMs()
                    windowBytes += n
                    val elapsed = windowStart.elapsedNow().inWholeMilliseconds
                    if (elapsed >= 1000) {
                        piece.speedBytesPerSec = windowBytes * 1000 / elapsed
                        windowStart = TimeSource.Monotonic.markNow(); windowBytes = 0
                    }
                    rateLimiter.acquire(n)
                    if (piece.isCompleted) break
                }
                sink.flush()
            } finally {
                sink.close()
            }
        }
    }
}

/** Connections for this task: its own setting, else the app setting, else by file size. */
internal fun HttpChunkDownloader.connectionsFor(taskId: String, totalLength: Long): Int {
    val task = tasksState.value[taskId]
    val wanted = task?.maxConnections?.takeIf { it > 0 }
        ?: com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.connections.value.takeIf { it > 0 }
        ?: calculateOptimalChunkCount(totalLength)
    // No point in more pieces than 512 KB each.
    val byPieceSize = (totalLength / (512L * 1024)).toInt().coerceAtLeast(1)
    return wanted.coerceIn(1, ChunkLayout.MAX_CONNECTIONS).coerceAtMost(byPieceSize)
}

internal suspend fun HttpChunkDownloader.downloadSingleStream(
    taskId: String,
    url: String,
    cachePath: String,
    finalDestPath: String,
    totalLength: Long
) {
    val destPath = "$cachePath.part"
    val targetOkioPath = destPath.toPath()
    try {
        targetOkioPath.parent?.let { parentDir ->
            FileSystem.SYSTEM.createDirectories(parentDir)
        }
    } catch (_: Exception) {
    }

    var initialExisting = 0L
    if (FileSystem.SYSTEM.exists(targetOkioPath)) {
        initialExisting = FileSystem.SYSTEM.metadataOrNull(targetOkioPath)?.size ?: 0L
    }
    val initialStartByte =
        if (initialExisting > 0 && totalLength > 0 && initialExisting < totalLength) initialExisting else 0L

    updateTaskState(taskId) {
        it.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = initialStartByte,
            totalBytes = if (totalLength > 0) totalLength else it.totalBytes
        )
    }

    fetchWithRetry {
        var existingBytes = 0L
        if (FileSystem.SYSTEM.exists(targetOkioPath)) {
            existingBytes = FileSystem.SYSTEM.metadataOrNull(targetOkioPath)?.size ?: 0L
        }
        val startByte =
            if (existingBytes > 0 && totalLength > 0 && existingBytes < totalLength) existingBytes else 0L

        client.prepareGet(url) {
            applyDownloadHeaders(url, taskHeaders(taskId, url))
            if (startByte > 0) {
                header(HttpHeaders.Range, "bytes=$startByte-")
            }
        }.execute { response ->
            if (!response.status.isSuccess() && response.status.value != 206) {
                throw Exception("HTTP Error ${response.status.value}")
            }
            // Resume asked for a Range but the server sent the whole file (200):
            // start over from byte 0 instead of appending a second copy.
            val resumed = startByte > 0 && response.status.value == 206

            val channel: ByteReadChannel = response.bodyAsChannel()
            val fileSink = if (resumed) {
                FileSystem.SYSTEM.appendingSink(targetOkioPath).buffer()
            } else {
                FileSystem.SYSTEM.sink(targetOkioPath).buffer()
            }

            val buffer = ByteArray(64 * 1024)
            var downloaded = if (resumed) startByte else 0L
            val timeSource = TimeSource.Monotonic
            val startMark = timeSource.markNow()
            val speedometer = Speedometer(windowDurationMs = 1200L)
            val rateLimiter = com.michelle.raouf.fetch_jet.core.network.RateLimiter {
                speedLimitProvider(taskId)
            }

            try {
                var lastUpdateMs = 0L
                val updateIntervalMs = 200L

                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break

                    fileSink.write(buffer, 0, read)
                    downloaded += read
                    rateLimiter.acquire(read)

                    val nowMs = startMark.elapsedNow().inWholeMilliseconds
                    if (nowMs - lastUpdateMs >= updateIntervalMs) {
                        lastUpdateMs = nowMs
                        val speed = speedometer.recordSample(nowMs, downloaded)

                        val effectiveTotal = if (totalLength > 0) totalLength else maxOf(
                            downloaded * 2,
                            50 * 1024 * 1024L
                        )
                        val prog =
                            if (effectiveTotal > 0) (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(
                                0f,
                                0.95f
                            ) else 0f

                        updateTaskState(taskId) {
                            it.copy(
                                downloadedBytes = downloaded,
                                totalBytes = if (totalLength > 0) totalLength else downloaded,
                                progress = prog,
                                downloadSpeedBytesPerSec = speed
                            )
                        }
                    }
                }
                fileSink.flush()
            } finally {
                fileSink.close()
            }

            // A connection that closed early is not a finished file.
            if (totalLength > 0 && downloaded != totalLength) {
                throw Exception("Connection closed early: got $downloaded of $totalLength bytes")
            }

            // Copy completed file from cache to final destination
            val savedPath = copyIntoPlace(targetOkioPath, finalDestPath.toPath())
            targetOkioPath.parent?.let { deleteRecursively(it) }

            updateTaskState(taskId) {
                it.copy(
                    fileName = savedPath.name,
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = if (totalLength > 0) totalLength else downloaded,
                    totalBytes = if (totalLength > 0) totalLength else downloaded,
                    progress = 1.0f,
                    downloadSpeedBytesPerSec = 0L
                )
            }
        }
    }
}
