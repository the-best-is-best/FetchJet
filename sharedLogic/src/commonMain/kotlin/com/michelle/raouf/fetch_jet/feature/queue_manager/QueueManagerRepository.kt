package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.http_downloader.HttpDownloadEngine
import com.michelle.raouf.fetch_jet.feature.http_downloader.createHttpEngine
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaStreamDownloader
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentDownloader
import com.tbib.kmp_localization.LocalizationManager
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

    private val _globalUploadSpeedLimit = MutableStateFlow(0L)

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
                errorMessage = e.message ?: LocalizationManager.Strings.sysCouldNotStart
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
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        com.michelle.raouf.fetch_jet.core.log.AppLog.d { "Inspect: begin $url" }
        return mediaEngine.inspectMediaQualities(url, requestHeaders).also { list ->
            com.michelle.raouf.fetch_jet.core.log.AppLog.d {
                "Inspect: end in ${started.elapsedNow().inWholeMilliseconds}ms, ${list.size} qualities"
            }
        }
    }

    /** The videos of a playlist / channel link (null = not a playlist). */
    suspend fun inspectPlaylist(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? =
        com.michelle.raouf.fetch_jet.feature.media_extractor.inspectPlatformPlaylist(url, headers)

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
        sortByType: Boolean = true,
        /** Start at this time (epoch ms); 0 = now (or as soon as a slot is free). */
        scheduledAtMs: Long = 0L,
        /** Connections for a direct download; 0 = app setting / automatic. */
        maxConnections: Int = 0,
        pairedAudioUrl: String? = null
    ): Result<DownloadTask> {
        // A quoted / file:// .torrent path is stored as a plain path.
        val trimmedUrl = url.trim().let {
            if (com.michelle.raouf.fetch_jet.feature.torrent_engine.isLocalTorrentPath(it))
                com.michelle.raouf.fetch_jet.feature.torrent_engine.normalizeLocalTorrentPath(it)
            else codeHostRawUrl(it)
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
        val baseDir =
            if (!customDestinationPath.isNullOrBlank()) customDestinationPath else pathResolver.getDownloadsDirectory()
        // "Sort into folders": Video / Music / Archives / ... under the chosen folder, by file type.
        val destDir =
            if (sortByType && com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.sortIntoFolders.value) {
                val nameForType = (customFileName ?: torrentName ?: targetUrl.substringBefore('?')
                    .substringAfterLast('/'))
                com.michelle.raouf.fetch_jet.core.settings.FileCategory.folderFor(
                    nameForType,
                    isMedia = engineType == EngineType.MEDIA_STREAM,
                    isTorrent = engineType == EngineType.BITTORRENT
                )?.let { sub ->
                    val trimmed = baseDir.trimEnd('/', '\\')
                    // Already points at a type folder (the Add dialog shows the real target) → use as is.
                    val known = com.michelle.raouf.fetch_jet.core.settings.FileCategory.allFolders
                    if (known.any { trimmed.endsWith("/$it") || trimmed.endsWith("\\$it") }) baseDir
                    else trimmed + "/" + sub
                } ?: baseDir
            } else baseDir

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
            pairedAudioUrl = pairedAudioUrl?.takeIf { it.isNotBlank() },
            clipStart = clipStart,
            clipEnd = clipEnd,
            clipByCutting = cutAfterDownload,
            approximateTotalBytes = initialApproximateBytes,
            createdAtEpochMs = nowEpochMs(),
            requestHeaders = requestHeaders.filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() },
            scheduledAtMs = scheduledAtMs.takeIf { it > nowEpochMs() } ?: 0L,
            maxConnections = maxConnections.coerceIn(0, 32)
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
            val text = FileSystem.SYSTEM.read(persistencePath) { readUtf8() }
            var restored = 0
            var broken = 0
            val entries = TaskCache.decode(text) { e ->
                broken++
                println("QueueManager: skipped one unreadable task entry: ${e::class.simpleName}: ${e.message}")
            }
            for (entry in entries) try {
                val task = entry.toTask(pathResolver.getDownloadsDirectory())
                if (task.status == DownloadStatus.QUEUED) {
                    pendingTasks.update { it + (task.id to task) }
                } else when (task.engineType) {
                    EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task)
                    EngineType.BITTORRENT -> torrentEngine.enqueueTask(task)
                    EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task)
                }
                restored++
            } catch (e: Throwable) {
                broken++
                println("QueueManager: skipped one unreadable task entry: ${e::class.simpleName}: ${e.message}")
            }
            if (broken > 0) println("QueueManager: restored $restored tasks, skipped $broken broken entries")
            if (restored == 0 && broken > 0) restoreFromBackup()
        } catch (e: Throwable) {
            e.printStackTrace()
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
            val text = FileSystem.SYSTEM.read(persistencePath) { readUtf8() }
            for (entry in TaskCache.decode(text)) try {
                if (allTasksFlow.value.any { it.id == entry.id }) continue
                val task = entry.toTask(pathResolver.getDownloadsDirectory(), DownloadStatus.PAUSED)
                when (task.engineType) {
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
                            FileSystem.SYSTEM.delete(backupPath, mustExist = false)
                        }
                    } else {
                        hasSeenTasks = true
                        val jsonArray = TaskCache.encode(tasks)
                        // Atomic: a crash mid-write used to leave a half file and lose every task.
                        val tmp = "$persistencePath.tmp".toPath()
                        FileSystem.SYSTEM.write(tmp) {
                            writeUtf8(jsonArray)
                        }
                        // The file being replaced was readable (we loaded it): keep it as the backup.
                        if (FileSystem.SYSTEM.exists(persistencePath)) {
                            try {
                                // A file from before encryption holds plain links and cookies:
                                // the backup gets the new (encrypted) list instead.
                                val old = FileSystem.SYSTEM.read(persistencePath) { readUtf8() }
                                if (old.contains("\"url\":\"http")) {
                                    FileSystem.SYSTEM.write(backupPath) { writeUtf8(jsonArray) }
                                } else {
                                    FileSystem.SYSTEM.copy(persistencePath, backupPath)
                                }
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

internal fun codeHostRawUrl(url: String): String {
    val github = Regex(
        """^(https?://(?:www\.)?github\.com/[^/]+/[^/]+)/blob/(.+)$""",
        RegexOption.IGNORE_CASE
    )
    github.matchEntire(url)?.let { return "${it.groupValues[1]}/raw/${it.groupValues[2]}" }
    val gitlab =
        Regex("""^(https?://(?:www\.)?gitlab\.com/.+?/-)/blob/(.+)$""", RegexOption.IGNORE_CASE)
    gitlab.matchEntire(url)?.let { return "${it.groupValues[1]}/raw/${it.groupValues[2]}" }
    return url
}
