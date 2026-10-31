package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.http_downloader.HttpDownloadEngine
import com.michelle.raouf.fetch_jet.feature.http_downloader.createHttpEngine
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaStreamDownloader
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.concurrent.Volatile

class QueueManagerRepository(
    val pathResolver: PathResolver = PathResolver(),
    val detectLinkTypeUseCase: DetectLinkTypeUseCase = DetectLinkTypeUseCase(),
    val notificationManager: DownloadNotificationManager = DownloadNotificationManager()
) {

    companion object {
        @Volatile
        private var defaultInstance: QueueManagerRepository? = null

        fun getInstance(): QueueManagerRepository {
            return defaultInstance ?: QueueManagerRepository().also { defaultInstance = it }
        }
    }

    // Lives in the app's settings folder, not in the user's Downloads (where it was
    // visible, synced by OneDrive, and held signed URLs). Old location is migrated once.
    private val persistencePath by lazy {
        val newPath = "${pathResolver.getSettingsDirectory()}/tasks_cache.json".toPath()
        val oldPath = "${pathResolver.getDownloadsDirectory()}/kortex_tasks_cache.json".toPath()
        try {
            if (!FileSystem.SYSTEM.exists(newPath) && FileSystem.SYSTEM.exists(oldPath)) {
                FileSystem.SYSTEM.copy(oldPath, newPath)
                FileSystem.SYSTEM.delete(oldPath)
            }
        } catch (_: Exception) {
        }
        newPath
    }

    private val _globalDownloadSpeedLimit = MutableStateFlow(0L)
    val globalDownloadSpeedLimit: StateFlow<Long> = _globalDownloadSpeedLimit.asStateFlow()

    private val _globalUploadSpeedLimit = MutableStateFlow(0L)
    val globalUploadSpeedLimit: StateFlow<Long> = _globalUploadSpeedLimit.asStateFlow()

    fun setGlobalDownloadSpeedLimit(limit: Long) {
        _globalDownloadSpeedLimit.value = limit
    }

    fun setGlobalUploadSpeedLimit(limit: Long) {
        _globalUploadSpeedLimit.value = limit
    }

    val httpEngine: HttpDownloadEngine = createHttpEngine(
        pathResolver = pathResolver,
        speedLimitProvider = { taskId -> getEffectiveDownloadLimit(taskId) }
    )
    val torrentEngine: TorrentDownloader = TorrentDownloader(
        downloadSpeedLimitProvider = { taskId -> getEffectiveDownloadLimit(taskId) },
        uploadSpeedLimitProvider = { taskId -> getEffectiveUploadLimit(taskId) }
    )
    val mediaEngine: MediaStreamDownloader = MediaStreamDownloader(
        speedLimitProvider = { taskId -> getEffectiveDownloadLimit(taskId) }
    )

    private fun getAllCurrentTasks(): List<DownloadTask> {
        val httpTasks = httpEngine.getAllTasksFlow().value.values
        val torrentTasks = torrentEngine.getAllTasksFlow().value.values
        val mediaTasks = mediaEngine.getAllTasksFlow().value.values
        return (httpTasks + torrentTasks + mediaTasks).toList()
    }

    fun getEffectiveDownloadLimit(taskId: String): Long {
        val allTasks = getAllCurrentTasks()
        val task = allTasks.firstOrNull { it.id == taskId }
        val taskLimit = task?.maxDownloadSpeedBytesPerSec ?: 0L
        val globalLimit = _globalDownloadSpeedLimit.value

        if (globalLimit <= 0L && taskLimit <= 0L) return 0L

        val activeTasks = allTasks.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING
        }
        val activeCount = maxOf(1, activeTasks.size)
        val perTaskGlobal =
            if (globalLimit > 0L) maxOf(16 * 1024L, globalLimit / activeCount) else 0L

        return when {
            taskLimit > 0L && perTaskGlobal > 0L -> minOf(taskLimit, perTaskGlobal)
            taskLimit > 0L -> taskLimit
            perTaskGlobal > 0L -> perTaskGlobal
            else -> 0L
        }
    }

    fun getEffectiveUploadLimit(taskId: String): Long {
        val allTasks = getAllCurrentTasks()
        val task = allTasks.firstOrNull { it.id == taskId }
        val taskLimit = task?.maxUploadSpeedBytesPerSec ?: 0L
        val globalLimit = _globalUploadSpeedLimit.value

        if (globalLimit <= 0L && taskLimit <= 0L) return 0L

        val activeTasks = allTasks.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING
        }
        val activeCount = maxOf(1, activeTasks.size)
        val perTaskGlobal =
            if (globalLimit > 0L) maxOf(16 * 1024L, globalLimit / activeCount) else 0L

        return when {
            taskLimit > 0L && perTaskGlobal > 0L -> minOf(taskLimit, perTaskGlobal)
            taskLimit > 0L -> taskLimit
            perTaskGlobal > 0L -> perTaskGlobal
            else -> 0L
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var taskSequence = 0L

    private fun generateUniqueTaskId(): String {
        taskSequence++
        val randomNum = kotlin.random.Random.nextLong(100000L, 999999L)
        return "task_${taskSequence}_$randomNum"
    }

    // ---- Queue & schedule ------------------------------------------------------------------
    // Downloads that wait (for their start time, or for a free slot) live here, not in an
    // engine: an engine only ever sees a download when it really starts.

    /** Waiting downloads, by id. */
    private val pendingTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    private val queueSettingsFile get() = "${pathResolver.getSettingsDirectory()}/queue.properties".toPath()

    private val _maxConcurrentDownloads = MutableStateFlow(loadMaxConcurrent())

    /** How many downloads run at the same time; 0 = no limit. */
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    fun setMaxConcurrentDownloads(count: Int) {
        _maxConcurrentDownloads.value = count.coerceIn(0, 20)
        try {
            queueSettingsFile.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(queueSettingsFile) {
                writeUtf8("# FetchJet queue settings\n")
                writeUtf8("maxConcurrentDownloads=${_maxConcurrentDownloads.value}\n")
            }
        } catch (_: Exception) {
        }
        pumpQueue()
    }

    private fun loadMaxConcurrent(): Int = try {
        if (!FileSystem.SYSTEM.exists(queueSettingsFile)) 3
        else FileSystem.SYSTEM.read(queueSettingsFile) { readUtf8() }.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("maxConcurrentDownloads=") }
            ?.substringAfter('=')?.trim()?.toIntOrNull()?.coerceIn(0, 20) ?: 3
    } catch (_: Exception) {
        3
    }

    /** Downloads the engines are running (or starting) now. */
    private fun runningCount(): Int = getAllCurrentTasks().count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING ||
                it.status == DownloadStatus.QUEUED
    }

    private fun hasFreeSlot(): Boolean {
        val max = _maxConcurrentDownloads.value
        return max <= 0 || runningCount() < max
    }

    /**
     * Paused / failed downloads that "Resume all" put back in line: they stay paused in their
     * engine until the queue gives them a slot, then they continue from where they stopped.
     */
    private val resumeQueue = MutableStateFlow<Set<String>>(emptySet())

    private val pumpLock = Mutex()

    /** Starts waiting downloads whose time has come, while there are free slots (oldest first). */
    private fun pumpQueue() {
        scope.launch {
            pumpLock.withLock {
                val now = nowEpochMs()
                val engineTasks = getAllCurrentTasks().associateBy { it.id }
                val due = (pendingTasks.value.values.filter { it.scheduledAtMs <= now } +
                        resumeQueue.value.mapNotNull { engineTasks[it] })
                    .sortedBy { it.createdAtEpochMs }
                // A resumed engine task shows "running" only a moment later: count it here.
                var resumedNow = 0
                for (task in due) {
                    val max = _maxConcurrentDownloads.value
                    if (max > 0 && runningCount() + resumedNow >= max) break
                    // Taken out in one step: "Start now" / Cancel may race with this loop.
                    val taken = pendingTasks.getAndUpdate { it - task.id }[task.id]
                    if (taken != null) {
                        startOnEngine(taken)
                    } else if (task.id in resumeQueue.getAndUpdate { it - task.id }) {
                        resumeOnEngine(task)
                        resumedNow++
                    }
                }
                // Ids whose download is gone or no longer paused leave the line.
                resumeQueue.update { ids ->
                    ids.filterTo(mutableSetOf()) { id ->
                        val t = engineTasks[id]
                        t != null && (t.status == DownloadStatus.PAUSED || t.status == DownloadStatus.ERROR)
                    }
                }
            }
        }
    }

    private suspend fun resumeOnEngine(task: DownloadTask): Result<Unit> = try {
        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.resume(task.id)
            EngineType.BITTORRENT -> torrentEngine.resume(task.id)
            EngineType.MEDIA_STREAM -> mediaEngine.resume(task.id)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Pauses everything that runs or waits. */
    fun pauseAll() {
        scope.launch {
            resumeQueue.value = emptySet()
            val ids = pendingTasks.value.keys + getAllCurrentTasks().filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING ||
                        it.status == DownloadStatus.QUEUED
            }.map { it.id }
            ids.forEach { pauseTask(it) }
        }
    }

    /**
     * Puts every paused / failed download back in line; the queue runs them a few at a time
     * (Settings -> downloads at the same time), oldest first.
     */
    fun resumeAll() {
        val ids = getAllCurrentTasks().filter {
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR
        }.map { it.id }
        resumeQueue.update { it + ids }
        pumpQueue()
    }

    private suspend fun startOnEngine(task: DownloadTask) {
        val fresh = task.copy(
            status = DownloadStatus.QUEUED,
            scheduledAtMs = 0L,
            waitingInQueue = false,
            queuePosition = 0
        )
        val result = try {
            when (fresh.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.start(fresh)
                EngineType.BITTORRENT -> torrentEngine.start(fresh)
                EngineType.MEDIA_STREAM -> mediaEngine.start(fresh)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onFailure { e ->
            // Keep it visible with the reason instead of losing it.
            val failed = fresh.copy(
                status = DownloadStatus.ERROR,
                errorMessage = e.message ?: "Could not start"
            )
            when (fresh.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(failed)
                EngineType.BITTORRENT -> torrentEngine.enqueueTask(failed)
                EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(failed)
            }
        }
    }

    val allTasksFlow: StateFlow<List<DownloadTask>> = combine(
        httpEngine.getAllTasksFlow(),
        torrentEngine.getAllTasksFlow(),
        mediaEngine.getAllTasksFlow(),
        pendingTasks,
        resumeQueue
    ) { httpTasks, torrentTasks, mediaTasks, pending, resumeIds ->
        val now = nowEpochMs()
        val inLine = (httpTasks.values + torrentTasks.values + mediaTasks.values).filter {
            it.id in resumeIds && (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR)
        }
        val dueOrder = (pending.values.filter { it.scheduledAtMs <= now } + inLine)
            .sortedBy { it.createdAtEpochMs }
            .mapIndexed { i, t -> t.id to i + 1 }.toMap()
        val waiting = pending.values.map {
            it.copy(
                status = DownloadStatus.QUEUED,
                waitingInQueue = true,
                queuePosition = dueOrder[it.id] ?: 0
            )
        }
        val inLineIds = inLine.map { it.id }.toSet()
        val engineTasks = (httpTasks.values + torrentTasks.values + mediaTasks.values).map {
            if (it.id in inLineIds) it.copy(
                status = DownloadStatus.QUEUED,
                waitingInQueue = true,
                errorMessage = null,
                queuePosition = dueOrder[it.id] ?: 0
            ) else it
        }
        (engineTasks + waiting)
            .sortedByDescending { it.createdAtEpochMs }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val activeRunningTaskIds = mutableSetOf<String>()
    private val completedNotifiedTaskIds = mutableSetOf<String>()

    init {
        observeTasksForNotifications()
        loadAndRestoreTasks()
        observeTasksForPersistence()
        // Starts queued / scheduled downloads when a slot frees up or their time comes.
        scope.launch {
            while (true) {
                delay(1000)
                if (pendingTasks.value.isNotEmpty() || resumeQueue.value.isNotEmpty()) pumpQueue()
            }
        }
    }

    private fun observeTasksForNotifications() {
        scope.launch {
            allTasksFlow.collect { tasks ->
                val currentTaskIds = tasks.map { it.id }.toSet()

                val removedTaskIds = activeRunningTaskIds.filter { it !in currentTaskIds }
                removedTaskIds.forEach { taskId ->
                    notificationManager.cancelNotification(taskId)
                    activeRunningTaskIds.remove(taskId)
                    completedNotifiedTaskIds.remove(taskId)
                }

                tasks.forEach { task ->
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING) {
                        activeRunningTaskIds.add(task.id)
                        completedNotifiedTaskIds.remove(task.id)
                        val percent = (task.progress * 100).toInt()
                        val speedStr = formatSpeed(task.downloadSpeedBytesPerSec)
                        val name = task.fileName.ifBlank { task.url.substringAfterLast("/") }
                        notificationManager.showOrUpdateNotification(
                            taskId = task.id,
                            fileName = name,
                            progressPercent = percent,
                            speedText = speedStr,
                            downloadedBytes = task.downloadedBytes,
                            totalBytes = task.totalBytes
                        )
                    } else if (task.status == DownloadStatus.COMPLETED) {
                        if (activeRunningTaskIds.contains(task.id) && !completedNotifiedTaskIds.contains(
                                task.id
                            )
                        ) {
                            completedNotifiedTaskIds.add(task.id)
                            activeRunningTaskIds.remove(task.id)
                            val name = task.fileName.ifBlank { task.url.substringAfterLast("/") }
                            notificationManager.showOrUpdateNotification(
                                taskId = task.id,
                                fileName = name,
                                progressPercent = 100,
                                speedText = "Completed",
                                downloadedBytes = task.totalBytes,
                                totalBytes = task.totalBytes
                            )
                        }
                    } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.ERROR) {
                        if (activeRunningTaskIds.remove(task.id)) {
                            notificationManager.cancelNotification(task.id)
                        }
                    }
                }
            }
        }
    }

    suspend fun inspectMediaQualities(
        url: String,
        requestHeaders: Map<String, String> = emptyMap()
    ): List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> {
        return mediaEngine.inspectMediaQualities(url, requestHeaders)
    }

    /** The videos of a playlist / channel link (null = not a playlist). */
    suspend fun inspectPlaylist(url: String): com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? =
        com.michelle.raouf.fetch_jet.feature.media_extractor.inspectPlatformPlaylist(url)

    suspend fun enqueueTask(
        url: String,
        customDestinationPath: String? = null,
        customFileName: String? = null,
        disableUpload: Boolean = false,
        torrentFiles: List<com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry> = emptyList(),
        mediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),
        torrentName: String? = null,
        webSeedUrls: List<String> = emptyList(),
        maxDownloadSpeedBytesPerSec: Long = 0L,
        maxUploadSpeedBytesPerSec: Long = 0L,
        forcedEngineType: EngineType? = null,
        clipStart: String? = null,
        clipEnd: String? = null,
        /** Clip: whole video at full speed, then a local cut (see CutPlugin.kt). Off = only the
         *  clip is fetched (--download-sections), which YouTube throttles to about playback rate. */
        clipByCutting: Boolean = false,
        requestHeaders: Map<String, String> = emptyMap(),
        /** Start at this time (epoch ms); 0 = now (or as soon as a slot is free). */
        scheduledAtMs: Long = 0L
    ): Result<DownloadTask> {
        // A quoted / file:// .torrent path is stored as a plain path.
        val trimmedUrl = url.trim().let {
            if (com.michelle.raouf.fetch_jet.feature.torrent_engine.isLocalTorrentPath(it))
                com.michelle.raouf.fetch_jet.feature.torrent_engine.normalizeLocalTorrentPath(it)
            else it
        }
        if (trimmedUrl.isBlank()) return Result.failure(Exception("URL cannot be empty"))

        var targetUrl = trimmedUrl
        var formatId: String? = null
        var originalMediaUrl: String? = null
        var initialTotalBytes = 0L
        var initialApproximateBytes = 0L
        if (mediaQualities.isNotEmpty()) {
            val selected =
                mediaQualities.firstOrNull { it.isSelected } ?: mediaQualities.firstOrNull()
            if (selected != null) {
                targetUrl = selected.directUrl
                formatId = selected.formatId
                originalMediaUrl = selected.originalUrl ?: trimmedUrl
                if (selected.size > 0L) {
                    val isClippingRequest = !clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank()
                    // Fast mode downloads the whole file: its real size is the total.
                    if (isClippingRequest && !clipByCutting) {
                        val s = parseSimpleTimeSec(clipStart)
                        val e =
                            if (!clipEnd.isNullOrBlank()) parseSimpleTimeSec(clipEnd) else selected.durationSeconds.toDouble()
                        val clipDur = if (e > s) e - s else selected.durationSeconds.toDouble()
                        val approxBytes = if (selected.durationSeconds > 0 && clipDur > 0) {
                            ((selected.size.toDouble() * clipDur) / selected.durationSeconds.toDouble()).toLong()
                        } else {
                            selected.size
                        }
                        // If we have a realistic proportional size (> 1 MB), use it as fixed initial total!
                        initialTotalBytes = if (approxBytes > 1024 * 1024L) approxBytes else 0L
                        initialApproximateBytes = approxBytes
                    } else {
                        initialTotalBytes = selected.size
                    }
                }
            }
        }

        val engineType = forcedEngineType
            ?: if (mediaQualities.isNotEmpty()) EngineType.MEDIA_STREAM else detectLinkTypeUseCase(
                targetUrl
            )

        if (engineType == EngineType.MEDIA_STREAM && originalMediaUrl.isNullOrBlank()) {
            originalMediaUrl = trimmedUrl
        }
        // Clip mode is the user's choice in the Add dialog (default: only the clip is downloaded).
        val cutAfterDownload =
            clipByCutting && (!clipStart.isNullOrBlank() || !clipEnd.isNullOrBlank())
        val taskId = generateUniqueTaskId()
        val destDir =
            if (!customDestinationPath.isNullOrBlank()) customDestinationPath else pathResolver.getDownloadsDirectory()

        val task = DownloadTask(
            id = taskId,
            url = targetUrl,
            fileName = customFileName ?: torrentName ?: "",
            destinationPath = destDir,
            engineType = engineType,
            disableUpload = disableUpload,
            totalBytes = initialTotalBytes,
            torrentFiles = torrentFiles,
            webSeedUrls = webSeedUrls,
            maxDownloadSpeedBytesPerSec = maxDownloadSpeedBytesPerSec,
            maxUploadSpeedBytesPerSec = maxUploadSpeedBytesPerSec,
            formatId = formatId,
            originalUrl = originalMediaUrl,
            clipStart = clipStart,
            clipEnd = clipEnd,
            clipByCutting = cutAfterDownload,
            approximateTotalBytes = initialApproximateBytes,
            createdAtEpochMs = nowEpochMs(),
            requestHeaders = requestHeaders.filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() },
            scheduledAtMs = scheduledAtMs.takeIf { it > nowEpochMs() } ?: 0L
        )

        // Later (scheduled), others already waiting, or no free slot: join the queue. The pump
        // (under its lock) starts it, so a new add never jumps ahead of waiting ones.
        if (task.scheduledAtMs > 0L || pendingTasks.value.isNotEmpty() || !hasFreeSlot()) {
            pendingTasks.update { it + (task.id to task.copy(status = DownloadStatus.QUEUED)) }
            pumpQueue()
            return Result.success(task)
        }

        return try {
            // An engine that refuses (e.g. BitTorrent not available on this build) must not be
            // reported as "Started download".
            // QUEUED (= starting) right away, so the next add already counts this one as running.
            val starting = task.copy(status = DownloadStatus.QUEUED)
            when (engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.start(starting)
                EngineType.BITTORRENT -> torrentEngine.start(starting)
                EngineType.MEDIA_STREAM -> mediaEngine.start(starting)
            }.getOrThrow()
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pauseTask(taskId: String): Result<Unit> {
        // In line after "Resume all": it is still paused in its engine, just leave the line.
        if (taskId in resumeQueue.getAndUpdate { it - taskId }) return Result.success(Unit)
        // A waiting download leaves the queue and stays paused until Resume.
        pendingTasks.getAndUpdate { it - taskId }[taskId]?.let { waiting ->
            val paused = waiting.copy(status = DownloadStatus.PAUSED, scheduledAtMs = 0L)
            when (paused.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(paused)
                EngineType.BITTORRENT -> torrentEngine.enqueueTask(paused)
                EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(paused)
            }
            return Result.success(Unit)
        }
        val task = allTasksFlow.value.find { it.id == taskId }
            ?: return Result.failure(Exception("Task not found"))
        notificationManager.cancelNotification(taskId)
        activeRunningTaskIds.remove(taskId)
        return when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.pause(taskId)
            EngineType.BITTORRENT -> torrentEngine.pause(taskId)
            EngineType.MEDIA_STREAM -> mediaEngine.pause(taskId)
        }
    }

    suspend fun resumeTask(taskId: String): Result<Unit> {
        // "Start now" on a waiting download: skip the queue / the schedule.
        pendingTasks.getAndUpdate { it - taskId }[taskId]?.let { waiting ->
            startOnEngine(waiting)
            return Result.success(Unit)
        }
        resumeQueue.update { it - taskId } // "Start now" on a download in line
        val task = allTasksFlow.value.find { it.id == taskId }
            ?: return Result.failure(Exception("Task not found"))
        return when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.resume(taskId)
            EngineType.BITTORRENT -> torrentEngine.resume(taskId)
            EngineType.MEDIA_STREAM -> mediaEngine.resume(taskId)
        }
    }

    suspend fun cancelTask(taskId: String): Result<Unit> {
        resumeQueue.update { it - taskId }
        if (pendingTasks.getAndUpdate { it - taskId }.containsKey(taskId)) {
            return Result.success(Unit) // never started: nothing on disk
        }
        val task = allTasksFlow.value.find { it.id == taskId }
            ?: return Result.failure(Exception("Task not found"))
        notificationManager.cancelNotification(taskId)
        activeRunningTaskIds.remove(taskId)
        completedNotifiedTaskIds.remove(taskId)

        return when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.cancel(taskId)
            EngineType.BITTORRENT -> torrentEngine.cancel(taskId)
            EngineType.MEDIA_STREAM -> mediaEngine.cancel(taskId)
        }
    }

    suspend fun removeTask(taskId: String, deleteFile: Boolean): Result<Unit> {
        resumeQueue.update { it - taskId }
        if (pendingTasks.getAndUpdate { it - taskId }.containsKey(taskId)) {
            return Result.success(Unit)
        }
        val task = allTasksFlow.value.find { it.id == taskId }
            ?: return Result.failure(Exception("Task not found"))
        notificationManager.cancelNotification(taskId)
        activeRunningTaskIds.remove(taskId)
        completedNotifiedTaskIds.remove(taskId)

        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.cancel(taskId)
            EngineType.BITTORRENT -> torrentEngine.cancel(taskId)
            EngineType.MEDIA_STREAM -> mediaEngine.cancel(taskId)
        }

        if (deleteFile) {
            try {
                val effectiveFileName = if (task.fileName.isNotBlank()) {
                    task.fileName
                } else {
                    task.url.substringBefore('?').substringAfterLast('/')
                }
                val baseName = effectiveFileName.substringBeforeLast('.')
                val tempDir = pathResolver.getTempDirectory()

                when (task.engineType) {
                    EngineType.HTTP_DIRECT, EngineType.MEDIA_STREAM -> {
                        // 1. Delete destination files
                        if (task.destinationPath.isNotBlank() && effectiveFileName.isNotBlank()) {
                            val destDir = task.destinationPath
                            val filePathsToCheck = mutableListOf(
                                "$destDir/$effectiveFileName",
                                "$destDir/$effectiveFileName.part",
                                "$destDir/$effectiveFileName.ytdl",
                                "$destDir/$effectiveFileName.temp"
                            )
                            for (i in 0..16) {
                                filePathsToCheck.add("$destDir/$effectiveFileName.part$i")
                            }
                            for (p in filePathsToCheck) {
                                try {
                                    val okPath = p.toPath()
                                    if (FileSystem.SYSTEM.exists(okPath)) {
                                        FileSystem.SYSTEM.delete(okPath)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }

                        // 2. Delete cache/temp files
                        if (tempDir.isNotBlank() && effectiveFileName.isNotBlank()) {
                            val tempPathsToCheck = mutableListOf(
                                "$tempDir/$effectiveFileName",
                                "$tempDir/$effectiveFileName.part",
                                "$tempDir/$effectiveFileName.ytdl",
                                "$tempDir/$effectiveFileName.temp"
                            )
                            for (i in 0..16) {
                                tempPathsToCheck.add("$tempDir/$effectiveFileName.part$i")
                            }
                            for (p in tempPathsToCheck) {
                                try {
                                    val okPath = p.toPath()
                                    if (FileSystem.SYSTEM.exists(okPath)) {
                                        FileSystem.SYSTEM.delete(okPath)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            // Also search tempDir for any leftover files starting with baseName
                            try {
                                val tempOkioPath = tempDir.toPath()
                                if (FileSystem.SYSTEM.exists(tempOkioPath)) {
                                    val list = FileSystem.SYSTEM.list(tempOkioPath)
                                    for (item in list) {
                                        if (item.name.startsWith(baseName) || item.name.startsWith(
                                                effectiveFileName
                                            )
                                        ) {
                                            try {
                                                FileSystem.SYSTEM.delete(item)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }

                    EngineType.BITTORRENT -> {
                        if (task.destinationPath.isNotBlank() && task.fileName.isNotBlank()) {
                            val dirPath = "${task.destinationPath}/${task.fileName}".toPath()
                            if (FileSystem.SYSTEM.exists(dirPath)) {
                                try {
                                    FileSystem.SYSTEM.deleteRecursively(dirPath)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        for (fileEntry in task.torrentFiles) {
                            val filePathStr =
                                "${task.destinationPath}/${if (fileEntry.path.startsWith("${task.fileName}/")) fileEntry.path else "${task.fileName}/${fileEntry.path}"}"
                            val filePathObj = filePathStr.toPath()
                            if (FileSystem.SYSTEM.exists(filePathObj)) {
                                try {
                                    FileSystem.SYSTEM.delete(filePathObj)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
        }

        return Result.success(Unit)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 Kbps (0 KB/s)"
        val bits = bytesPerSec * 8.0
        val kbps = bits / 1024.0
        val mbps = kbps / 1024.0
        val mbpsStr = if (mbps >= 1.0) {
            val formatted = kotlin.math.round(mbps * 10) / 10.0
            "$formatted Mbps"
        } else {
            val formatted = kotlin.math.round(kbps * 10) / 10.0
            "$formatted Kbps"
        }

        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        val byteStr = if (mb >= 1.0) {
            val formatted = kotlin.math.round(mb * 10) / 10.0
            "$formatted MB/s"
        } else {
            val formatted = kotlin.math.round(kb * 10) / 10.0
            "$formatted KB/s"
        }

        return "$mbpsStr ($byteStr)"
    }

    fun updateTorrentFileSelection(taskId: String, fileIndex: Int, isSelected: Boolean) {
        if (pendingTasks.value.containsKey(taskId)) {
            pendingTasks.update { m ->
                val t = m[taskId] ?: return@update m
                m + (taskId to t.copy(torrentFiles = t.torrentFiles.map { f ->
                    if (f.index == fileIndex) f.copy(
                        isSelected = isSelected
                    ) else f
                }))
            }
            return
        }
        torrentEngine.updateFileSelection(taskId, fileIndex, isSelected)
    }

    fun updateTorrentFilePriority(taskId: String, fileIndex: Int, priority: Int) {
        torrentEngine.updateFilePriority(taskId, fileIndex, priority)
    }

    private fun loadAndRestoreTasks() {
        try {
            if (!FileSystem.SYSTEM.exists(persistencePath)) return
            val json = FileSystem.SYSTEM.read(persistencePath) { readUtf8() }
            val objects = splitJsonObjects(json)
            var restored = 0
            var broken = 0
            for (content in objects) try {
                val id = extractJsonField(content, "id") ?: continue
                val url = extractJsonField(content, "url") ?: ""
                val fileName = extractJsonField(content, "fileName") ?: ""
                val destinationPath = extractJsonField(content, "destinationPath")
                    ?: pathResolver.getDownloadsDirectory()
                val engineTypeName = extractJsonField(content, "engineType") ?: "HTTP_DIRECT"
                val statusName = extractJsonField(content, "status") ?: "PAUSED"
                val totalBytes = extractJsonField(content, "totalBytes")?.toLongOrNull() ?: 0L
                val downloadedBytes =
                    extractJsonField(content, "downloadedBytes")?.toLongOrNull() ?: 0L
                val progress = extractJsonField(content, "progress")?.toFloatOrNull() ?: 0f
                val createdAtEpochMs =
                    extractJsonField(content, "createdAtEpochMs")?.toLongOrNull() ?: 0L
                val formatId = extractJsonField(content, "formatId")?.ifBlank { null }
                val originalUrl = extractJsonField(content, "originalUrl")?.ifBlank { null }
                val clipStart = extractJsonField(content, "clipStart")?.ifBlank { null }
                val clipEnd = extractJsonField(content, "clipEnd")?.ifBlank { null }
                val clipByCutting =
                    extractJsonField(content, "clipByCutting")?.toBooleanStrictOrNull() ?: false
                val serverSupportsResume =
                    extractJsonField(content, "serverSupportsResume")?.toBooleanStrictOrNull()
                val scheduledAtMs = extractJsonField(content, "scheduledAtMs")?.toLongOrNull() ?: 0L
                val currentStepText = extractJsonField(content, "currentStepText")?.ifBlank { null }
                val disableUpload =
                    extractJsonField(content, "disableUpload")?.toBooleanStrictOrNull() ?: false
                val maxDownloadSpeedBytesPerSec =
                    extractJsonField(content, "maxDownloadSpeedBytesPerSec")?.toLongOrNull() ?: 0L
                val maxUploadSpeedBytesPerSec =
                    extractJsonField(content, "maxUploadSpeedBytesPerSec")?.toLongOrNull() ?: 0L
                // "index:selected:priority;..." - which files of a torrent the user picked.
                val torrentFiles = extractJsonField(content, "torrentFiles")?.ifBlank { null }
                    ?.split(';')?.mapNotNull { entry ->
                        val f = entry.split(':')
                        val idx = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                        com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry(
                            index = idx,
                            path = "",
                            size = 0L,
                            isSelected = f.getOrNull(1) != "0",
                            priority = f.getOrNull(2)?.toIntOrNull() ?: 0
                        )
                    } ?: emptyList()
                val webSeedUrls = extractJsonField(content, "webSeeds")?.ifBlank { null }
                    ?.split(' ')?.filter { it.isNotBlank() } ?: emptyList()
                val requestHeaders = buildMap {
                    extractJsonField(content, "hdrCookie")?.ifBlank { null }
                        ?.let { put("Cookie", it) }
                    extractJsonField(content, "hdrReferer")?.ifBlank { null }
                        ?.let { put("Referer", it) }
                    extractJsonField(content, "hdrUserAgent")?.ifBlank { null }
                        ?.let { put("User-Agent", it) }
                }

                val engineType = try {
                    EngineType.valueOf(engineTypeName)
                } catch (_: Exception) {
                    EngineType.HTTP_DIRECT
                }
                val rawStatus = try {
                    DownloadStatus.valueOf(statusName)
                } catch (_: Exception) {
                    DownloadStatus.PAUSED
                }
                val status =
                    if (rawStatus == DownloadStatus.DOWNLOADING || rawStatus == DownloadStatus.CONNECTING) DownloadStatus.PAUSED else rawStatus

                val task = DownloadTask(
                    id = id,
                    url = url,
                    fileName = fileName,
                    destinationPath = destinationPath,
                    engineType = engineType,
                    status = status,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    progress = progress,
                    createdAtEpochMs = createdAtEpochMs,
                    formatId = formatId,
                    originalUrl = originalUrl,
                    clipStart = clipStart,
                    clipEnd = clipEnd,
                    clipByCutting = clipByCutting,
                    serverSupportsResume = serverSupportsResume,
                    currentStepText = currentStepText,
                    disableUpload = disableUpload,
                    maxDownloadSpeedBytesPerSec = maxDownloadSpeedBytesPerSec,
                    maxUploadSpeedBytesPerSec = maxUploadSpeedBytesPerSec,
                    requestHeaders = requestHeaders,
                    torrentFiles = torrentFiles,
                    webSeedUrls = webSeedUrls,
                    scheduledAtMs = scheduledAtMs
                )

                if (status == DownloadStatus.QUEUED) {
                    // Was waiting (or just starting) when the app closed: back in the queue.
                    pendingTasks.update { it + (task.id to task) }
                } else when (engineType) {
                    EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task)
                    EngineType.BITTORRENT -> torrentEngine.enqueueTask(task)
                    EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task)
                }
                restored++
            } catch (e: Throwable) {
                // One damaged entry (hand-edited file, interrupted write, odd value) is skipped;
                // the rest of the list still comes back.
                broken++
                println("QueueManager: skipped one unreadable task entry: ${e::class.simpleName}: ${e.message}")
            }
            if (broken > 0) println("QueueManager: restored $restored tasks, skipped $broken broken entries")
            if (restored == 0 && broken > 0) restoreFromBackup()
        } catch (e: Throwable) { // also StackOverflowError/OOM from a corrupt or huge cache
            e.printStackTrace()
            // Keep the damaged file for inspection and fall back to the last good copy.
            try {
                FileSystem.SYSTEM.atomicMove(persistencePath, "$persistencePath.corrupt".toPath())
            } catch (_: Throwable) {
            }
            restoreFromBackup()
        }
    }

    private val backupPath get() = "$persistencePath.bak".toPath()
    private var hasSeenTasks = false

    /** Second attempt with the previous successfully written cache (if any). */
    private fun restoreFromBackup() {
        try {
            if (!FileSystem.SYSTEM.exists(backupPath)) return
            println("QueueManager: restoring tasks from backup")
            FileSystem.SYSTEM.copy(backupPath, persistencePath)
            // Parse the backup with the same per-entry isolation; if it is broken too we end up empty.
            val json = FileSystem.SYSTEM.read(persistencePath) { readUtf8() }
            for (content in splitJsonObjects(json)) try {
                val id = extractJsonField(content, "id") ?: continue
                if (allTasksFlow.value.any { it.id == id }) continue
                val engineType = try {
                    EngineType.valueOf(extractJsonField(content, "engineType") ?: "HTTP_DIRECT")
                } catch (_: Exception) {
                    EngineType.HTTP_DIRECT
                }
                val task = DownloadTask(
                    id = id,
                    url = extractJsonField(content, "url") ?: "",
                    fileName = extractJsonField(content, "fileName") ?: "",
                    destinationPath = extractJsonField(content, "destinationPath")
                        ?: pathResolver.getDownloadsDirectory(),
                    engineType = engineType,
                    status = DownloadStatus.PAUSED,
                    totalBytes = extractJsonField(content, "totalBytes")?.toLongOrNull() ?: 0L,
                    downloadedBytes = extractJsonField(content, "downloadedBytes")?.toLongOrNull()
                        ?: 0L,
                    progress = extractJsonField(content, "progress")?.toFloatOrNull() ?: 0f,
                    createdAtEpochMs = extractJsonField(content, "createdAtEpochMs")?.toLongOrNull()
                        ?: 0L,
                    formatId = extractJsonField(content, "formatId")?.ifBlank { null },
                    originalUrl = extractJsonField(content, "originalUrl")?.ifBlank { null }
                )
                when (engineType) {
                    EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task)
                    EngineType.BITTORRENT -> torrentEngine.enqueueTask(task)
                    EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task)
                }
            } catch (_: Throwable) {
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun observeTasksForPersistence() {
        scope.launch {
            // Persist only what is worth persisting, and not on every 200 ms progress tick:
            // speed/progress changes are collapsed; bytes are written at most once per second.
            allTasksFlow
                .map { tasks ->
                    tasks.map {
                        it.copy(
                            downloadSpeedBytesPerSec = 0L,
                            uploadSpeedBytesPerSec = 0L,
                            chunks = emptyList(),
                            // the merge/save progress line is not worth a disk write every 0.5 s
                            postProcessLabel = null,
                            postProcessProgress = -1f,
                            postProcessStartedAtMs = 0L,
                            queuePosition = 0
                        )
                    }
                }
                .distinctUntilChanged()
                .conflate()
                .collect { tasks ->
                    delay(1000)
                try {
                    if (tasks.isEmpty()) {
                        // Only delete once we have seen real tasks in this run: the very first
                        // emission can be the empty initial state before the restore lands.
                        if (hasSeenTasks && FileSystem.SYSTEM.exists(persistencePath)) {
                            FileSystem.SYSTEM.delete(persistencePath)
                        }
                    } else {
                        hasSeenTasks = true
                        val jsonArray = tasks.joinToString(
                            separator = ",",
                            prefix = "[",
                            postfix = "]"
                        ) { task ->
                            val safeId = escapeJson(task.id)
                            val safeUrl = escapeJson(task.url)
                            val safeFileName = escapeJson(task.fileName)
                            val safeDest = escapeJson(task.destinationPath)
                            val safeOriginalUrl = escapeJson(task.originalUrl ?: "")
                            val safeFormatId = escapeJson(task.formatId ?: "")
                            val safeClipStart = escapeJson(task.clipStart ?: "")
                            val safeClipEnd = escapeJson(task.clipEnd ?: "")
                            val safeStepText = escapeJson(task.currentStepText ?: "")
                            val safeCookie = escapeJson(task.requestHeaders["Cookie"] ?: "")
                            val safeReferer = escapeJson(task.requestHeaders["Referer"] ?: "")
                            val safeUa = escapeJson(task.requestHeaders["User-Agent"] ?: "")
                            val safeTorrentFiles = escapeJson(task.torrentFiles.joinToString(";") { "${it.index}:${if (it.isSelected) 1 else 0}:${it.priority}" })
                            val safeWebSeeds = escapeJson(task.webSeedUrls.joinToString(" "))
                            """{"id":"$safeId","url":"$safeUrl","fileName":"$safeFileName","destinationPath":"$safeDest","engineType":"${task.engineType.name}","status":"${task.status.name}","totalBytes":${task.totalBytes},"downloadedBytes":${task.downloadedBytes},"progress":${task.progress},"createdAtEpochMs":${task.createdAtEpochMs},"formatId":"$safeFormatId","originalUrl":"$safeOriginalUrl","clipStart":"$safeClipStart","clipEnd":"$safeClipEnd","currentStepText":"$safeStepText","disableUpload":${task.disableUpload},"clipByCutting":${task.clipByCutting},"serverSupportsResume":"${task.serverSupportsResume ?: ""}","maxDownloadSpeedBytesPerSec":${task.maxDownloadSpeedBytesPerSec},"maxUploadSpeedBytesPerSec":${task.maxUploadSpeedBytesPerSec},"hdrCookie":"$safeCookie","hdrReferer":"$safeReferer","hdrUserAgent":"$safeUa","torrentFiles":"$safeTorrentFiles","webSeeds":"$safeWebSeeds","scheduledAtMs":${task.scheduledAtMs}}"""
                        }
                        // Atomic: a crash mid-write used to leave a half file and lose every task.
                        val tmp = "$persistencePath.tmp".toPath()
                        FileSystem.SYSTEM.write(tmp) {
                            writeUtf8(jsonArray)
                        }
                        // The file being replaced was readable (we loaded it): keep it as the backup.
                        if (FileSystem.SYSTEM.exists(persistencePath)) {
                            try {
                                FileSystem.SYSTEM.copy(persistencePath, backupPath)
                            } catch (_: Exception) {
                            }
                        }
                        FileSystem.SYSTEM.atomicMove(tmp, persistencePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun splitJsonObjects(jsonArrayStr: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var escape = false

        for (i in jsonArrayStr.indices) {
            val c = jsonArrayStr[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) startIndex = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        result.add(jsonArrayStr.substring(startIndex, i + 1))
                        startIndex = -1
                    }
                }
            }
        }
        return result
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') {
                        sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                when (val next = str[i + 1]) {
                    '\\' -> {
                        sb.append('\\'); i += 2
                    }

                    '"' -> {
                        sb.append('"'); i += 2
                    }

                    'b' -> {
                        sb.append('\b'); i += 2
                    }

                    'f' -> {
                        sb.append('\u000c'); i += 2
                    }

                    'n' -> {
                        sb.append('\n'); i += 2
                    }

                    'r' -> {
                        sb.append('\r'); i += 2
                    }

                    't' -> {
                        sb.append('\t'); i += 2
                    }

                    'u' -> {
                        if (i + 5 < str.length) {
                            val hex = str.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append("\\u")
                                i += 2
                            }
                        } else {
                            sb.append("\\u")
                            i += 2
                        }
                    }

                    else -> {
                        sb.append(next); i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Reads `"field": "string"` or `"field": number|true|false|null` from one JSON object.
     *
     * Hand-rolled scanner: the previous regex `((?:\\.|[^"\\])*)` recurses once per character in
     * java.util.regex and threw StackOverflowError at start-up as soon as a task carried a long
     * value (the browser's Cookie header is often several KB) - the window stayed blank.
     */
    private fun extractJsonField(jsonObj: String, fieldName: String): String? {
        val key = "\"$fieldName\""
        var from = 0
        while (true) {
            val k = jsonObj.indexOf(key, from)
            if (k < 0) return null
            var i = k + key.length
            while (i < jsonObj.length && jsonObj[i].isWhitespace()) i++
            if (i >= jsonObj.length || jsonObj[i] != ':') {
                from = k + 1; continue
            } // key inside a value
            i++
            while (i < jsonObj.length && jsonObj[i].isWhitespace()) i++
            if (i >= jsonObj.length) return null
            if (jsonObj[i] == '"') {
                val sb = StringBuilder()
                var j = i + 1
                while (j < jsonObj.length) {
                    val c = jsonObj[j]
                    if (c == '\\' && j + 1 < jsonObj.length) {
                        sb.append(c).append(jsonObj[j + 1]); j += 2; continue
                    }
                    if (c == '"') return unescapeJson(sb.toString())
                    sb.append(c); j++
                }
                return null // unterminated string
            }
            val endIdx = jsonObj.indexOfAny(charArrayOf(',', '}'), i)
                .let { if (it < 0) jsonObj.length else it }
            return jsonObj.substring(i, endIdx).trim()
        }
    }

    private fun parseSimpleTimeSec(timeStr: String?): Double {
        if (timeStr.isNullOrBlank()) return 0.0
        val parts = timeStr.trim().removePrefix("-").split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toDoubleOrNull() ?: 0.0) * 3600.0 + (parts[1].toDoubleOrNull()
                    ?: 0.0) * 60.0 + (parts[2].toDoubleOrNull() ?: 0.0)

                2 -> (parts[0].toDoubleOrNull() ?: 0.0) * 60.0 + (parts[1].toDoubleOrNull() ?: 0.0)
                1 -> parts[0].toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }
}

/** Real wall-clock time; the old `taskSequence*1000+random` made "sort by date" random. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
