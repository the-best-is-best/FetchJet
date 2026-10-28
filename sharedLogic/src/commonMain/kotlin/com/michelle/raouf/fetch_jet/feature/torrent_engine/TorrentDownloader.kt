package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.engine.DownloadEngine
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * BitTorrent engine. All networking is done by [TorrentBackend] (libtorrent on desktop and
 * Android); this class turns its progress snapshots into [DownloadTask] updates and keeps a
 * copy of each torrent's metadata so a task can be resumed after the app restarts.
 */
class TorrentDownloader(
    private val pathResolver: PathResolver = PathResolver(),
    private val downloadSpeedLimitProvider: (String) -> Long = { 0L },
    private val uploadSpeedLimitProvider: (String) -> Long = { 0L },
    private val backend: TorrentBackend = createTorrentBackend()
) : DownloadEngine {

    override val engineType: EngineType = EngineType.BITTORRENT

    val isSupported: Boolean get() = backend.isSupported
    fun engineStatus(): String = backend.engineStatus()
    suspend fun installEngine(): Result<Unit> = backend.installEngine()

    private val tasksState = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    private val activeJobs = mutableMapOf<String, Job>()
    private val jobsLock = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** In-memory metadata cache: task url -> parsed torrent (bytes included). */
    private val metadataCache = mutableMapOf<String, TorrentMetadata>()

    override fun observeTask(taskId: String): Flow<DownloadTask> =
        tasksState.map { it[taskId] }.filterNotNull()

    fun enqueueTask(task: DownloadTask): DownloadTask {
        tasksState.update { current -> current + (task.id to task) }
        return task
    }

    fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = tasksState.asStateFlow()

    // ---- metadata --------------------------------------------------------------------------

    /** Used by the "Inspect" button and by [start] when a task has no file list yet. */
    suspend fun fetchAndParseTorrent(url: String): Result<ParsedTorrentInfo> {
        return try {
            if (!backend.isSupported) {
                return Result.failure(Exception("BitTorrent downloads are not available on this platform"))
            }
            val meta = resolveMetadata(url)
            Result.success(
                ParsedTorrentInfo(
                    name = meta.name,
                    files = meta.files,
                    totalSize = meta.totalSize,
                    webSeeds = emptyList(),
                    announce = null
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: a native-library failure (UnsatisfiedLinkError) is an
            // Error and used to kill the coroutine silently, leaving the spinner forever.
            Result.failure(Exception(describe(e)))
        }
    }

    private fun describe(e: Throwable): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Unknown error"
        return if (e is Exception) msg else "${e::class.simpleName}: $msg"
    }

    private suspend fun resolveMetadata(url: String, taskId: String? = null): TorrentMetadata {
        metadataCache[url]?.let { return it }

        // A copy saved by an earlier run of this task (survives app restarts).
        if (taskId != null) {
            cachedTorrentPath(taskId).let { p ->
                if (FileSystem.SYSTEM.exists(p)) {
                    try {
                        val bytes = FileSystem.SYSTEM.read(p) { readByteArray() }
                        return backend.fetchMetadata(null, bytes).also { metadataCache[url] = it }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val trimmed = url.trim()
        val meta = when {
            trimmed.startsWith("magnet:", ignoreCase = true) -> backend.fetchMetadata(trimmed, null)
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith(
                "https://",
                ignoreCase = true
            ) -> {
                backend.fetchMetadata(null, downloadTorrentFile(trimmed))
            }

            else -> {
                // Local .torrent path
                val p = trimmed.removePrefix("file://").toPath()
                if (!FileSystem.SYSTEM.exists(p)) throw Exception("Torrent file not found: $trimmed")
                backend.fetchMetadata(null, FileSystem.SYSTEM.read(p) { readByteArray() })
            }
        }
        metadataCache[url] = meta
        return meta
    }

    private suspend fun downloadTorrentFile(url: String): ByteArray {
        val client = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
        try {
            val safeUrl = url.replace(" ", "%20")
            val response = client.get(safeUrl) {
                header("User-Agent", desktopUserAgent)
                header("Referer", extractReferer(safeUrl))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Failed to download .torrent file: HTTP ${response.status.value}")
            }
            val bytes = response.bodyAsBytes()
            if (bytes.size > MAX_TORRENT_FILE_BYTES) throw Exception(".torrent file is too large")
            return bytes
        } finally {
            client.close()
        }
    }

    private fun cachedTorrentPath(taskId: String) =
        "${pathResolver.getSettingsDirectory()}/torrents/$taskId.torrent".toPath()

    private fun saveTorrentCopy(taskId: String, bytes: ByteArray) {
        try {
            val p = cachedTorrentPath(taskId)
            p.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(p) { write(bytes) }
        } catch (_: Exception) {
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------

    override suspend fun start(task: DownloadTask): Result<Unit> {
        if (!backend.isSupported) {
            return Result.failure(Exception("BitTorrent downloads are not available on this platform"))
        }
        enqueueTask(task)
        launchRunner(task.id)
        return Result.success(Unit)
    }

    private suspend fun launchRunner(taskId: String) {
        jobsLock.withLock {
            activeJobs.remove(taskId)?.cancel()
            activeJobs[taskId] = scope.launch { runTorrent(taskId) }
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        jobsLock.withLock { activeJobs.remove(taskId)?.cancel() }
        backend.pause(taskId)
        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L
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
        jobsLock.withLock { activeJobs.remove(taskId)?.cancel() }
        backend.remove(taskId, deleteFiles = false)
        tasksState.update { current -> current - taskId }
        try {
            val p = cachedTorrentPath(taskId)
            if (FileSystem.SYSTEM.exists(p)) FileSystem.SYSTEM.delete(p)
        } catch (_: Exception) {
        }
        return Result.success(Unit)
    }

    private suspend fun runTorrent(taskId: String) {
        val initialTask = tasksState.value[taskId] ?: return
        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.CONNECTING,
                errorMessage = null,
                currentStepText = "Fetching metadata"
            )
        }

        try {
            val meta = resolveMetadata(initialTask.url, taskId)
            saveTorrentCopy(taskId, meta.torrentBytes)

            // Keep the user's selection/priorities if they already chose files, otherwise all.
            val current = tasksState.value[taskId] ?: return
            val files: List<TorrentFileEntry> = if (current.torrentFiles.isNotEmpty()) {
                meta.files.map { mf ->
                    val chosen = current.torrentFiles.firstOrNull { it.index == mf.index }
                    if (chosen != null) mf.copy(
                        isSelected = chosen.isSelected,
                        priority = chosen.priority
                    ) else mf
                }
            } else meta.files
            val selected = files.filter { it.isSelected }.map { it.index }.toSet()
            if (selected.isEmpty()) throw Exception("No files selected for download in this torrent.")
            val priorities = files.associate { it.index to it.priority }
            val totalSelected = files.filter { it.isSelected }.sumOf { it.size }
            val name = pathResolver.sanitizeFileName(
                if (current.fileName.isNotBlank()) current.fileName else meta.name
            )

            updateTaskState(taskId) {
                it.copy(
                    fileName = name,
                    torrentFiles = files,
                    totalBytes = totalSelected,
                    status = DownloadStatus.CONNECTING,
                    currentStepText = "Connecting to peers"
                )
            }

            backend.add(
                taskId = taskId,
                torrentBytes = meta.torrentBytes,
                savePath = current.destinationPath,
                selected = selected,
                priorities = priorities,
                disableUpload = current.disableUpload,
                downloadLimitBytesPerSec = downloadSpeedLimitProvider(taskId),
                uploadLimitBytesPerSec = uploadSpeedLimitProvider(taskId)
            )

            var lastDown = -1L
            var lastUp = -1L
            while (true) {
                val p = backend.progress(taskId)
                if (p == null) {
                    delay(POLL_MS)
                    continue
                }
                if (p.error != null) throw Exception(p.error)

                // Speed limits can change in the settings while running.
                val down = downloadSpeedLimitProvider(taskId)
                val up = uploadSpeedLimitProvider(taskId)
                if (down != lastDown || up != lastUp) {
                    backend.setLimits(taskId, down, up)
                    lastDown = down; lastUp = up
                }

                val t = tasksState.value[taskId] ?: return
                val updatedFiles = t.torrentFiles.map { f ->
                    val done = p.fileProgress.getOrNull(f.index) ?: f.downloadedBytes
                    f.copy(downloadedBytes = done, isCompleted = f.isSelected && done >= f.size)
                }
                val wanted = if (p.wantedBytes > 0) p.wantedBytes else totalSelected
                val progress =
                    if (wanted > 0) (p.downloadedBytes.toFloat() / wanted).coerceIn(0f, 1f) else 0f

                if (p.isFinished) {
                    updateTaskState(taskId) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = wanted,
                            totalBytes = wanted,
                            progress = 1f,
                            downloadSpeedBytesPerSec = 0L,
                            uploadSpeedBytesPerSec = if (t.disableUpload) 0L else p.uploadRate,
                            torrentFiles = updatedFiles,
                            currentStepText = if (t.disableUpload) "Completed" else "Seeding"
                        )
                    }
                    // A download manager does not seed forever: stop when the user disabled
                    // uploading, otherwise leave it seeding while the app runs.
                    if (t.disableUpload) backend.pause(taskId)
                    return
                }

                updateTaskState(taskId) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadedBytes = p.downloadedBytes,
                        totalBytes = wanted,
                        progress = progress,
                        downloadSpeedBytesPerSec = p.downloadRate,
                        uploadSpeedBytesPerSec = p.uploadRate,
                        torrentFiles = updatedFiles,
                        currentStepText = "${p.stateText} · ${p.peers} peers (${p.seeds} seeds)"
                    )
                }
                delay(POLL_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            try {
                backend.pause(taskId)
            } catch (_: Throwable) {
            }
            updateTaskState(taskId) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = describe(e),
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L
                )
            }
        }
    }

    // ---- file selection ---------------------------------------------------------------------

    fun updateFileSelection(taskId: String, fileIndex: Int, isSelected: Boolean) {
        scope.launch {
            var files: List<TorrentFileEntry> = emptyList()
            tasksState.update { current ->
                val task = current[taskId] ?: return@update current
                files =
                    task.torrentFiles.map { f -> if (f.index == fileIndex) f.copy(isSelected = isSelected) else f }
                val newTotal = files.filter { it.isSelected }.sumOf { it.size }
                current + (taskId to task.copy(torrentFiles = files, totalBytes = newTotal))
            }
            applySelection(taskId, files)
        }
    }

    fun updateFilePriority(taskId: String, fileIndex: Int, priority: Int) {
        scope.launch {
            var files: List<TorrentFileEntry> = emptyList()
            tasksState.update { current ->
                val task = current[taskId] ?: return@update current
                files =
                    task.torrentFiles.map { f -> if (f.index == fileIndex) f.copy(priority = priority) else f }
                current + (taskId to task.copy(torrentFiles = files))
            }
            applySelection(taskId, files)
        }
    }

    private suspend fun applySelection(taskId: String, files: List<TorrentFileEntry>) {
        val selected = files.filter { it.isSelected }.map { it.index }.toSet()
        val priorities = files.associate { it.index to it.priority }
        backend.setFileSelection(taskId, selected, priorities)
        val task = tasksState.value[taskId] ?: return
        val needsRun = files.any { it.isSelected && !it.isCompleted }
        val running = jobsLock.withLock { activeJobs[taskId]?.isActive == true }
        if (needsRun && !running && task.status != DownloadStatus.PAUSED) {
            launchRunner(taskId)
        }
    }

    private fun updateTaskState(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        tasksState.update { current ->
            val existing = current[taskId] ?: return@update current
            current + (taskId to transform(existing))
        }
    }

    private companion object {
        const val POLL_MS = 700L
        const val MAX_TORRENT_FILE_BYTES = 20 * 1024 * 1024
    }
}
