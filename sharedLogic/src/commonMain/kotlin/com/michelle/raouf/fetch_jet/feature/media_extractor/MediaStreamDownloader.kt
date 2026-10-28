package com.michelle.raouf.fetch_jet.feature.media_extractor


import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.engine.DownloadEngine
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource


class MediaStreamDownloader(

    private val client: HttpClient = HttpClient {

        install(HttpRedirect) {

            checkHttpMethod = false

        }

        install(HttpTimeout) {
            requestTimeoutMillis = null
            connectTimeoutMillis = 60000L
            socketTimeoutMillis = 60000L
        }

    },

    private val pathResolver: PathResolver = PathResolver(),

    private val speedLimitProvider: (String) -> Long = { 0L }

) : DownloadEngine {


    override val engineType: EngineType = EngineType.MEDIA_STREAM


    private val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    private val activeJobs = mutableMapOf<String, Job>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private val desktopUserAgent =

        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


    override fun observeTask(taskId: String): Flow<DownloadTask> {

        return MutableStateFlow(tasksState.value[taskId] ?: createEmptyTask(taskId)).asStateFlow()

    }


    fun enqueueTask(task: DownloadTask): DownloadTask {

        tasksState.update { current -> current + (task.id to task) }

        return task

    }


    override suspend fun start(task: DownloadTask): Result<Unit> {
        return try {
            activeJobs.remove(task.id)?.cancel()
            enqueueTask(task.copy(status = DownloadStatus.QUEUED, downloadSpeedBytesPerSec = 0L))

            val job = scope.launch {
                executeMediaDownload(task.id)
            }
            activeJobs[task.id] = job
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L
            )
        }
        val currentJob = activeJobs.remove(taskId)
        currentJob?.cancel()
        return Result.success(Unit)
    }

    override suspend fun resume(taskId: String): Result<Unit> {
        val currentTask =
            tasksState.value[taskId] ?: return Result.failure(Exception("Task not found"))
        return start(currentTask)
    }

    override suspend fun cancel(taskId: String): Result<Unit> {
        val currentJob = activeJobs.remove(taskId)
        currentJob?.cancel()
        currentJob?.join()
        tasksState.update { current -> current - taskId }
        // The task's scratch folder (yt-dlp .part files, temp copies) is of no use any more.
        try { deleteRecursively("${pathResolver.getTempDirectory()}/task_$taskId".toPath()) } catch (_: Exception) {}
        return Result.success(Unit)
    }

    private suspend fun executeMediaDownload(taskId: String) {
        val initialTask = tasksState.value[taskId] ?: return
        if (!coroutineContext.isActive || tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
            return
        }

        updateTaskState(taskId) {
            if (it.status == DownloadStatus.PAUSED) it
            else it.copy(
                status = DownloadStatus.CONNECTING,
                currentStepText = "Connecting...",
                errorMessage = null
            )
        }

        val effectiveUrl = resolveEffectiveMediaUrl(initialTask.url)
        val downloadSourceUrl = resolveEffectiveMediaUrl(initialTask.originalUrl ?: initialTask.url)

        val resolvedTitle = try {
            if (isYouTubeUrl(effectiveUrl)) fetchYouTubeTitle(effectiveUrl) else fetchGenericMediaTitle(
                effectiveUrl
            )
        } catch (_: Exception) {
            null
        }
        val mediaTitle = resolvedTitle ?: extractMediaTitle(effectiveUrl)
        val isInvalidExtension = initialTask.fileName.matches(
            Regex(
                """.*\.(php|asp|aspx|jsp|html|htm|cgi|do|action)$""",
                RegexOption.IGNORE_CASE
            )
        )
        val rawFileName =
            if (initialTask.fileName.isNotBlank() && !isInvalidExtension) initialTask.fileName else "$mediaTitle.mp4"
        val fileName = pathResolver.sanitizeFileName(rawFileName)

        updateTaskState(taskId) {
            it.copy(fileName = fileName)
        }

        val baseCacheDir = pathResolver.getTempDirectory()
        val cacheDir = "$baseCacheDir/task_$taskId"
        val cacheTargetPath = "$cacheDir/$fileName".toPath()
        val finalTargetPath = "${initialTask.destinationPath}/$fileName".toPath()
        try {
            FileSystem.SYSTEM.createDirectories(cacheDir.toPath())
        } catch (_: Exception) {
        }

        var downloadedRealFile = false

        try {
            // Check if yt-dlp native download is possible
            if (isMediaExtractorToolInstalled()) {
                val currentLimit = speedLimitProvider(taskId)
                val isClipping =
                    !initialTask.clipStart.isNullOrBlank() || !initialTask.clipEnd.isNullOrBlank()
                val initialStep = when {
                    isClipping -> "Clipping Video..."
                    initialTask.formatId?.contains('+') == true -> "Downloading Video (Step 1 of 3)..."
                    else -> "Downloading Media..."
                }
                updateTaskState(taskId) {
                    if (it.status == DownloadStatus.PAUSED) it
                    else it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        currentStepText = initialStep
                    )
                }
                val success = try {
                    downloadMediaWithPlatformTool(
                        url = downloadSourceUrl,
                        destinationDir = cacheDir,
                        fileName = fileName,
                        formatId = initialTask.formatId,
                        clipStart = initialTask.clipStart,
                        clipEnd = initialTask.clipEnd,
                        speedLimitBytesPerSec = currentLimit,
                        speedLimitProvider = { speedLimitProvider(taskId) },
                        onProgress = { progress, downloadedBytes, reportedTotalBytes, speedBytesPerSec, stepText ->
                            if (tasksState.value[taskId]?.status != DownloadStatus.PAUSED) {
                                updateTaskState(taskId) {
                                    if (it.status == DownloadStatus.PAUSED) it
                                    else {
                                        val newApprox =
                                            if (isClipping && downloadedBytes > 0 && progress > 0.02f) {
                                                (downloadedBytes / progress).toLong()
                                            } else it.approximateTotalBytes

                                        val newTotal = when {
                                            it.totalBytes > 0L -> it.totalBytes
                                            reportedTotalBytes > 0L -> reportedTotalBytes
                                            else -> 0L
                                        }

                                        val finalProgress = if (progress > 0f) progress
                                        else if (newTotal > 0L && downloadedBytes > 0L) {
                                            (downloadedBytes.toFloat() / newTotal.toFloat()).coerceIn(
                                                0f,
                                                0.99f
                                            )
                                        } else it.progress

                                        it.copy(
                                            status = DownloadStatus.DOWNLOADING,
                                            progress = finalProgress,
                                            downloadedBytes = if (downloadedBytes > 0) downloadedBytes else it.downloadedBytes,
                                            totalBytes = newTotal,
                                            approximateTotalBytes = newApprox,
                                            downloadSpeedBytesPerSec = if (speedBytesPerSec > 0L) speedBytesPerSec else it.downloadSpeedBytesPerSec,
                                            currentStepText = stepText ?: it.currentStepText
                                        )
                                    }
                                }
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }

                if (tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                    return
                }

                // yt-dlp failed (or a merge failed): the cache may still hold an intermediate
                // file like "name.f137.mp4". Do NOT pick it up as the finished download.
                if (!success) {
                    throw Exception("Media tool reported a failure. Check the URL, quality or your network.")
                }

                val baseName = fileName.substringBeforeLast(".")
                val searchPrefix = baseName.take(15)
                var foundCachePath: okio.Path? = null
                if (FileSystem.SYSTEM.exists(cacheTargetPath)) {
                    foundCachePath = cacheTargetPath
                } else {
                    try {
                        val list = FileSystem.SYSTEM.list(cacheDir.toPath())
                        foundCachePath = list.firstOrNull {
                            val n = it.name
                            !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp") &&
                                    !Regex("""\.f\d+\.\w+$""").containsMatchIn(n) &&
                                    (n == fileName || n.startsWith(baseName) || n.startsWith(
                                        searchPrefix
                                    ) || baseName.startsWith(n.substringBeforeLast(".")))
                        } ?: list.filter {
                            val n = it.name.lowercase()
                            !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp") &&
                                    (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(
                                        ".m4a"
                                    ) || n.endsWith(".mp3"))
                        }.maxByOrNull {
                            try {
                                FileSystem.SYSTEM.metadata(it).lastModifiedAtMillis ?: 0L
                            } catch (_: Exception) {
                                0L
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                if (foundCachePath != null && FileSystem.SYSTEM.exists(foundCachePath)) {
                    val completedSize = try {
                        FileSystem.SYSTEM.metadata(foundCachePath).size ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                    if (completedSize > 1024L) {
                        try {
                            finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                        } catch (_: Exception) {
                        }
                        val actualFinalPath =
                            "${initialTask.destinationPath}/${foundCachePath.name}".toPath()
                        if (FileSystem.SYSTEM.exists(actualFinalPath)) {
                            FileSystem.SYSTEM.delete(actualFinalPath)
                        }
                        FileSystem.SYSTEM.copy(foundCachePath, actualFinalPath)
                        deleteRecursively(cacheDir.toPath())
                        updateTaskState(taskId) {
                            it.copy(
                                fileName = actualFinalPath.name,
                                status = DownloadStatus.COMPLETED,
                                progress = 1.0f,
                                downloadedBytes = completedSize,
                                totalBytes = completedSize,
                                downloadSpeedBytesPerSec = 0L,
                                currentStepText = "Completed"
                            )
                        }
                        downloadedRealFile = true
                    }
                }
            }

            if (!coroutineContext.isActive || tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                return
            }

            if (!downloadedRealFile) {
                if (isYouTubeUrl(effectiveUrl) && isMediaExtractorToolInstalled()) {
                    throw Exception("Failed to download YouTube media. Verify the URL or select a different quality.")
                }

                val isDirectVideo =
                    initialTask.url.contains("googlevideo.com") || initialTask.url.contains("fbcdn.net") || initialTask.url.contains(
                        "video-downloads"
                    ) || initialTask.url.endsWith(".mp4") || initialTask.url.endsWith(".webm") || initialTask.url.contains(
                        "manifest"
                    ) || initialTask.url.contains("m3u8") || initialTask.url.contains("videoplayback")

                val directStreamUrl = if (isDirectVideo) {
                    initialTask.url
                } else if (isYouTubeUrl(effectiveUrl)) {
                    resolveAnyValidYouTubeStream(effectiveUrl)
                } else {
                    val response: HttpResponse = client.get(effectiveUrl) {
                        header(HttpHeaders.UserAgent, desktopUserAgent)
                        header(
                            HttpHeaders.Accept,
                            "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8"
                        )
                    }
                    if (response.status.isSuccess()) {
                        val contentType = response.headers[HttpHeaders.ContentType] ?: ""
                        if (contentType.contains("video") || contentType.contains("octet-stream")) {
                            downloadStreamToPath(taskId, response, cacheTargetPath)
                            try {
                                finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                            } catch (_: Exception) {
                            }
                            if (FileSystem.SYSTEM.exists(finalTargetPath)) {
                                FileSystem.SYSTEM.delete(finalTargetPath)
                            }
                            FileSystem.SYSTEM.copy(cacheTargetPath, finalTargetPath)
                            deleteRecursively(cacheDir.toPath())
                            downloadedRealFile = true
                            null
                        } else {
                            val htmlBody = response.bodyAsText()
                            extractHighQualityVideoUrl(effectiveUrl, htmlBody)
                        }
                    } else null
                }

                if (!downloadedRealFile && !directStreamUrl.isNullOrBlank()) {
                    try {
                        val videoResponse: HttpResponse = client.get(directStreamUrl) {
                            header(
                                HttpHeaders.UserAgent,
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            )
                            header(HttpHeaders.Accept, "*/*")
                            header("Sec-Fetch-Mode", "navigate")
                            if (directStreamUrl.contains("googlevideo.com")) {
                                header("Referer", "https://www.youtube.com/")
                                header("Origin", "https://www.youtube.com")
                            } else if (directStreamUrl.contains("fbcdn.net") || directStreamUrl.contains(
                                    "facebook.com"
                                )
                            ) {
                                header("Referer", "https://www.facebook.com/")
                                header("Origin", "https://www.facebook.com")
                            }
                        }
                        if (videoResponse.status.isSuccess()) {
                            downloadStreamToPath(taskId, videoResponse, cacheTargetPath)
                            try {
                                finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                            } catch (_: Exception) {
                            }
                            if (FileSystem.SYSTEM.exists(finalTargetPath)) {
                                FileSystem.SYSTEM.delete(finalTargetPath)
                            }
                            FileSystem.SYSTEM.copy(cacheTargetPath, finalTargetPath)
                            deleteRecursively(cacheDir.toPath())
                            downloadedRealFile = true
                        } else {
                            throw Exception("HTTP Error: ${videoResponse.status.value}")
                        }
                    } catch (innerE: CancellationException) {
                        throw innerE
                    } catch (innerE: Exception) {
                        throw Exception("Failed to fetch stream: ${innerE.message}")
                    }
                }
            }

            if (!downloadedRealFile) {
                throw Exception("Unable to extract direct video stream. The video may be private, age-restricted, or protected.")
            }
        } catch (e: CancellationException) {
            // Task was paused or cancelled by user. Preserve files and state!
            return
        } catch (e: Exception) {
            if (tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                return
            }
            // Keep the task's own cache (yt-dlp .part files resume on retry) and NEVER touch the
            // destination: nothing of this run was written there, and a same-named file the user
            // already had must survive a failed download.

            updateTaskState(taskId) {
                if (it.status == DownloadStatus.PAUSED) it
                else it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Download failed due to network exception",
                    downloadSpeedBytesPerSec = 0L
                )
            }
            return
        }

        if (downloadedRealFile) {
            updateTaskState(taskId) {
                if (it.status == DownloadStatus.PAUSED) it
                else it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1.0f,
                    downloadSpeedBytesPerSec = 0L,
                    currentStepText = "Completed"
                )
            }
        }
    }

    private suspend fun resolveInvidiousStream(videoId: String): String? {

        val instances = listOf(

            "https://vid.puffyan.us",

            "https://inv.nadeko.net",

            "https://invidious.projectsegfault.net"

        )

        for (instance in instances) {

            try {

                val apiUrl = "$instance/api/v1/videos/$videoId"

                val response: HttpResponse = client.get(apiUrl) {

                    header(HttpHeaders.UserAgent, desktopUserAgent)

                }

                if (response.status.isSuccess()) {

                    val json = response.bodyAsText()

                    val match = Regex("""["']url["']\s*:\s*["'](https?://[^"']+)["']""").find(json)

                    if (match != null) {

                        val url = cleanEscapedUrl(match.groupValues[1])

                        if (url.startsWith("http")) {

                            return url

                        }

                    }

                }

            } catch (_: Exception) {

            }

        }

        return null

    }


    private suspend fun resolveYouTubeFromHtmlPage(url: String): String? {

        try {

            val response: HttpResponse = client.get(url) {

                header(HttpHeaders.UserAgent, desktopUserAgent)

                header(

                    HttpHeaders.Accept,

                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

                )

            }

            if (response.status.isSuccess()) {

                val html = response.bodyAsText()

                val regex = Regex("""ytInitialPlayerResponse\s*=\s*(\{.+?\});</script>""")

                val match = regex.find(html)

                if (match != null) {

                    val json = match.groupValues[1]

                    val urlMatch =

                        Regex("""["']url["']\s*:\s*["'](https?:\\?/\\?/[^"']+)["']""").find(json)

                    if (urlMatch != null) {

                        return cleanEscapedUrl(urlMatch.groupValues[1])

                    }

                }

                val gvMatch =

                    Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)""").find(

                        html

                    )

                if (gvMatch != null) {

                    return cleanEscapedUrl(gvMatch.groupValues[1])

                }

            }

        } catch (_: Exception) {

        }

        return null

    }


    private suspend fun resolveAnyValidYouTubeStream(url: String): String? {

        // 0. Try platform-specific resolver first (e.g. yt-dlp bundled binary)

        val platformStream = try {

            resolvePlatformStream(url)

        } catch (_: Exception) {

            null

        }

        if (!platformStream.isNullOrBlank() && platformStream.startsWith("http")) {

            return platformStream

        }


        val videoId = extractYouTubeVideoId(url)


        // 1. Try Piped API first

        if (!videoId.isNullOrBlank()) {

            try {

                val apiUrl = "https://pipedapi.kavin.rocks/streams/$videoId"

                val response: HttpResponse = client.get(apiUrl) {

                    header(HttpHeaders.UserAgent, desktopUserAgent)

                }

                if (response.status.isSuccess()) {

                    val json = response.bodyAsText()

                    val mp4Match =

                        Regex("""["']url["']\s*:\s*["'](https?://[^"']+\.mp4[^"']*)["']""").find(

                            json

                        )

                    if (mp4Match != null) {

                        val dlUrl = cleanEscapedUrl(mp4Match.groupValues[1])

                        if (dlUrl.startsWith("http")) return dlUrl

                    }

                }

            } catch (_: Exception) {

            }

        }


        // 2. Try Invidious instances

        if (!videoId.isNullOrBlank()) {

            val invidiousUrl = resolveInvidiousStream(videoId)

            if (!invidiousUrl.isNullOrBlank()) return invidiousUrl

        }


        // 3. Try parsing YouTube HTML page directly

        val htmlStreamUrl = resolveYouTubeFromHtmlPage(url)

        if (!htmlStreamUrl.isNullOrBlank()) return htmlStreamUrl


        // 4. Try Cobalt API across multiple quality tiers

        for (q in listOf("720", "1080", "480", "360")) {

            try {

                val cobaltUrl = "https://api.cobalt.tools/api/json"

                val response: HttpResponse = client.post(cobaltUrl) {

                    header(HttpHeaders.UserAgent, desktopUserAgent)

                    header(HttpHeaders.Accept, "application/json")

                    header(HttpHeaders.ContentType, "application/json")

                    setBody("""{"url":"$url","vQuality":"$q"}""")

                }

                if (response.status.isSuccess()) {

                    val json = response.bodyAsText()

                    val urlMatch = Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(json)

                    val dlUrl = urlMatch?.groupValues?.get(1)?.replace("""\/""", "/")

                    if (!dlUrl.isNullOrBlank() && dlUrl.startsWith("http")) {

                        return dlUrl

                    }

                }

            } catch (_: Exception) {

            }

        }


        // 5. Fallback to embed stream

        if (!videoId.isNullOrBlank()) {

            val embedUrl = resolveYouTubeEmbedStream(videoId)

            if (!embedUrl.isNullOrBlank()) return embedUrl

        }



        return null

    }


    private suspend fun downloadStreamToPath(

        taskId: String,

        response: HttpResponse,

        targetPath: okio.Path

    ) {

        val channel: ByteReadChannel = response.bodyAsChannel()

        val totalLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L



        updateTaskState(taskId) {

            it.copy(

                status = DownloadStatus.DOWNLOADING,

                totalBytes = if (totalLength > 0) totalLength else it.totalBytes

            )

        }


        val fileSink = FileSystem.SYSTEM.sink(targetPath).buffer()

        val buffer = ByteArray(8192)

        var downloaded = 0L

        val timeSource = TimeSource.Monotonic

        var mark = timeSource.markNow()

        var bytesSinceMark = 0L

        val rateLimiter = com.michelle.raouf.fetch_jet.core.network.RateLimiter {

            speedLimitProvider(taskId)

        }



        try {

            var chunksSinceUpdate = 0



            while (!channel.isClosedForRead) {
                if (!coroutineContext.isActive || tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                    // Breaking here let the caller copy a truncated file to the destination
                    // and mark it COMPLETED. Abort instead.
                    throw CancellationException("Download paused or cancelled")
                }

                val read = channel.readAvailable(buffer, 0, buffer.size)

                if (read <= 0) break



                fileSink.write(buffer, 0, read)

                downloaded += read

                bytesSinceMark += read

                chunksSinceUpdate++

                rateLimiter.acquire(read)


                val elapsedMs = mark.elapsedNow().inWholeMilliseconds

                var speed = 0L



                if (elapsedMs >= 500) {

                    speed = (bytesSinceMark * 1000) / elapsedMs

                    mark = timeSource.markNow()

                    bytesSinceMark = 0L

                }



                if (chunksSinceUpdate >= 16 || elapsedMs >= 500) {

                    chunksSinceUpdate = 0

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

                            downloadSpeedBytesPerSec = if (speed > 0) speed else it.downloadSpeedBytesPerSec

                        )

                    }

                }

            }


            val effectiveTotal = if (totalLength > 0) totalLength else downloaded

            updateTaskState(taskId) {

                it.copy(

                    downloadedBytes = downloaded,

                    totalBytes = effectiveTotal,

                    progress = 1.0f,

                    downloadSpeedBytesPerSec = 0L

                )

            }

        } finally {

            fileSink.close()

        }

    }


    private suspend fun fetchYouTubeTitle(url: String): String? {

        return try {

            val oembedUrl = "https://www.youtube.com/oembed?url=${url}&format=json"

            val response: HttpResponse = client.get(oembedUrl)

            if (response.status.isSuccess()) {

                val json = response.bodyAsText()

                val match = Regex("""["']title["']\s*:\s*["']([^"']+)["']""").find(json)

                if (match != null) {

                    val rawTitle = match.groupValues[1]

                    decodeUnicodeEscapes(rawTitle)

                } else null

            } else null

        } catch (_: Exception) {

            null

        }

    }


    private suspend fun fetchGenericMediaTitle(url: String): String? {

        return try {

            val domain = url.lowercase()

            if (domain.contains("vimeo.com")) {

                val oembedUrl = "https://vimeo.com/api/oembed.json?url=$url"

                val response: HttpResponse = client.get(oembedUrl)

                if (response.status.isSuccess()) {

                    val json = response.bodyAsText()

                    val match = Regex("""["']title["']\s*:\s*["']([^"']+)["']""").find(json)

                    if (match != null) {

                        return decodeUnicodeEscapes(match.groupValues[1])

                    }

                }

            }

            null

        } catch (_: Exception) {

            null

        }

    }


    private fun decodeUnicodeEscapes(input: String): String {

        val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")

        return regex.replace(input) { m ->

            try {

                val codePoint = m.groupValues[1].toInt(16)

                codePoint.toChar().toString()

            } catch (_: Exception) {

                m.value

            }

        }

    }


    private fun extractYouTubeVideoId(url: String): String? {

        try {

            val lower = url.lowercase()

            when {

                lower.contains("/shorts/") -> {

                    val parts = url.split("/shorts/")

                    if (parts.size > 1) {

                        return parts[1].substringBefore("?").substringBefore("/")

                    }

                }


                lower.contains("youtu.be/") -> {

                    val parts = url.split("youtu.be/")

                    if (parts.size > 1) {

                        return parts[1].substringBefore("?").substringBefore("/")

                    }

                }


                lower.contains("watch?v=") -> {

                    val parts = url.split("watch?v=")

                    if (parts.size > 1) {

                        return parts[1].substringBefore("&").substringBefore("/")

                    }

                }

            }

        } catch (_: Exception) {

        }

        return null

    }


    private suspend fun resolveYouTubeEmbedStream(videoId: String): String? {

        try {

            val embedUrl = "https://www.youtube.com/embed/$videoId"

            val response: HttpResponse = client.get(embedUrl) {

                header(HttpHeaders.UserAgent, desktopUserAgent)

            }

            if (response.status.isSuccess()) {

                val body = response.bodyAsText()

                val extracted = extractHighQualityVideoUrl(embedUrl, body)

                if (!extracted.isNullOrBlank()) {

                    return extracted

                }

                val m =

                    Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)""").find(

                        body

                    )

                if (m != null) {

                    return cleanEscapedUrl(m.groupValues[1])

                }

            }

        } catch (_: Exception) {

        }

        return null

    }


    private fun isYouTubeUrl(url: String): Boolean {

        val lower = url.lowercase()

        return lower.contains("youtube.com") || lower.contains("youtu.be")

    }


    suspend fun resolveEffectiveMediaUrl(url: String): String {
        val current = normalizeYouTubeUrl(url.trim())
        val isRedirectLink = current.contains("facebook.com/share/", ignoreCase = true) ||
                current.contains("fb.watch/", ignoreCase = true) ||
                current.contains("m.facebook.com/", ignoreCase = true) ||
                current.contains("tiktok.com/t/", ignoreCase = true) ||
                current.contains("vm.tiktok.com/", ignoreCase = true) ||
                current.contains("vt.tiktok.com/", ignoreCase = true)

        if (!isRedirectLink) return current

        return try {
            val response: HttpResponse = client.get(current) {
                header(HttpHeaders.UserAgent, desktopUserAgent)
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
            }
            val finalUrl = response.call.request.url.toString()
            if (finalUrl.isNotBlank() && !finalUrl.contains("/login") && !finalUrl.contains("checkpoint")) {
                if (finalUrl.contains("m.facebook.com")) {
                    finalUrl.replace("m.facebook.com", "www.facebook.com")
                } else {
                    finalUrl
                }
            } else {
                val body = response.bodyAsText()
                val canonicalMatch = Regex(
                    """<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(body)
                    ?: Regex(
                        """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(body)
                val canonical = canonicalMatch?.groupValues?.get(1)?.let { cleanEscapedUrl(it) }
                if (!canonical.isNullOrBlank() && !canonical.contains("/login") && !canonical.contains(
                        "checkpoint"
                    )
                ) {
                    if (canonical.contains("m.facebook.com")) canonical.replace(
                        "m.facebook.com",
                        "www.facebook.com"
                    ) else canonical
                } else {
                    current
                }
            }
        } catch (_: Exception) {
            current
        }
    }

    private fun normalizeYouTubeUrl(url: String): String {

        try {

            val lower = url.lowercase()

            if (lower.contains("/shorts/")) {

                val parts = url.split("/shorts/")

                if (parts.size > 1) {

                    val videoId = parts[1].substringBefore("?").substringBefore("/")

                    if (videoId.isNotBlank()) {

                        return "https://www.youtube.com/watch?v=$videoId"

                    }

                }

            }

        } catch (_: Exception) {

        }

        return url

    }


    private fun extractHighQualityVideoUrl(pageUrl: String, body: String): String? {

        val domain = pageUrl.lowercase()


        // 1. YouTube High Quality Extractor

        if (domain.contains("youtube.com") || domain.contains("youtu.be")) {

            val ytPatterns = listOf(

                Regex("""["']url["']\s*:\s*["'](https?:\\?/\\?/[^"']*googlevideo\.com\\?/videoplayback[^"']*)["']"""),

                Regex("""(https?:\\?/\\?/[^"'\s]*googlevideo\.com\\?/videoplayback[^"'\s]*)"""),

                Regex("""["']streamingData["']\s*:\s*\{.*?"url"\s*:\s*["']([^"']+)["']""")

            )

            for (p in ytPatterns) {

                val m = p.find(body)

                if (m != null) {

                    val raw = m.groupValues[1]

                    val cleaned = cleanEscapedUrl(raw)

                    if (cleaned.isNotBlank()) return cleaned

                }

            }

        }


        // 2. Facebook High Quality (HD) Extractor

        if (domain.contains("facebook.com") || domain.contains("fb.watch")) {

            val fbPatterns = listOf(

                Regex("""["']playable_url_quality_hd["']\s*:\s*["']([^"']+)["']"""),

                Regex("""["']browser_native_hd_url["']\s*:\s*["']([^"']+)["']"""),

                Regex("""["']playable_url["']\s*:\s*["']([^"']+)["']"""),

                Regex("""["']browser_native_sd_url["']\s*:\s*["']([^"']+)["']"""),

                Regex("""hd_src\s*:\s*["']([^"']+)["']"""),

                Regex("""sd_src\s*:\s*["']([^"']+)["']"""),

                Regex("""sd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),

                Regex("""hd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),

                Regex("""video_src\s*:\s*["']([^"']+)["']"""),

                Regex(

                    """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

                    RegexOption.IGNORE_CASE

                ),

                Regex("""(https?:\\?/\\?/[^"'\s]*fbcdn\.net[^"'\s]*\.mp4[^"'\s]*)"""),

                Regex("""(https?:\\?/\\?/[^"'\s]*video[^"'\s]*\.fbcdn\.net[^"'\s]*)""")

            )

            for (p in fbPatterns) {

                val m = p.find(body)

                if (m != null) {

                    val raw = m.groupValues[1]

                    val cleaned = cleanEscapedUrl(raw)

                    if (cleaned.isNotBlank()) return cleaned

                }

            }

        }


        // 3. Instagram & TikTok High Quality Video Extractor

        if (domain.contains("instagram.com") || domain.contains("tiktok.com")) {

            val socialPatterns = listOf(

                Regex(

                    """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

                    RegexOption.IGNORE_CASE

                ),

                Regex("""["']video_url["']\s*:\s*["']([^"']+)["']"""),

                Regex("""["']playAddr["']\s*:\s*["']([^"']+)["']"""),

                Regex("""(https?:\\?/\\?/[^"'\s]*instagram\.com[^"'\s]*\.mp4[^"'\s]*)"""),

                Regex("""(https?:\\?/\\?/[^"'\s]*tiktokcdn\.com[^"'\s]*)""")

            )

            for (p in socialPatterns) {

                val m = p.find(body)

                if (m != null) {

                    val raw = m.groupValues[1]

                    val cleaned = cleanEscapedUrl(raw)

                    if (cleaned.isNotBlank()) return cleaned

                }

            }

        }


        // 4. Generic High Quality OpenGraph or HTML5 Video Tag Extractor

        val genericPatterns = listOf(

            Regex(

                """<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""",

                RegexOption.IGNORE_CASE

            ),

            Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),

            Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),

            Regex(

                """(https?://[^"'\s]+\.(?:mp4|m4v|webm|mkv|ts)(?:\?[^"'\s]*)?)""",

                RegexOption.IGNORE_CASE

            )

        )

        for (p in genericPatterns) {

            val m = p.find(body)

            if (m != null) {

                val raw = m.groupValues[1]

                val cleaned = cleanEscapedUrl(raw)

                if (cleaned.isNotBlank()) return cleaned

            }

        }



        return null

    }


    private fun extractMultipleWebQualities(
        pageUrl: String,
        body: String,
        videoTitle: String?
    ): List<MediaQualityEntry> {
        val domain = pageUrl.lowercase()
        val list = mutableListOf<MediaQualityEntry>()
        val seenUrls = mutableSetOf<String>()

        if (domain.contains("facebook.com") || domain.contains("fb.watch")) {
            val extractedDuration =
                Regex(""""playable_duration_in_ms"\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(1)
                    ?.toLongOrNull()?.let { it / 1000L }
                    ?: Regex("""playable_duration_in_ms\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(
                        1
                    )?.toLongOrNull()?.let { it / 1000L }
                    ?: Regex(""""duration"\s*:\s*"?([0-9.]+)"?""").find(body)?.groupValues?.get(1)
                        ?.toDoubleOrNull()?.toLong()
                    ?: Regex("""video_duration\s*:\s*([0-9]+)""").find(body)?.groupValues?.get(1)
                        ?.toLongOrNull()
                    ?: 0L

            val hdPatterns = listOf(
                Regex("""["']playable_url_quality_hd["']\s*:\s*["']([^"']+)["']"""),
                Regex("""["']browser_native_hd_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""hd_src\s*:\s*["']([^"']+)["']"""),
                Regex("""hd_src_no_ratelimit\s*:\s*["']([^"']+)["']""")
            )
            for (p in hdPatterns) {
                val m = p.find(body)
                if (m != null) {
                    val url = cleanEscapedUrl(m.groupValues[1])
                    if (url.startsWith("http") && !seenUrls.contains(url)) {
                        seenUrls.add(url)
                        list.add(
                            MediaQualityEntry(
                                index = list.size,
                                qualityLabel = "HD Quality (Direct MP4)",
                                directUrl = url,
                                format = "mp4",
                                size = -1L,
                                isSelected = list.isEmpty(),
                                formatId = "hd",
                                originalUrl = pageUrl,
                                videoTitle = videoTitle,
                                durationSeconds = extractedDuration
                            )
                        )
                        break
                    }
                }
            }

            val sdPatterns = listOf(
                Regex("""["']playable_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""["']browser_native_sd_url["']\s*:\s*["']([^"']+)["']"""),
                Regex("""sd_src\s*:\s*["']([^"']+)["']"""),
                Regex("""sd_src_no_ratelimit\s*:\s*["']([^"']+)["']"""),
                Regex("""video_src\s*:\s*["']([^"']+)["']""")
            )
            for (p in sdPatterns) {
                val m = p.find(body)
                if (m != null) {
                    val url = cleanEscapedUrl(m.groupValues[1])
                    if (url.startsWith("http") && !seenUrls.contains(url)) {
                        seenUrls.add(url)
                        list.add(
                            MediaQualityEntry(
                                index = list.size,
                                qualityLabel = "SD Quality (Direct MP4)",
                                directUrl = url,
                                format = "mp4",
                                size = -1L,
                                isSelected = list.isEmpty(),
                                formatId = "sd",
                                originalUrl = pageUrl,
                                videoTitle = videoTitle,
                                durationSeconds = extractedDuration
                            )
                        )
                        break
                    }
                }
            }
        }

        if (list.isEmpty()) {
            val genericUrl = extractHighQualityVideoUrl(pageUrl, body)
            if (!genericUrl.isNullOrBlank() && !seenUrls.contains(genericUrl)) {
                list.add(
                    MediaQualityEntry(
                        index = 0,
                        qualityLabel = "High Quality HD Stream",
                        directUrl = genericUrl,
                        format = "mp4",
                        size = -1L,
                        isSelected = true,
                        originalUrl = pageUrl,
                        videoTitle = videoTitle
                    )
                )
            }
        }

        return list
    }

    private fun cleanEscapedUrl(rawUrl: String): String {

        return rawUrl

            .replace("""\/""", "/")

            .replace("""\u0026""", "&")

            .replace("""&amp;""", "&")

            .replace("""\u00253D""", "=")

            .replace("""\u002526""", "&")

            .replace("""\u0025""", "%")

            .trim()

    }


    private fun extractMediaTitle(url: String): String {

        val domain = url.lowercase()

        val timestamp = timeSourceMs()

        return when {

            domain.contains("youtube.com") || domain.contains("youtu.be") -> "YouTube_Video_$timestamp"

            domain.contains("vimeo.com") -> "Vimeo_HD_Video_$timestamp"

            domain.contains("tiktok.com") -> "TikTok_Video_$timestamp"

            domain.contains("instagram.com") -> "Instagram_Reel_$timestamp"

            domain.contains("twitter.com") || domain.contains("x.com") -> "Twitter_Video_$timestamp"

            domain.contains("facebook.com") || domain.contains("fb.watch") -> "Facebook_Video_$timestamp"

            domain.contains("twitch.tv") -> "Twitch_Stream_$timestamp"

            else -> {

                val hostName = url.substringBefore("?").substringAfter("://").substringBefore("/")

                    .replace(".", "_")

                if (hostName.isNotBlank()) "Media_Stream_${hostName}_$timestamp" else "Media_Stream_$timestamp"

            }

        }

    }


    private var mediaCounter = 0L

    private fun timeSourceMs(): Long {

        mediaCounter++

        return mediaCounter * 10000L + kotlin.random.Random.nextLong(1000L, 9999L)

    }


    private fun updateTaskState(taskId: String, transform: (DownloadTask) -> DownloadTask) {

        tasksState.update { current ->

            val existing = current[taskId] ?: return@update current

            current + (taskId to transform(existing))

        }

    }


    suspend fun inspectMediaQualities(url: String): List<MediaQualityEntry> {

        val effectiveUrl = resolveEffectiveMediaUrl(url)

        val list = mutableListOf<MediaQualityEntry>()

        var currentIndex = 0

        try {

            val resolvedTitle = try {

                if (isYouTubeUrl(effectiveUrl)) fetchYouTubeTitle(effectiveUrl) else fetchGenericMediaTitle(

                    effectiveUrl

                )

            } catch (_: Exception) {

                null

            }


            // 0. Try platform-specific quality inspector (e.g. yt-dlp on Desktop)

            val platformQualities = try {

                inspectPlatformQualities(effectiveUrl)

            } catch (_: Exception) {

                emptyList()

            }

            if (platformQualities.isNotEmpty()) {
                val maxDur = platformQualities.maxOfOrNull { it.durationSeconds } ?: 0L
                return platformQualities.map {
                    var entry = it
                    if (entry.videoTitle.isNullOrBlank() && resolvedTitle != null) {
                        entry = entry.copy(videoTitle = resolvedTitle)
                    }
                    if (entry.durationSeconds <= 0L && maxDur > 0L) {
                        entry = entry.copy(durationSeconds = maxDur)
                    }
                    entry
                }
            }



            if (isYouTubeUrl(effectiveUrl)) {

                val videoId = extractYouTubeVideoId(effectiveUrl)

                // 1. Try Piped API
                if (!videoId.isNullOrBlank()) {
                    try {
                        val apiUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
                        val response: HttpResponse = client.get(apiUrl) {
                            header(HttpHeaders.UserAgent, desktopUserAgent)
                            timeout {
                                requestTimeoutMillis = 3000L
                                connectTimeoutMillis = 3000L
                                socketTimeoutMillis = 3000L
                            }
                        }
                        if (response.status.isSuccess()) {
                            val json = response.bodyAsText()
                            val pipedEntries = parsePipedQualities(json)
                            for (entry in pipedEntries) {
                                if (list.none { it.directUrl == entry.directUrl || it.qualityLabel == entry.qualityLabel }) {
                                    list.add(
                                        entry.copy(
                                            index = currentIndex++,
                                            isSelected = list.isEmpty()
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // 2. Query Cobalt API across quality tiers (1080, 720, 480, 360)
                val qualitiesToTry = listOf("1080", "720", "480", "360")
                for (q in qualitiesToTry) {
                    try {
                        val cobaltUrl = "https://api.cobalt.tools/api/json"
                        val response: HttpResponse = client.post(cobaltUrl) {
                            header(HttpHeaders.UserAgent, desktopUserAgent)
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.ContentType, "application/json")
                            timeout {
                                requestTimeoutMillis = 3000L
                                connectTimeoutMillis = 3000L
                                socketTimeoutMillis = 3000L
                            }
                            setBody("""{"url":"$effectiveUrl","vQuality":"$q"}""")
                        }
                        if (response.status.isSuccess()) {
                            val json = response.bodyAsText()
                            val urlMatch =
                                Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(json)
                            val dlUrl = urlMatch?.groupValues?.get(1)?.replace("""\/""", "/")
                            if (!dlUrl.isNullOrBlank() && dlUrl.startsWith("http")) {
                                val label = when (q) {
                                    "1080" -> "1080p (Full HD - MP4)"
                                    "720" -> "720p (HD - MP4)"
                                    "480" -> "480p (SD - MP4)"
                                    else -> "360p (Low - MP4)"
                                }
                                if (list.none {
                                        it.qualityLabel.contains(
                                            q,
                                            true
                                        ) || it.directUrl == dlUrl
                                    }) {
                                    list.add(
                                        MediaQualityEntry(
                                            index = currentIndex++,
                                            qualityLabel = label,
                                            directUrl = dlUrl,
                                            format = "mp4",
                                            size = -1L,
                                            isSelected = list.isEmpty()
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // 3. Fallback to embed stream if still empty

                if (list.isEmpty() && !videoId.isNullOrBlank()) {

                    val embedUrl = resolveYouTubeEmbedStream(videoId)

                    if (!embedUrl.isNullOrBlank()) {

                        list.add(

                            MediaQualityEntry(

                                index = currentIndex++,

                                qualityLabel = "720p (HD Stream - MP4)",

                                directUrl = embedUrl,

                                format = "mp4",

                                size = -1L,

                                isSelected = true

                            )

                        )

                    }

                }

            } else {
                val cleanUrl = effectiveUrl.substringBefore('?')
                val ext = cleanUrl.substringAfterLast('.', "").lowercase()
                val isDirectAudio = ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
                val isDirectVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v")

                if (isDirectAudio || isDirectVideo) {
                    val label =
                        if (isDirectAudio) "Audio Only (${ext.uppercase()})" else "Direct Media (${ext.uppercase()})"
                    var headSize = -1L
                    try {
                        val headResp = client.head(effectiveUrl)
                        headSize =
                            headResp.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                    } catch (_: Exception) {
                    }

                    list.add(
                        MediaQualityEntry(
                            index = currentIndex++,
                            qualityLabel = label,
                            directUrl = effectiveUrl,
                            format = ext,
                            size = headSize,
                            isSelected = list.isEmpty(),
                            originalUrl = effectiveUrl,
                            videoTitle = resolvedTitle
                        )
                    )
                } else {
                    val response = client.get(effectiveUrl) {
                        header(HttpHeaders.UserAgent, desktopUserAgent)
                    }
                    if (response.status.isSuccess()) {
                        val body = response.bodyAsText()
                        val webQualities =
                            extractMultipleWebQualities(effectiveUrl, body, resolvedTitle)
                        for (entry in webQualities) {
                            if (list.none { it.directUrl == entry.directUrl }) {
                                list.add(
                                    entry.copy(
                                        index = currentIndex++,
                                        isSelected = list.isEmpty()
                                    )
                                )
                            }
                        }
                    }
                }
            }

        } catch (_: Exception) {

        }



        list.removeAll { it.directUrl.contains("watch?v=") || it.directUrl.contains("youtu.be/") }



        if (list.isEmpty()) {

            val fallbackUrl = resolveAnyValidYouTubeStream(effectiveUrl)

            if (!fallbackUrl.isNullOrBlank()) {

                list.add(

                    MediaQualityEntry(

                        index = 0,

                        qualityLabel = "720p (HD - MP4)",

                        directUrl = fallbackUrl,

                        format = "mp4",

                        size = -1L,

                        isSelected = true

                    )

                )

            }

        }


        val resolvedTitle = try {

            if (isYouTubeUrl(effectiveUrl)) fetchYouTubeTitle(effectiveUrl) else fetchGenericMediaTitle(

                effectiveUrl

            )

        } catch (_: Exception) {

            null

        }

        if (resolvedTitle != null) {

            for (i in list.indices) {

                if (list[i].videoTitle.isNullOrBlank()) {

                    list[i] = list[i].copy(videoTitle = resolvedTitle)

                }

            }

        }



        if (list.isNotEmpty() && list.none { it.isSelected }) {

            list[0] = list[0].copy(isSelected = true)

        }



        return list

    }


    private fun parsePipedQualities(json: String): List<MediaQualityEntry> {

        val list = mutableListOf<MediaQualityEntry>()

        try {

            val qualityRegex = Regex(""""quality"\s*:\s*"([^"]+)"""")

            val formatRegex = Regex(""""format"\s*:\s*"([^"]+)"""")

            val blocks = json.split("\"url\":")

            var index = 0

            for (i in 1 until blocks.size) {

                val block = blocks[i]

                val urlMatch = Regex("^\\s*\"([^\"]+)\"").find(block)

                val qualityMatch = qualityRegex.find(block)

                val formatMatch = formatRegex.find(block)



                if (urlMatch != null) {

                    val rawUrl = urlMatch.groupValues[1]

                    val url = cleanEscapedUrl(rawUrl)

                    val quality = qualityMatch?.groupValues?.get(1) ?: "HD"

                    val format = formatMatch?.groupValues?.get(1) ?: "mp4"



                    if (url.startsWith("http") && (format.contains(

                            "mp4",

                            true

                        ) || format.contains("webm", true) || url.contains("googlevideo"))

                    ) {

                        if (list.none { it.qualityLabel.contains(quality, true) }) {

                            list.add(

                                MediaQualityEntry(

                                    index = index,

                                    qualityLabel = "$quality ($format)",

                                    directUrl = url,

                                    format = if (format.contains("webm", true)) "webm" else "mp4",

                                    size = -1L,

                                    isSelected = index == 0

                                )

                            )

                            index++

                        }

                    }

                }

            }

        } catch (_: Exception) {

        }

        return list

    }


    private fun createEmptyTask(taskId: String): DownloadTask {

        return DownloadTask(

            id = taskId,

            url = "",

            fileName = "",

            destinationPath = "",

            engineType = EngineType.MEDIA_STREAM

        )

    }


    fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = tasksState.asStateFlow()


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