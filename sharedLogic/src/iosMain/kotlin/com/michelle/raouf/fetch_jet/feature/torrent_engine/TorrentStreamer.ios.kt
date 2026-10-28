package com.michelle.raouf.fetch_jet.feature.torrent_engine

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.FileSystem
import okio.Path
import okio.buffer

actual suspend fun streamUrlToFile(
    urlStr: String,
    targetPath: Path,
    userAgent: String,
    speedLimitProvider: () -> Long,
    startByte: Long,
    onProgress: (downloaded: Long, speed: Long) -> Unit
): Result<Unit> {
    return try {
        val safeUrlStr = urlStr.replace(" ", "%20")
        val client = HttpClient()
        val response = client.get(safeUrlStr) {
            header("User-Agent", userAgent)
            header("Referer", extractReferer(safeUrlStr))
            if (startByte > 0) {
                header(io.ktor.http.HttpHeaders.Range, "bytes=$startByte-")
            }
        }
        val responseCode = response.status.value
        if (responseCode !in 200..299) {
            client.close()
            return Result.failure(Exception("HTTP $responseCode: Failed to download from $urlStr"))
        }

        val channel = response.bodyAsChannel()
        val useAppending = (responseCode == 206 && startByte > 0)
        val sink = if (useAppending) {
            FileSystem.SYSTEM.appendingSink(targetPath).buffer()
        } else {
            FileSystem.SYSTEM.sink(targetPath).buffer()
        }
        val buffer = ByteArray(64 * 1024)
        var downloaded = 0L
        val startTime = kotlin.time.TimeSource.Monotonic.markNow()
        val rateLimiter =
            com.michelle.raouf.fetch_jet.core.network.RateLimiter(speedLimitProvider)
        var lastBytes = 0L
        var lastTime = startTime
        var activeSpeed = 0L

        try {
            while (!channel.isClosedForRead) {
                currentCoroutineContext().ensureActive()
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                downloaded += read
                rateLimiter.acquire(read)

                val now = kotlin.time.TimeSource.Monotonic.markNow()
                val elapsed = (now - lastTime).inWholeMilliseconds
                if (elapsed >= 150) {
                    val diff = downloaded - lastBytes
                    val instantaneous = (diff * 1000.0 / elapsed).toLong()
                    activeSpeed = if (activeSpeed > 0) {
                        (activeSpeed * 0.4 + instantaneous * 0.6).toLong()
                    } else {
                        instantaneous
                    }
                    onProgress(downloaded, activeSpeed)
                    lastBytes = downloaded
                    lastTime = now
                }
            }
            sink.flush()
        } finally {
            sink.close()
            client.close()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }
}