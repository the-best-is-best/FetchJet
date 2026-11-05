package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.network.NetworkClient
import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.engine.DownloadEngine
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.tbib.kmp_localization.LocalizationManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.coroutines.coroutineContext

class MediaStreamDownloader(

    internal val pathResolver: PathResolver = PathResolver(),

    internal val speedLimitProvider: (String) -> Long = { 0L }

) : DownloadEngine {
    /** Why the last HLS (own engine) step failed, for the task's error line. */
    internal var lastHlsError: String? = null

    /** HTTP/1.1-only client for piece downloads (googlevideo rejects ranged requests over h2). */
    private val streamClients =
        NetworkClient { com.michelle.raouf.fetch_jet.core.network.createStreamHttpClient() }
    internal val streamClient: HttpClient get() = streamClients.current

    private val clients = NetworkClient {
        HttpClient {
            install(HttpRedirect) {
                checkHttpMethod = false
            }
            install(HttpTimeout) {
                requestTimeoutMillis = null
                connectTimeoutMillis = 60000L
                socketTimeoutMillis = 60000L
            }
            useNetworkSettings()
        }
    }
    internal val client: HttpClient get() = clients.current

    override val engineType: EngineType = EngineType.MEDIA_STREAM

    internal val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    internal val activeJobs = mutableMapOf<String, Job>()

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal val desktopUserAgent =

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
            // Wait for a previous run to really stop (yt-dlp/ffmpeg killed, files closed)
            // before touching its files.
            activeJobs.remove(task.id)?.let { it.cancel(); withTimeoutOrNull(3000L) { it.join() } }

            // Only the section mode cannot resume; a cut-afterwards clip resumes like any video.
            val isClip =
                (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()) && !task.clipByCutting
            if (isClip) {
                // A clip is cut by ffmpeg while it downloads and cannot be resumed: yt-dlp took
                // the half-written file left by a pause as finished and the task completed with
                // only the part downloaded before the pause. Start the clip again cleanly.
                try {
                    deleteRecursively("${pathResolver.getTempDirectory()}/task_${task.id}".toPath())
                } catch (_: Exception) {
                }
            }
            val fresh = if (isClip) task.copy(
                downloadedBytes = 0L,
                progress = 0f,
                currentStepText = null,
                errorMessage = null
            ) else task
            enqueueTask(fresh.copy(status = DownloadStatus.QUEUED, downloadSpeedBytesPerSec = 0L))

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
        // Wait (briefly) until yt-dlp/ffmpeg is really gone, so an immediate Resume never works
        // on files the old process is still writing. Capped: on iOS the in-app engine cannot
        // be interrupted and an unbounded wait would freeze Pause.
        currentJob?.let { withTimeoutOrNull(3000L) { it.join() } }
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
        currentJob?.let { withTimeoutOrNull(3000L) { it.join() } } // capped: see pause()
        tasksState.update { current -> current - taskId }
        // The task's scratch folder (yt-dlp .part files, temp copies) is of no use any more.
        try { deleteRecursively("${pathResolver.getTempDirectory()}/task_$taskId".toPath()) } catch (_: Exception) {}
        return Result.success(Unit)
    }

    internal suspend fun executeMediaDownload(taskId: String) {
        MediaToolsLock.awaitIdle()
        var initialTask = tasksState.value[taskId] ?: return
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
        // Sites where the browser cookie made things worse (see inspectMediaQualities) get none.
        initialTask = initialTask.copy(
            requestHeaders = com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.effective(
                downloadSourceUrl,
                initialTask.requestHeaders
            )
        )

        val fileName = resolveMediaFileName(initialTask, effectiveUrl)

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
                // Only the section mode is read as a clip; a cut-afterwards clip downloads like a
                // normal video (steps 1-3) and the cut shows on the post-processing line.
                val isClipping =
                    (!initialTask.clipStart.isNullOrBlank() || !initialTask.clipEnd.isNullOrBlank()) &&
                            !initialTask.clipByCutting
                // The platform layer names the real steps as soon as yt-dlp has chosen the formats.
                val initialStep = if (isClipping) "Clipping Video..." else "Preparing download..."
                val streams = MultiStreamProgress()
                updateTaskState(taskId) {
                    if (it.status == DownloadStatus.PAUSED) it
                    else it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        currentStepText = initialStep
                    )
                }
                val useOwnHls = initialTask.formatId == HlsDownloader.FORMAT_ID ||
                        (HlsDownloader.isHlsUrl(downloadSourceUrl) && initialTask.formatId.isNullOrBlank())

                // Own engine first (YouTube & co.): yt-dlp only resolves the direct stream
                // URLs; FetchJet downloads them in pieces (several connections, pause/resume,
                // piece map) and merges with the local ffmpeg. Anything it cannot do — HLS/DASH
                // fragments, section clips, a failure midway — falls back to yt-dlp below.
                var directDone = false
                if (!useOwnHls && !isClipping) {
                    directDone = try {
                        downloadWithOwnEngine(
                            taskId,
                            initialTask,
                            downloadSourceUrl,
                            cacheDir,
                            cacheTargetPath.toString(),
                            fileName
                        )
                    } catch (e: CancellationException) {
                        if (tasksState.value[taskId]?.status == DownloadStatus.PAUSED) return
                        throw e
                    } catch (e: Exception) {
                        com.michelle.raouf.fetch_jet.core.log.AppLog.e("MediaStream: own engine failed, using yt-dlp: ${e.message}")
                        updateTaskState(taskId) {
                            if (it.status == DownloadStatus.PAUSED) it
                            else it.copy(
                                currentStepText = "yt-dlp fallback · ${
                                    (e.message ?: e::class.simpleName ?: "error").take(
                                        90
                                    )
                                }",
                                chunks = emptyList(),
                                connections = 0,
                                progress = 0f,
                                downloadedBytes = 0L
                            )
                        }
                        false
                    }
                    if (tasksState.value[taskId]?.status == DownloadStatus.PAUSED) return
                }

                val success = if (directDone) true else try {
                    if (useOwnHls) {
                        updateTaskState(taskId) {
                            if (it.status == DownloadStatus.PAUSED) it else it.copy(
                                currentStepText = "Downloading parts…"
                            )
                        }
                        downloadWithOwnHls(
                            taskId,
                            initialTask,
                            downloadSourceUrl,
                            cacheDir,
                            cacheTargetPath
                        )
                    } else {
                        com.michelle.raouf.fetch_jet.core.log.AppLog.d {
                            "MediaStream: yt-dlp downloads ${
                                downloadSourceUrl.take(
                                    80
                                )
                            } format=${initialTask.formatId}"
                        }
                        downloadWithYtDlp(
                            taskId, initialTask, downloadSourceUrl, cacheDir, fileName,
                            isClipping, currentLimit, streams
                        )
                    }
                } catch (e: CancellationException) {
                    if (useOwnHls && tasksState.value[taskId]?.status == DownloadStatus.PAUSED) return
                    throw e
                } catch (e: Exception) {
                    if (useOwnHls) lastHlsError = e.message
                    false
                }

                if (tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                    return
                }

                // yt-dlp failed (or a merge failed): the cache may still hold an intermediate
                // file like "name.f137.mp4". Do NOT pick it up as the finished download.
                if (!success) {
                    if (useOwnHls) throw Exception("HLS: ${lastHlsError ?: "the stream could not be downloaded"}")
                    throw Exception(
                        MediaToolErrors.last(cacheDir)
                            ?.let { "yt-dlp: ${it.removePrefix("ERROR:").trim()}" }
                            ?: "Media tool reported a failure. Check the URL, quality or your network."
                    )
                }

                val foundCachePath = findFinishedFileInCache(cacheDir, cacheTargetPath, fileName)
                downloadedRealFile =
                    moveFinishedFileIntoPlace(
                        taskId,
                        initialTask,
                        foundCachePath,
                        finalTargetPath,
                        cacheDir
                    )
            }

            // Anything after yt-dlp (HTTP fallback) is a plain download: no merge line.
            if (!downloadedRealFile) {
                updateTaskState(taskId) {
                    it.copy(
                        postProcessLabel = null,
                        postProcessProgress = -1f,
                        postProcessStartedAtMs = 0L
                    )
                }
            }

            if (!coroutineContext.isActive || tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                return
            }

            if (!downloadedRealFile) {
                downloadedRealFile = downloadWithHttpFallback(
                    taskId, initialTask, effectiveUrl, cacheDir, cacheTargetPath, finalTargetPath
                )
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
                    errorMessage = e.message
                        ?: LocalizationManager.Strings.sysDownloadFailedNetwork,
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

    suspend fun resolveEffectiveMediaUrl(url: String): String {
        val current = normalizeYouTubeUrl(url.trim())
        // Facebook share links: yt-dlp follows them itself (with the right cookies/headers).
        // Resolving them here got a 400 or a generic /watch/live/ page from Facebook, and
        // yt-dlp then saw an "Unsupported URL".
        if (current.contains(
                "facebook.com/share/",
                ignoreCase = true
            ) || current.contains("fb.watch/", ignoreCase = true)
        ) {
            return current.replace("m.facebook.com", "www.facebook.com")
        }
        // facebook.com/watch/live/?ref=…&v=ID (the Live tab) → the plain watch URL yt-dlp knows.
        if (current.contains("facebook.com/", ignoreCase = true)) {
            Regex("""[?&]v=(\d{8,})""").find(current)?.groupValues?.get(1)?.let { id ->
                if (!current.contains("/watch/?v=")) return "https://www.facebook.com/watch/?v=$id"
            }
        }
        val isRedirectLink = current.contains("m.facebook.com/", ignoreCase = true) ||
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
            // A Facebook "redirect" to a page without a video id (/watch/live/?ref=…) is useless:
            // keep the link the user gave and let yt-dlp resolve it.
            if (finalUrl.contains("facebook.com", ignoreCase = true) &&
                !Regex("""\d{8,}""").containsMatchIn(finalUrl.substringAfter("facebook.com"))
            ) return current
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

    internal var mediaCounter = 0L

    internal fun timeSourceMs(): Long {

        mediaCounter++

        return mediaCounter * 10000L + kotlin.random.Random.nextLong(1000L, 9999L)

    }

    internal fun updateTaskState(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        tasksState.update { current ->
            val existing = current[taskId] ?: return@update current
            var next = transform(existing)
            // The post-processing line only exists while the task is running.
            if (next.postProcessLabel != null && next.status != DownloadStatus.DOWNLOADING &&
                next.status != DownloadStatus.CONNECTING
            ) {
                next = next.copy(
                    postProcessLabel = null,
                    postProcessProgress = -1f,
                    postProcessStartedAtMs = 0L
                )
            }
            current + (taskId to next)
        }
    }

    suspend fun inspectMediaQualities(
        url: String,
        requestHeaders: Map<String, String> = emptyMap()
    ): List<MediaQualityEntry> {
        // A playlist link (.m3u8): FetchJet's own HLS engine reads the renditions itself. The
        // CDNs that block yt-dlp / ffmpeg (Cloudflare on Udemy...) let this client through.
        inspectHlsVariants(url, requestHeaders)?.let { return it }

        MediaToolsLock.awaitIdle()
        val effectiveUrl = resolveEffectiveMediaUrl(url)

        val list = mutableListOf<MediaQualityEntry>()

        var currentIndex = 0

        try {
            val titleStarted = kotlin.time.TimeSource.Monotonic.markNow()
            val resolvedTitle = try {
                if (isYouTubeUrl(effectiveUrl)) fetchYouTubeTitle(effectiveUrl) else fetchGenericMediaTitle(
                    effectiveUrl
                )
            } catch (_: Exception) {
                null
            }
            com.michelle.raouf.fetch_jet.core.log.AppLog.d { "Inspect: title fetch ${titleStarted.elapsedNow().inWholeMilliseconds}ms" }

            // 0. Try platform-specific quality inspector (e.g. yt-dlp on Desktop)

            // The browser's Cookie/Referer help on sites that need a login (Udemy) but can make
            // others serve a page the extractor cannot read (Facebook, logged in). Try with the
            // headers first, then without them.
            val platformQualities = inspectPlatformWithCookieRetry(effectiveUrl, requestHeaders)

            if (platformQualities.isNotEmpty()) return withTitleAndDuration(
                platformQualities,
                resolvedTitle
            )

            currentIndex = if (isYouTubeUrl(effectiveUrl)) {
                inspectYouTubeFallbacks(effectiveUrl, list, currentIndex)
            } else {
                inspectWebPage(effectiveUrl, resolvedTitle, list, currentIndex)
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

    internal fun createEmptyTask(taskId: String): DownloadTask {

        return DownloadTask(

            id = taskId,

            url = "",

            fileName = "",

            destinationPath = "",

            engineType = EngineType.MEDIA_STREAM

        )

    }

    fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = tasksState.asStateFlow()

    internal fun deleteRecursively(path: okio.Path) {
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

/** "HH:MM:SS", "MM:SS" or plain seconds -> seconds; null when unreadable. */
internal fun parseTimeToSecondsOrNull(t: String): Double? {
    val parts = t.trim().split(':').map { it.trim() }
    if (parts.any { it.isEmpty() || it.toDoubleOrNull() == null }) return null
    return when (parts.size) {
        1 -> parts[0].toDouble()
        2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
        3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
        else -> null
    }
}
