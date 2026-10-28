package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.math.min
import kotlin.time.TimeSource

private data class ResolvedHttpMetadata(
    val finalUrl: String,
    val statusCode: Int,
    val statusDescription: String,
    val contentType: String,
    val contentLength: Long,
    val acceptRanges: Boolean,
    val contentDisposition: String?
)

private class ChunkProgressTracker(numChunks: Int, initialBytes: List<Long>) {
    private val downloaded = initialBytes.toLongArray()
    private val mutex = Mutex()

    suspend fun update(index: Int, bytes: Long) {
        mutex.withLock {
            if (index in downloaded.indices) {
                downloaded[index] = bytes
            }
        }
    }

    suspend fun getTotal(): Long {
        return mutex.withLock { downloaded.sum() }
    }

    suspend fun getChunks(chunkSpecs: List<DownloadChunk>): List<DownloadChunk> {
        return mutex.withLock {
            chunkSpecs.map { c ->
                val dl =
                    if (c.chunkIndex in downloaded.indices) downloaded[c.chunkIndex] else c.downloadedBytes
                c.copy(
                    downloadedBytes = dl,
                    isCompleted = dl >= (c.endByte - c.startByte + 1)
                )
            }
        }
    }
}

private class Speedometer(private val windowDurationMs: Long = 1200L) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // (timeMs, totalBytes)
    private val mutex = Mutex()

    suspend fun recordSample(timeMs: Long, totalBytes: Long): Long {
        return mutex.withLock {
            samples.addLast(timeMs to totalBytes)

            while (samples.size > 2 && (timeMs - samples.first().first) > windowDurationMs) {
                samples.removeFirst()
            }

            if (samples.size < 2) return@withLock 0L

            val oldest = samples.first()
            val newest = samples.last()
            val durationMs = newest.first - oldest.first
            val bytesDelta = newest.second - oldest.second

            if (durationMs > 100L && bytesDelta >= 0L) {
                (bytesDelta * 1000L) / durationMs
            } else {
                0L
            }
        }
    }

    suspend fun reset() {
        mutex.withLock { samples.clear() }
    }
}

