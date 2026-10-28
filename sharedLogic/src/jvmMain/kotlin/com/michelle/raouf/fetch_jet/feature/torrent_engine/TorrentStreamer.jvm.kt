package com.michelle.raouf.fetch_jet.feature.torrent_engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.FileSystem
import okio.Path
import okio.buffer
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun streamUrlToFile(
    urlStr: String,
    targetPath: Path,
    userAgent: String,
    speedLimitProvider: () -> Long,
    startByte: Long,
    onProgress: (downloaded: Long, speed: Long) -> Unit
): Result<Unit> {
    val safeUrlStr = urlStr.replace(" ", "%20")
    val url = URI(safeUrlStr).toURL()
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Referer", extractReferer(safeUrlStr))
        if (startByte > 0) {
            connection.setRequestProperty("Range", "bytes=$startByte-")
        }
        connection.instanceFollowRedirects = true
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return Result.failure(Exception("HTTP $responseCode: Failed to download from $urlStr"))
        }

        val inputStream = connection.inputStream
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
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = inputStream.read(buffer)
                if (read == -1) break
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
            try {
                inputStream.close()
            } catch (_: Exception) {
            }
            connection.disconnect()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }
}