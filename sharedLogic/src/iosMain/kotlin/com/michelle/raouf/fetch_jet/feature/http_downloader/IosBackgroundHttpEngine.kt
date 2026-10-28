package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTaskStateRunning
import platform.Foundation.NSURLSessionTaskStateSuspended
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Lets the Swift AppDelegate hand over the completion handler from
 * `application(_:handleEventsForBackgroundURLSession:completionHandler:)`.
 * Called from Swift as `IosBackgroundSessionBridge.shared.completionHandler = { ... }`.
 */
object IosBackgroundSessionBridge {
    var completionHandler: (() -> Unit)? = null
    const val SESSION_IDENTIFIER = "com.michelle.raouf.fetch_jet.background-downloads"
}

/**
 * HTTP engine for iOS built on a *background* NSURLSession.
 *
 * Why: iOS kills ordinary sockets as soon as the app is suspended. The old approach
 * (playing silent audio to stay alive) burns battery and is an App Review rejection.
 * A background session is the sanctioned mechanism: the system daemon performs the
 * transfer, survives suspension and even app termination, and relaunches the app to
 * deliver results.
 *
 * Limits versus the desktop engine: one connection per file (the system decides),
 * pause = `cancelByProducingResumeData` (needs a server that honours Range), no
 * per-task speed limit (the system does not expose one).
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackgroundHttpEngine(
    private val pathResolver: PathResolver
) : HttpDownloadEngine {

    override val engineType: EngineType = EngineType.HTTP_DIRECT

    private val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    /** taskId -> live NSURLSession task. */
    private val nativeTasks = mutableMapOf<String, NSURLSessionDownloadTask>()

    /** Speed estimation: taskId -> (timestamp, bytes). */
    private val speedSamples = mutableMapOf<String, Pair<Double, Long>>()

    private val session: NSURLSession by lazy {
        val config = NSURLSessionConfiguration
            .backgroundSessionConfigurationWithIdentifier(IosBackgroundSessionBridge.SESSION_IDENTIFIER)
        config.sessionSendsLaunchEvents = true
        config.discretionary = false
        config.allowsCellularAccess = true
        config.timeoutIntervalForResource = 7 * 24 * 3600.0 // a week: large files on slow links
        NSURLSession.sessionWithConfiguration(
            config,
            delegate = Delegate(),
            delegateQueue = NSOperationQueue()
        )
            .also { reattachRunningTasks(it) }
    }

    init {
        // Recreate the background session at every launch (not on the first start()): this is
        // what makes the system deliver the events of transfers that finished while the app
        // was suspended or killed, and what lets us adopt transfers still running.
        session
    }

    override fun observeTask(taskId: String): Flow<DownloadTask> =
        tasksState.map { it[taskId] }.filterNotNull()

    override fun enqueueTask(task: DownloadTask): DownloadTask {
        tasksState.update { current ->
            val live = nativeTasks[task.id]
            val restored = if (live != null && task.status == DownloadStatus.PAUSED) {
                // The system is still transferring this one: keep it "downloading", the
                // persisted copy only knows it was active before the app went away.
                task.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null)
            } else task
            current + (task.id to restored)
        }
        return task
    }

    override fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = tasksState.asStateFlow()

    // ---- lifecycle ------------------------------------------------------------------------

    override suspend fun start(task: DownloadTask): Result<Unit> {
        enqueueTask(task)
        return runCatching {
            // Adopted after a relaunch and still running: nothing to restart.
            nativeTasks[task.id]?.let { live ->
                val state = live.state
                if (state == NSURLSessionTaskStateRunning || state == NSURLSessionTaskStateSuspended) {
                    if (state == NSURLSessionTaskStateSuspended) live.resume()
                    updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null) }
                    return@runCatching
                }
            }
            nativeTasks.remove(task.id)?.cancel()
            val url = NSURL.URLWithString(task.url.trim())
                ?: throw IllegalArgumentException("Invalid URL")

            // A previous pause left resume data? Continue from there.
            val resume = NSData.dataWithContentsOfFile(resumeDataPath(task.id))
            val native = if (resume != null) {
                session.downloadTaskWithResumeData(resume)
            } else {
                val request = NSMutableURLRequest(uRL = url)
                request.setValue(USER_AGENT, forHTTPHeaderField = "User-Agent")
                session.downloadTaskWithRequest(request)
            }
            native.taskDescription = task.id
            nativeTasks[task.id] = native
            updateTask(task.id) {
                it.copy(
                    status = DownloadStatus.CONNECTING,
                    errorMessage = null,
                    downloadSpeedBytesPerSec = 0L
                )
            }
            native.resume()
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        val native = nativeTasks.remove(taskId)
        if (native == null) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.PAUSED,
                    downloadSpeedBytesPerSec = 0L
                )
            }
            return Result.success(Unit)
        }
        native.cancelByProducingResumeData { data ->
            data?.writeToFile(resumeDataPath(taskId), atomically = true)
        }
        updateTask(taskId) {
            it.copy(
                status = DownloadStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L
            )
        }
        return Result.success(Unit)
    }

    override suspend fun resume(taskId: String): Result<Unit> {
        val task = tasksState.value[taskId] ?: return Result.failure(Exception("Task not found"))
        return start(task)
    }

    override suspend fun cancel(taskId: String): Result<Unit> {
        nativeTasks.remove(taskId)?.cancel()
        deleteIfExists(resumeDataPath(taskId))
        tasksState.update { it - taskId }
        return Result.success(Unit)
    }

    // ---- helpers --------------------------------------------------------------------------

    /** After a relaunch the system may still be running our transfers: adopt them. */
    private fun reattachRunningTasks(s: NSURLSession) {
        s.getAllTasksWithCompletionHandler { tasks ->
            tasks?.forEach { any ->
                val native = any as? NSURLSessionDownloadTask ?: return@forEach
                val id = native.taskDescription ?: return@forEach
                nativeTasks[id] = native
                if (tasksState.value[id] == null) {
                    // The queue's restore will usually fill this in; keep a placeholder.
                    tasksState.update {
                        it + (id to DownloadTask(
                            id = id,
                            url = native.originalRequest?.URL?.absoluteString ?: "",
                            fileName = "",
                            destinationPath = pathResolver.getDownloadsDirectory(),
                            engineType = EngineType.HTTP_DIRECT,
                            status = DownloadStatus.DOWNLOADING
                        ))
                    }
                }
            }
        }
    }

    private fun resumeDataPath(taskId: String): String =
        "${pathResolver.getSettingsDirectory()}/$taskId.resume"

    private fun deleteIfExists(path: String) {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(path)) fm.removeItemAtPath(path, null)
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        tasksState.update { current ->
            val existing = current[taskId] ?: return@update current
            current + (taskId to transform(existing))
        }
    }

    private fun now(): Double = NSDate().timeIntervalSince1970

    // ---- NSURLSession delegate ------------------------------------------------------------

    private inner class Delegate : NSObject(), NSURLSessionDownloadDelegateProtocol {

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long
        ) {
            val id = downloadTask.taskDescription ?: return
            val t = now()
            val prev = speedSamples[id]
            val speed = if (prev != null && t - prev.first >= 0.5) {
                ((totalBytesWritten - prev.second) / (t - prev.first)).toLong().coerceAtLeast(0L)
            } else null
            if (prev == null || speed != null) speedSamples[id] = t to totalBytesWritten
            val total = if (totalBytesExpectedToWrite > 0) totalBytesExpectedToWrite else 0L
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    downloadedBytes = totalBytesWritten,
                    totalBytes = if (total > 0) total else it.totalBytes,
                    progress = if (total > 0) (totalBytesWritten.toFloat() / total).coerceIn(
                        0f,
                        1f
                    ) else it.progress,
                    downloadSpeedBytesPerSec = speed ?: it.downloadSpeedBytesPerSec
                )
            }
        }

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL
        ) {
            val id = downloadTask.taskDescription ?: return
            val task = tasksState.value[id]
            val response = downloadTask.response as? NSHTTPURLResponse
            val statusCode = response?.statusCode?.toInt() ?: 200
            if (statusCode !in 200..299) {
                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = "HTTP $statusCode"
                    )
                }
                return
            }
            val suggested = response?.suggestedFilename
            val rawName = task?.fileName?.ifBlank { null }
                ?: suggested
                ?: downloadTask.originalRequest?.URL?.lastPathComponent
                ?: "download_file"
            val fileName = pathResolver.sanitizeFileName(rawName)
            val destDir =
                task?.destinationPath?.ifBlank { null } ?: pathResolver.getDownloadsDirectory()
            val fm = NSFileManager.defaultManager
            if (!fm.fileExistsAtPath(destDir)) fm.createDirectoryAtPath(destDir, true, null, null)
            var finalName = fileName
            var destPath = "$destDir/$finalName"
            var n = 1
            while (fm.fileExistsAtPath(destPath)) {
                val stem = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.', "")
                finalName = if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext"
                destPath = "$destDir/$finalName"
                n++
            }
            // The temp file is deleted when this method returns: move it synchronously.
            val moved =
                fm.moveItemAtURL(didFinishDownloadingToURL, NSURL.fileURLWithPath(destPath), null)
            if (!moved) {
                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = "Could not save file to $destDir"
                    )
                }
                return
            }
            deleteIfExists(resumeDataPath(id))
            val size = (fm.attributesOfItemAtPath(destPath, null)
                ?.get("NSFileSize") as? NSNumber)?.longValue
                ?: task?.totalBytes ?: 0L
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    fileName = finalName,
                    destinationPath = destDir,
                    downloadedBytes = size,
                    totalBytes = size,
                    progress = 1f,
                    downloadSpeedBytesPerSec = 0L
                )
            }
        }

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            val id = task.taskDescription ?: return
            nativeTasks.remove(id)
            speedSamples.remove(id)
            val error = didCompleteWithError ?: return   // success already handled above
            // code -999 = cancelled: either our pause() (resume data saved) or cancel().
            if (error.code == -999L) return
            val resumeData = error.userInfo["NSURLSessionDownloadTaskResumeData"] as? NSData
            resumeData?.writeToFile(resumeDataPath(id), atomically = true)
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = error.localizedDescription,
                    downloadSpeedBytesPerSec = 0L
                )
            }
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            // iOS relaunched us in the background to deliver results; tell it we are done.
            val handler = IosBackgroundSessionBridge.completionHandler ?: return
            IosBackgroundSessionBridge.completionHandler = null
            dispatch_async(dispatch_get_main_queue()) { handler() }
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 FetchJet"
    }
}

actual fun createHttpEngine(
    pathResolver: PathResolver,
    speedLimitProvider: (String) -> Long
): HttpDownloadEngine = IosHybridHttpEngine(pathResolver, speedLimitProvider)
