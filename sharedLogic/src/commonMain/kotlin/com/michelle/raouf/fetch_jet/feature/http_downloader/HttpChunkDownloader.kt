package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.tbib.kmp_localization.LocalizationManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class HttpChunkDownloader(
    internal val client: HttpClient = HttpClient {
        install(HttpRedirect) {
            checkHttpMethod = false
        }
        install(HttpTimeout) {
            requestTimeoutMillis = null
            connectTimeoutMillis = 60_000L
            socketTimeoutMillis = 60_000L
        }
    },
    internal val pathResolver: PathResolver = PathResolver(),
    internal val speedLimitProvider: (String) -> Long = { 0L }
) : HttpDownloadEngine {

    override val engineType: EngineType = EngineType.HTTP_DIRECT

    internal val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    private val activeJobs = mutableMapOf<String, Job>()
    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                // Resume needs byte ranges and a known length (see downloadSingleStream).
                it.copy(
                    fileName = targetFileName,
                    serverSupportsResume = acceptRanges && contentLength > 0L
                )
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
                    updateTaskState(taskId) {
                        it.copy(
                            serverSupportsResume = false,
                            chunks = emptyList(),
                            currentStepText = "Single connection · ${e.message}"
                        )
                    }
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
                    errorMessage = e.message ?: LocalizationManager.Strings.sysDownloadFailed,
                    downloadSpeedBytesPerSec = 0L
                )
            }
        }
    }

    internal fun taskHeaders(taskId: String, requestUrl: String = ""): Map<String, String> {
        val task = tasksState.value[taskId] ?: return emptyMap()
        return headersFor(task.requestHeaders, task.url, requestUrl)
    }

    private var fileCounter = 0L
    internal fun timeSourceMs(): Long {
        fileCounter++
        return fileCounter * 10000L + kotlin.random.Random.nextLong(1000L, 9999L)
    }

    internal fun updateTaskState(taskId: String, transform: (DownloadTask) -> DownloadTask) {
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
}