class HttpChunkDownloader(
    private val client: HttpClient = HttpClient {
        install(HttpRedirect) {
            checkHttpMethod = false
        }
        install(HttpTimeout) {
            requestTimeoutMillis = null
            connectTimeoutMillis = 60_000L
            socketTimeoutMillis = 60_000L
        }
    },
    private val pathResolver: PathResolver = PathResolver(),
    private val speedLimitProvider: (String) -> Long = { 0L }
) : HttpDownloadEngine {

    override val engineType: EngineType = EngineType.HTTP_DIRECT

    private val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    private val activeJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun observeTask(taskId: String): Flow<DownloadTask> {
        return MutableStateFlow(tasksState.value[taskId] ?: createEmptyTask(taskId)).asStateFlow()
    }

    override fun enqueueTask(task: DownloadTask): DownloadTask {
        tasksState.update { current -> current + (task.id to task) }
        return task
    }

    override suspend fun start(task: DownloadTask): Result<Unit> {
        return try {
            enqueueTask(task)
            // A double resume would run two writers on the same files.
            activeJobs.remove(task.id)?.cancel()
            val job = scope.launch {
                executeDownload(task.id)
            }
            activeJobs[task.id] = job
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L
            )
        }
        return Result.success(Unit)
    }

    override suspend fun resume(taskId: String): Result<Unit> {
        val currentTask =
            tasksState.value[taskId] ?: return Result.failure(Exception("Task not found"))
        return start(currentTask)
    }

    override suspend fun cancel(taskId: String): Result<Unit> {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        job?.join() // let the chunk writers close their files before we delete them
        val task = tasksState.value[taskId]
        if (task != null && task.fileName.isNotBlank()) {
            cleanupTempFiles(task)
        }
        // Whole scratch folder: .partN files, the single-stream .part, anything left behind.
        try { deleteTaskScratchDir(taskId) } catch (_: Exception) {}
        tasksState.update { current -> current - taskId }
        return Result.success(Unit)
    }

    private fun deleteTaskScratchDir(taskId: String) {
        val dir = "${pathResolver.getTempDirectory()}/task_$taskId".toPath()
        if (!FileSystem.SYSTEM.exists(dir)) return
        FileSystem.SYSTEM.listOrNull(dir)?.forEach { child ->
            try { FileSystem.SYSTEM.delete(child) } catch (_: Exception) {}
        }
        try { FileSystem.SYSTEM.delete(dir) } catch (_: Exception) {}
    }

    private suspend fun executeDownload(taskId: String) {
        val initialTask = tasksState.value[taskId] ?: return

        updateTaskState(taskId) {
            it.copy(status = DownloadStatus.CONNECTING, errorMessage = null)
        }

        try {
            val metadata = fetchWithRetry {
                resolveFinalUrlAndMetadata(initialTask.url, initialTask.requestHeaders)
            }

            if (metadata.statusCode !in 200..299) {
                val detailMsg = when (metadata.statusCode) {
                    403 -> "HTTP 403: Access Denied by Server (Protected by Akamai/WAF or Geo-block)"
                    404 -> "HTTP 404: File Not Found on Server"
                    401 -> "HTTP 401: Unauthorized (Authentication required)"
                    503 -> "HTTP 503: Service Unavailable"
                    else -> "HTTP Error ${metadata.statusCode}: ${metadata.statusDescription}"
                }
                throw Exception(detailMsg)
            }

            val contentType = metadata.contentType.lowercase()
            if (contentType.contains("text/html")) {
                throw Exception("Protected by Cloudflare/WAF Challenge. Please use 'Open in Browser' to get the direct file link.")
            }

            val contentLength = metadata.contentLength
            val acceptRanges = metadata.acceptRanges

            val rawFileName = if (initialTask.fileName.isBlank()) {
                extractFileName(
                    metadata.finalUrl,
                    metadata.contentDisposition,
                    metadata.contentType
                )
            } else {
                initialTask.fileName
            }
            val targetFileName = pathResolver.sanitizeFileName(rawFileName)

            updateTaskState(taskId) {
                it.copy(fileName = targetFileName)
            }

            val baseCacheDir = pathResolver.getTempDirectory()
            val taskCacheDir = "$baseCacheDir/task_$taskId"
            val cachePath = "$taskCacheDir/$targetFileName"
            val finalDestPath = "${initialTask.destinationPath}/$targetFileName"
            try {
                FileSystem.SYSTEM.createDirectories(taskCacheDir.toPath())
            } catch (_: Exception) {
            }

            // Enable parallel multi-connection chunked download if server supports byte ranges and file size > 2MB
            // Download to cache first, then copy to destination upon completion
            if (contentLength > 2 * 1024 * 1024 && acceptRanges) {
                try {
                    downloadParallelChunks(
                        taskId,
                        metadata.finalUrl,
                        cachePath,
                        finalDestPath,
                        contentLength
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RangeNotHonouredException) {
                    // The server cannot do ranges at all: the parts are worthless, stream it whole.
                    cleanupTempFiles(
                        initialTask.copy(
                            fileName = targetFileName,
                            destinationPath = initialTask.destinationPath
                        )
                    )
                    downloadSingleStream(
                        taskId,
                        metadata.finalUrl,
                        cachePath,
                        finalDestPath,
                        contentLength
                    )
                }
                // Any other failure (network blip after the retries, 429, disk) keeps the parts
                // already downloaded: the task shows ERROR and Resume continues from them.
                // (Before, every part of a multi-GB file was deleted and it restarted from zero.)
            } else {
                downloadSingleStream(
                    taskId,
                    metadata.finalUrl,
                    cachePath,
                    finalDestPath,
                    contentLength
                )
            }
        } catch (e: CancellationException) {
            // pause()/cancel() already set the status; do not overwrite it with ERROR
            throw e
        } catch (e: Exception) {
            updateTaskState(taskId) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Download failed",
                    downloadSpeedBytesPerSec = 0L
                )
            }
        }
    }

    private suspend fun resolveFinalUrlAndMetadata(
        initialUrl: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ResolvedHttpMetadata {
        var currentUrl = initialUrl
        var redirectCount = 0
        val maxRedirects = 10

        while (redirectCount < maxRedirects) {
            var redirectTarget: String? = null
            val metadata = client.prepareGet(currentUrl) {
                applyBrowserHeaders(currentUrl, headersFor(extraHeaders, initialUrl, currentUrl))
            }.execute { response ->
                val status = response.status.value
                if (status in 300..399) {
                    val location = response.headers[HttpHeaders.Location]
                    if (!location.isNullOrBlank()) {
                        redirectTarget = resolveUrl(currentUrl, location)
                        return@execute null
                    }
                }

                val contentType = response.headers[HttpHeaders.ContentType]?.lowercase() ?: ""
                if (contentType.contains("text/html") && redirectCount < maxRedirects) {
                    try {
                        val html = response.bodyAsText()
                        val redirectUrl = extractRedirectUrlFromHtml(html)
                        if (!redirectUrl.isNullOrBlank()) {
                            redirectTarget = resolveUrl(currentUrl, redirectUrl)
                            return@execute null
                        }
                    } catch (_: Exception) {
                    }
                }

                ResolvedHttpMetadata(
                    finalUrl = currentUrl,
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    contentType = contentType,
                    contentLength = response.contentLength() ?: -1L,
                    acceptRanges = response.headers[HttpHeaders.AcceptRanges]?.lowercase() == "bytes",
                    contentDisposition = response.headers[HttpHeaders.ContentDisposition]
                )
            }

            if (redirectTarget != null) {
                currentUrl = redirectTarget
                redirectCount++
                continue
            }

            if (metadata != null) {
                return metadata
            }
        }

        throw Exception("Too many redirects ($maxRedirects)")
    }

    private fun extractRedirectUrlFromHtml(html: String): String? {
        val metaRegex = Regex(
            """(?i)<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]+content\s*=\s*["']?[^"'>]*url\s*=\s*([^"'>\s]+)""",
            RegexOption.IGNORE_CASE
        )
        val match = metaRegex.find(html)
        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1].replace("\"", "").replace("'", "").trim()
        }
        val jsRegex = Regex("""(?i)window\.location\s*=\s*["']([^"']+)["']""")
        val jsMatch = jsRegex.find(html)
        if (jsMatch != null && jsMatch.groupValues.size > 1) {
            return jsMatch.groupValues[1].trim()
        }
        return null
    }

    private fun resolveUrl(baseUrl: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location
        }
        val scheme = baseUrl.substringBefore("://")
        val hostAndPath = baseUrl.substringAfter("://")
        val host = hostAndPath.substringBefore("/")
        return if (location.startsWith("/")) {
            "$scheme://$host$location"
        } else {
            val basePath = hostAndPath.substringBeforeLast("/", "")
            "$scheme://$host/$basePath/$location"
        }
    }

    private suspend fun downloadParallelChunks(
        taskId: String,
        url: String,
        cachePath: String,
        finalDestPath: String,
        totalLength: Long
    ) {
        val destPath = cachePath
        val numChunks = calculateOptimalChunkCount(totalLength)
        val chunkSize = totalLength / numChunks
        val destOkioPath = destPath.toPath()

        try {
            destOkioPath.parent?.let { parentDir ->
                FileSystem.SYSTEM.createDirectories(parentDir)
            }
        } catch (_: Exception) {
        }

        val chunkSpecs = mutableListOf<DownloadChunk>()
        for (i in 0 until numChunks) {
            val start = i * chunkSize
            val end = if (i == numChunks - 1) totalLength - 1 else (start + chunkSize - 1)
            val partPath = "${destPath}.part$i".toPath()
            val existingSize = if (FileSystem.SYSTEM.exists(partPath)) {
                FileSystem.SYSTEM.metadataOrNull(partPath)?.size ?: 0L
            } else 0L

            val isCompleted = existingSize >= (end - start + 1)
            chunkSpecs.add(
                DownloadChunk(
                    chunkIndex = i,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = min(existingSize, end - start + 1),
                    isCompleted = isCompleted
                )
            )
        }

        val initialTotal = chunkSpecs.sumOf { it.downloadedBytes }
        val tracker = ChunkProgressTracker(numChunks, chunkSpecs.map { it.downloadedBytes })

        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                totalBytes = totalLength,
                downloadedBytes = initialTotal,
                progress = if (totalLength > 0) initialTotal.toFloat() / totalLength.toFloat() else 0f,
                chunks = chunkSpecs
            )
        }

        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()
        val speedometer = Speedometer(windowDurationMs = 1200L)

        val tickerJob = scope.launch {
            while (isActive) {
                delay(200)
                val nowMs = startMark.elapsedNow().inWholeMilliseconds
                val currentTotal = tracker.getTotal()
                val speed = speedometer.recordSample(nowMs, currentTotal)

                val prog =
                    if (totalLength > 0) currentTotal.toFloat() / totalLength.toFloat() else 0f
                val currentChunks = tracker.getChunks(chunkSpecs)

                updateTaskState(taskId) { t ->
                    t.copy(
                        downloadedBytes = currentTotal,
                        progress = prog,
                        downloadSpeedBytesPerSec = speed,
                        chunks = currentChunks
                    )
                }
            }
        }

        val taskRateLimiter =
            com.michelle.raouf.fetch_jet.core.network.RateLimiter {
                speedLimitProvider(taskId)
            }

        // Children of the task coroutine: pause()/cancel() on the task job cancels every
        // chunk. With scope.async they outlived the task and a resume appended to the same
        // .partN files while the old workers were still writing -> corrupted parts.
        coroutineScope {
        val chunkJobs = chunkSpecs.map { chunk ->
            async {
                if (chunk.isCompleted) return@async

                val partPath = "${destPath}.part${chunk.chunkIndex}".toPath()

                fetchWithRetry {
                    val existingPartSize = if (FileSystem.SYSTEM.exists(partPath)) {
                        FileSystem.SYSTEM.metadataOrNull(partPath)?.size ?: 0L
                    } else 0L
                    var currentDownloaded =
                        min(existingPartSize, chunk.endByte - chunk.startByte + 1)
                    val currentStartByte = chunk.startByte + currentDownloaded
                    val endByte = chunk.endByte

                    if (currentStartByte > endByte) {
                        tracker.update(chunk.chunkIndex, currentDownloaded)
                        return@fetchWithRetry
                    }

                    client.prepareGet(url) {
                        applyDownloadHeaders(url, taskHeaders(taskId, url))
                        header(HttpHeaders.Range, "bytes=$currentStartByte-$endByte")
                    }.execute { response ->
                        // A server that ignores Range answers 200 with the WHOLE file; appending
                        // that to a part would corrupt it. Ranged requests must get 206.
                        if (response.status.value != 206) {
                            throw RangeNotHonouredException("Chunk ${chunk.chunkIndex} failed with HTTP ${response.status.value} (server did not honour Range)")
                        }

                        val channel: ByteReadChannel = response.bodyAsChannel()
                        val fileSink = FileSystem.SYSTEM.appendingSink(partPath).buffer()
                        val buffer = ByteArray(64 * 1024)

                        try {
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buffer, 0, buffer.size)
                                if (read <= 0) break

                                fileSink.write(buffer, 0, read)
                                currentDownloaded += read
                                tracker.update(chunk.chunkIndex, currentDownloaded)
                                taskRateLimiter.acquire(read)
                            }
                            tracker.update(chunk.chunkIndex, currentDownloaded)
                            fileSink.flush()
                        } finally {
                            fileSink.close()
                        }
                    }
                }
            }
        }

        try {
            chunkJobs.awaitAll()
        } finally {
            tickerJob.cancel()
        }
        }

        // Merge chunk parts into final target file
        val finalSink = FileSystem.SYSTEM.sink(destOkioPath).buffer()
        try {
            for (i in 0 until numChunks) {
                val partPath = "${destPath}.part$i".toPath()
                if (FileSystem.SYSTEM.exists(partPath)) {
                    val partSource = FileSystem.SYSTEM.source(partPath).buffer()
                    try {
                        finalSink.writeAll(partSource)
                    } finally {
                        partSource.close()
                    }
                    FileSystem.SYSTEM.delete(partPath)
                }
            }
            finalSink.flush()
        } finally {
            finalSink.close()
        }

        // Never report a short file as finished.
        val assembledSize = FileSystem.SYSTEM.metadataOrNull(destOkioPath)?.size ?: 0L
        if (totalLength > 0 && assembledSize != totalLength) {
            throw Exception("Assembled file is $assembledSize bytes, expected $totalLength (a chunk is missing)")
        }

        // Copy assembled file from cache to final destination
        val finalOkioPath = finalDestPath.toPath()
        try {
            finalOkioPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
        } catch (_: Exception) {
        }
        if (FileSystem.SYSTEM.exists(finalOkioPath)) {
            FileSystem.SYSTEM.delete(finalOkioPath)
        }
        FileSystem.SYSTEM.copy(destOkioPath, finalOkioPath)
        destOkioPath.parent?.let { deleteRecursively(it) }

        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = totalLength,
                totalBytes = totalLength,
                progress = 1.0f,
                downloadSpeedBytesPerSec = 0L,
                chunks = chunkSpecs.map { c ->
                    c.copy(
                        isCompleted = true,
                        downloadedBytes = c.endByte - c.startByte + 1
                    )
                }
            )
        }
    }

    private suspend fun downloadSingleStream(
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
                val finalOkioPath = finalDestPath.toPath()
                try {
                    finalOkioPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                } catch (_: Exception) {
                }
                if (FileSystem.SYSTEM.exists(finalOkioPath)) {
                    FileSystem.SYSTEM.delete(finalOkioPath)
                }
                FileSystem.SYSTEM.copy(targetOkioPath, finalOkioPath)
                targetOkioPath.parent?.let { deleteRecursively(it) }

                updateTaskState(taskId) {
                    it.copy(
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

    private fun taskHeaders(taskId: String, requestUrl: String = ""): Map<String, String> {
        val task = tasksState.value[taskId] ?: return emptyMap()
        return headersFor(task.requestHeaders, task.url, requestUrl)
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()

    /**
     * The browser's Cookie/Referer belong to the site the link came from. A redirect or chunk
     * request to another host (CDN) gets only the User-Agent, never the session cookie.
     */
    private fun headersFor(
        extra: Map<String, String>,
        originalUrl: String,
        requestUrl: String
    ): Map<String, String> {
        if (extra.isEmpty() || requestUrl.isBlank()) return extra
        val sameHost = hostOf(originalUrl) == hostOf(requestUrl)
        return if (sameHost) extra else extra.filterKeys {
            it.equals(
                "User-Agent",
                ignoreCase = true
            )
        }
    }

    /**
     * Headers captured by the browser extension win over our synthetic browser headers:
     * the site already accepted exactly these (Cookie, Referer, User-Agent) for this link.
     */
    private fun HttpRequestBuilder.applyExtraHeaders(extra: Map<String, String>) {
        for ((name, value) in extra) {
            if (name.isBlank() || value.isBlank()) continue
            if (name.any { it < ' ' } || value.any { it == '\r' || it == '\n' }) continue
            headers.remove(name)
            header(name, value)
        }
        if (extra.keys.any { it.equals("Referer", ignoreCase = true) }) {
            headers.remove("Sec-Fetch-Site")
            header("Sec-Fetch-Site", "same-origin")
        }
    }

    private fun HttpRequestBuilder.applyBrowserHeaders(
        url: String = "",
        extra: Map<String, String> = emptyMap()
    ) {
        header(HttpHeaders.UserAgent, desktopUserAgent)
        header(
            "Sec-Ch-Ua",
            "\"Google Chrome\";v=\"124\", \"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\""
        )
        header("Sec-Ch-Ua-Mobile", "?0")
        header("Sec-Ch-Ua-Platform", "\"Windows\"")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", "none")
        header("Sec-Fetch-User", "?1")
        header(
            HttpHeaders.Accept,
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        if (url.isNotBlank()) {
            val scheme = url.substringBefore("://")
            val hostAndPath = url.substringAfter("://")
            val host = hostAndPath.substringBefore("/")
            if (scheme.isNotBlank() && host.isNotBlank()) {
                header("Referer", "$scheme://$host/")
            }
        }
        applyExtraHeaders(extra)
    }

    private fun HttpRequestBuilder.applyDownloadHeaders(
        url: String = "",
        extra: Map<String, String> = emptyMap()
    ) {
        header(HttpHeaders.UserAgent, desktopUserAgent)
        header(
            "Sec-Ch-Ua",
            "\"Google Chrome\";v=\"124\", \"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\""
        )
        header("Sec-Ch-Ua-Mobile", "?0")
        header("Sec-Ch-Ua-Platform", "\"Windows\"")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", "none")
        header("Sec-Fetch-User", "?1")
        header(HttpHeaders.Accept, "*/*")
        header(HttpHeaders.AcceptEncoding, "identity")
        if (url.isNotBlank()) {
            val scheme = url.substringBefore("://")
            val hostAndPath = url.substringAfter("://")
            val host = hostAndPath.substringBefore("/")
            if (scheme.isNotBlank() && host.isNotBlank()) {
                header("Referer", "$scheme://$host/")
            }
        }
        applyExtraHeaders(extra)
    }

    private suspend fun <T> fetchWithRetry(
        maxRetries: Int = 6,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 30_000L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries - 1) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RangeNotHonouredException) {
                throw e // retrying will not make the server honour Range
            } catch (e: Exception) {
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }

    /** The server answered a ranged request with 200: ranges are not supported for this URL. */
    private class RangeNotHonouredException(message: String) : Exception(message)

    /**
     * RFC 6266: `attachment; filename="a b.pdf"; size=3` or `filename*=UTF-8''a%20b.pdf`.
     * The extended form wins when present; parameters after the name are not part of it.
     */
    private fun contentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val params = header.split(';').map { it.trim() }
        params.firstOrNull { it.startsWith("filename*=", ignoreCase = true) }?.let { p ->
            val raw = p.substringAfter('=').trim().trim('"')
            val encoded = raw.substringAfterLast("''", raw)
            val decoded = percentDecode(encoded)
            if (decoded.isNotBlank()) return decoded.substringAfterLast('/').substringAfterLast('\\')
        }
        params.firstOrNull { it.startsWith("filename=", ignoreCase = true) }?.let { p ->
            val name = p.substringAfter('=').trim().trim('"').trim()
            if (name.isNotBlank()) return name.substringAfterLast('/').substringAfterLast('\\')
        }
        return null
    }

    private fun percentDecode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val v = hex.toIntOrNull(16)
                if (v != null) { bytes.add(v.toByte()); i += 3; continue }
            }
            for (b in c.toString().encodeToByteArray()) bytes.add(b)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun extractFileName(
        url: String,
        contentDisposition: String?,
        contentType: String?
    ): String {
        contentDispositionFileName(contentDisposition)?.let { return it }

        val urlPathLast = url.substringBefore("?").substringAfterLast("/").trim()
        val hasValidExtension =
            urlPathLast.contains(".") && urlPathLast.substringAfterLast(".").length in 2..5

        if (urlPathLast.isNotBlank() && hasValidExtension) {
            return urlPathLast
        }

        val type = contentType?.lowercase() ?: ""
        val extension = mapMimeTypeToExtension(type)

        val baseName =
            if (urlPathLast.isNotBlank()) urlPathLast.substringBefore(".") else "download_${timeSourceMs()}"
        return "$baseName$extension"
    }

    private fun mapMimeTypeToExtension(contentType: String): String {
        return when {
            contentType.contains("application/pdf") -> ".pdf"
            contentType.contains("application/zip") || contentType.contains("x-zip") -> ".zip"
            contentType.contains("application/x-rar") -> ".rar"
            contentType.contains("application/x-7z") -> ".7z"
            contentType.contains("application/x-tar") || contentType.contains("gzip") -> ".tar.gz"
            contentType.contains("video/mp4") -> ".mp4"
            contentType.contains("video/x-matroska") -> ".mkv"
            contentType.contains("video/webm") -> ".webm"
            contentType.contains("audio/mpeg") -> ".mp3"
            contentType.contains("audio/aac") -> ".aac"
            contentType.contains("audio/wav") -> ".wav"
            contentType.contains("image/jpeg") -> ".jpg"
            contentType.contains("image/png") -> ".png"
            contentType.contains("image/gif") -> ".gif"
            contentType.contains("image/webp") -> ".webp"
            contentType.contains("application/json") -> ".json"
            contentType.contains("text/html") -> ".html"
            contentType.contains("text/plain") -> ".txt"
            else -> ".bin"
        }
    }

    private fun calculateOptimalChunkCount(fileSize: Long): Int {
        return when {
            fileSize > 100 * 1024 * 1024 -> 8 // 8 parallel connections for >100MB
            fileSize > 20 * 1024 * 1024 -> 6  // 6 parallel connections for >20MB
            else -> 4                          // 4 parallel connections for >2MB
        }
    }

    private fun cleanupTempFiles(task: DownloadTask) {
        val cacheDir = pathResolver.getTempDirectory()
        // Parts are written to <temp>/task_<id>/<name>.partN (see executeDownload).
        val cachePath = "$cacheDir/task_${task.id}/${task.fileName}"
        for (i in 0..8) {
            val partPath = "${cachePath}.part$i".toPath()
            try {
                if (FileSystem.SYSTEM.exists(partPath)) {
                    FileSystem.SYSTEM.delete(partPath)
                }
            } catch (_: Exception) {
            }
        }
    }

    private var fileCounter = 0L
    private fun timeSourceMs(): Long {
        fileCounter++
        return fileCounter * 10000L + kotlin.random.Random.nextLong(1000L, 9999L)
    }

    private fun updateTaskState(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        tasksState.update { current ->
            val existing = current[taskId] ?: return@update current
            current + (taskId to transform(existing))
        }
    }

    private fun createEmptyTask(taskId: String): DownloadTask {
        return DownloadTask(
            id = taskId,
            url = "",
            fileName = "",
            destinationPath = "",
            engineType = EngineType.HTTP_DIRECT
        )
    }

    override fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = tasksState.asStateFlow()

    private fun deleteRecursively(path: okio.Path) {
        try {
            if (!FileSystem.SYSTEM.exists(path)) return
            val metadata = FileSystem.SYSTEM.metadataOrNull(path)
            if (metadata?.isDirectory == true) {
                FileSystem.SYSTEM.listOrNull(path)?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            FileSystem.SYSTEM.delete(path)
        } catch (_: Exception) {
        }
    }
}