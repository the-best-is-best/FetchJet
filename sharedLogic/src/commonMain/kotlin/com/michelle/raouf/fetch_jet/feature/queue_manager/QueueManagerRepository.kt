package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.core.util.verifyFileChecksum
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.hidden.logName
import com.michelle.raouf.fetch_jet.feature.hidden.shownName
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.http_downloader.HttpDownloadEngine
import com.michelle.raouf.fetch_jet.feature.http_downloader.createHttpEngine
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaStreamDownloader
import com.michelle.raouf.fetch_jet.feature.media_extractor.addSubtitlesToFinished
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath
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
            if (!AppFileSystem.exists(newPath) && AppFileSystem.exists(oldPath)) {
                AppFileSystem.copy(oldPath, newPath)
                AppFileSystem.delete(oldPath)
            }
        } catch (_: Exception) {
        }
        newPath
    }

    private val _globalDownloadSpeedLimit = MutableStateFlow(0L)
    val globalDownloadSpeedLimit: StateFlow<Long> = _globalDownloadSpeedLimit.asStateFlow()

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
        val configuredLimit = _globalDownloadSpeedLimit.value
        val onlyInUse = configuredLimit > 0L &&
                com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.speedLimitOnlyInUse.value
        val globalLimit = com.michelle.raouf.fetch_jet.core.power.effectiveLimit(
            configuredLimit,
            onlyInUse,
            if (onlyInUse) com.michelle.raouf.fetch_jet.core.power.UserIdle.idleMs() else null
        )

        if (globalLimit <= 0L && taskLimit <= 0L) return 0L

        val activeTasks = allTasks.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING
        }
        // The global limit is shared by priority weight: High gets the biggest slice, Low the
        // smallest (High:Normal:Low = 3:2:1). Recomputed live, so changing a running download's
        // priority re-splits the speed at once without pausing anything.
        val priorityWeight = { p: Int -> if (p > 0) 3L else if (p < 0) 1L else 2L }
        val totalWeight = activeTasks.sumOf { priorityWeight(it.priority) }.coerceAtLeast(1L)
        val myWeight = priorityWeight(task?.priority ?: 0)
        val perTaskGlobal =
            if (globalLimit > 0L) maxOf(16 * 1024L, globalLimit * myWeight / totalWeight) else 0L

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

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskSequence = MutableStateFlow(0L)

    internal fun generateUniqueTaskId(): String {
        val sequence = taskSequence.updateAndGet { it + 1 }
        val randomNum = kotlin.random.Random.nextLong(100000L, 999999L)
        return "task_${sequence}_$randomNum"
    }

    // ---- Queue & schedule ------------------------------------------------------------------
    // Downloads that wait (for their start time, or for a free slot) live here, not in an
    // engine: an engine only ever sees a download when it really starts.

    /** Waiting downloads, by id. */
    internal val pendingTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    private val queueSettingsFile get() = "${pathResolver.getSettingsDirectory()}/queue.properties".toPath()

    private val _maxConcurrentDownloads = MutableStateFlow(loadMaxConcurrent())

    /** How many downloads run at the same time; 0 = no limit. */
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    fun setMaxConcurrentDownloads(count: Int) {
        _maxConcurrentDownloads.value = count.coerceIn(0, 20)
        try {
            queueSettingsFile.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(queueSettingsFile) {
                writeUtf8("# FetchJet queue settings\n")
                writeUtf8("maxConcurrentDownloads=${_maxConcurrentDownloads.value}\n")
            }
        } catch (_: Exception) {
        }
        pumpQueue()
    }

    private fun loadMaxConcurrent(): Int = try {
        if (!AppFileSystem.exists(queueSettingsFile)) 3
        else AppFileSystem.read(queueSettingsFile) { readUtf8() }.lineSequence()
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
                    .sortedWith(
                        compareByDescending<DownloadTask> { it.priority }
                            .thenBy { it.createdAtEpochMs }
                    )
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

    /** Checksum verdicts overlaid onto the engine tasks (true = match, false = mismatch). */
    private val _checksumResults = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private class ExtractState(val fraction: Float, val startedAt: Long)
    private val extractState = MutableStateFlow<Map<String, ExtractState>>(emptyMap())
    private val scanState = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val scanResults = MutableStateFlow<Map<String, String>>(emptyMap())
    private val checksumVerifyingIds = mutableSetOf<String>()

    private val baseTasksFlow: kotlinx.coroutines.flow.Flow<List<DownloadTask>> = combine(
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
            .sortedWith(
                compareByDescending<DownloadTask> { it.priority }
                    .thenBy { it.createdAtEpochMs }
            )
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
    }

    private val overlaidTasksFlow = combine(
        baseTasksFlow,
        _checksumResults,
        extractState,
        scanState,
        scanResults
    ) { tasks, results, extracting, scanning, scanned ->
        val withChecksums =
            if (results.isEmpty() && scanned.isEmpty()) tasks
            else tasks.map {
                val checked = if (it.id in results) it.copy(checksumVerified = results[it.id]) else it
                scanned[it.id]?.let { verdict -> checked.copy(scanResult = verdict) } ?: checked
            }
        if (extracting.isEmpty() && scanning.isEmpty()) withChecksums
        else withChecksums.map { task ->
            scanning[task.id]?.let { since ->
                return@map task.copy(
                    postProcessLabel = LocalizationManager.Strings.scanningState,
                    postProcessProgress = -1f,
                    postProcessStartedAtMs = since
                )
            }
            val state = extracting[task.id] ?: return@map task
            task.copy(
                postProcessLabel = LocalizationManager.Strings.extractingState((state.fraction * 100).toInt()),
                postProcessProgress = state.fraction,
                postProcessStartedAtMs = state.startedAt
            )
        }
    }

    val allTasksFlow: StateFlow<List<DownloadTask>> = combine(
        overlaidTasksFlow,
        HiddenStore.state,
        HiddenWork.leaving
    ) { tasks, hiddenState, leaving ->
        if (hiddenState.ids.isEmpty() && hiddenState.playlists.isEmpty()) tasks
        else tasks.map {
            when {
                it.id in leaving -> {
                    val origin = HiddenStore.originOf(it.id)
                    if (origin != null && HiddenFiles.isPrivate(pathResolver, it.destinationPath)) it.copy(destinationPath = origin) else it
                }
                hiddenState.hides(it) -> it.copy(hidden = true)
                else -> it
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val visibleTasksFlow: StateFlow<List<DownloadTask>> = allTasksFlow
        .map { tasks -> tasks.filter { !it.hidden } }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val _archiveEvents = kotlinx.coroutines.flow.MutableSharedFlow<com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveEvent>(extraBufferCapacity = 16)

    /** One event per archive the app opened (or failed to open) after its download finished. */
    val archiveEvents: kotlinx.coroutines.flow.SharedFlow<com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveEvent> =
        _archiveEvents.asSharedFlow()

    private val _scanEvents = kotlinx.coroutines.flow.MutableSharedFlow<com.michelle.raouf.fetch_jet.feature.file_scan.ScanEvent>(extraBufferCapacity = 16)

    val scanEvents: kotlinx.coroutines.flow.SharedFlow<com.michelle.raouf.fetch_jet.feature.file_scan.ScanEvent> =
        _scanEvents.asSharedFlow()

    private val unfinishedSeen = mutableSetOf<String>()
    private val extractingIds = MutableStateFlow<Set<String>>(emptySet())

    private val activeRunningTaskIds = mutableSetOf<String>()
    private val completedNotifiedTaskIds = mutableSetOf<String>()
    private val runningPlaylists = mutableMapOf<String, String>()

    /**
     * One notice when every video of a playlist that ran in this session is done (finished or
     * failed), instead of one per video. A list paused or still waiting is not done yet.
     */
    private fun notifyFinishedPlaylists(tasks: List<DownloadTask>) {
        for (task in tasks) {
            if (task.playlistId.isNotEmpty() &&
                (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING)
            ) runningPlaylists[task.playlistId] = task.playlistTitle
        }
        val finished = runningPlaylists.keys.filter { id ->
            tasks.filter { it.playlistId == id }.all { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.ERROR }
        }
        for (id in finished) {
            val title = runningPlaylists.remove(id).orEmpty()
            val members = tasks.filter { it.playlistId == id }
            if (members.isEmpty()) continue
            if (members.all { it.hidden }) continue
            val done = members.count { it.status == DownloadStatus.COMPLETED }
            val failed = members.size - done
            val text = com.michelle.raouf.fetch_jet.feature.media_extractor.playlistDoneText(title, done, failed)
            println("Playlist done: $title ($done finished, $failed failed)")
            com.michelle.raouf.fetch_jet.core.notification.SystemNotice.show(
                "playlist-$id", LocalizationManager.Strings.sysPlaylistDoneTitle, text
            )
        }
    }

    internal val hideLock = Mutex()

    internal val finishWatch = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        HiddenStore.ensureLoaded()
        observeTasksForNotifications()
        observeArchiveExtraction()
        observeChecksumVerification()
        loadAndRestoreTasks()
        observeTasksForPersistence()
        observeHiddenTasks()
        // Starts queued / scheduled downloads when a slot frees up or their time comes.
        scope.launch {
            while (true) {
                delay(1000)
                if (pendingTasks.value.isNotEmpty() || resumeQueue.value.isNotEmpty()) pumpQueue()
            }
        }
        scope.launch {
            combine(
                httpEngine.getAllTasksFlow(),
                torrentEngine.getAllTasksFlow(),
                mediaEngine.getAllTasksFlow()
            ) { http, torrent, media ->
                var running = 0
                for (map in arrayOf(http, torrent, media)) {
                    for (t in map.values) {
                        if (t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.CONNECTING ||
                            t.status == DownloadStatus.QUEUED
                        ) running++
                    }
                }
                running
            }.distinctUntilChanged().collect {
                if ((pendingTasks.value.isNotEmpty() || resumeQueue.value.isNotEmpty()) && hasFreeSlot()) pumpQueue()
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
                    if (task.hidden) {
                        if (activeRunningTaskIds.remove(task.id)) notificationManager.cancelNotification(task.id)
                        return@forEach
                    }
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING) {
                        activeRunningTaskIds.add(task.id)
                        completedNotifiedTaskIds.remove(task.id)
                        val percent = (task.progress * 100).toInt()
                        val speedStr = formatSpeed(task.downloadSpeedBytesPerSec)
                        val name = task.fileName.ifBlank {
                            com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(task.url).substringAfterLast("/")
                        }
                        notificationManager.showOrUpdateNotification(
                            taskId = task.id,
                            fileName = name,
                            progressPercent = percent,
                            speedText = speedStr,
                            downloadedBytes = task.downloadedBytes,
                            totalBytes = task.totalBytes
                        )
                    } else if (task.status == DownloadStatus.COMPLETED) {
                        if (task.playlistId.isNotEmpty() && activeRunningTaskIds.contains(task.id)) {
                            completedNotifiedTaskIds.add(task.id)
                            activeRunningTaskIds.remove(task.id)
                            notificationManager.cancelNotification(task.id)
                        } else if (activeRunningTaskIds.contains(task.id) && !completedNotifiedTaskIds.contains(
                                task.id
                            )
                        ) {
                            completedNotifiedTaskIds.add(task.id)
                            activeRunningTaskIds.remove(task.id)
                            val name = task.fileName.ifBlank {
                            com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(task.url).substringAfterLast("/")
                        }
                            notificationManager.showOrUpdateNotification(
                                taskId = task.id,
                                fileName = name,
                                progressPercent = 100,
                                speedText = "Completed",
                                downloadedBytes = task.totalBytes,
                                totalBytes = task.totalBytes
                            )
                        }
                    } else if (activeRunningTaskIds.remove(task.id)) {
                        // Paused, failed, back in line (QUEUED / IDLE): no longer downloading.
                        notificationManager.cancelNotification(task.id)
                    }
                }
                notifyFinishedPlaylists(tasks)
            }
        }
    }

    /**
     * When a download with a user-supplied checksum finishes, hash the file once and publish the
     * verdict (true = match, false = mismatch) into [_checksumResults], which allTasksFlow overlays.
     * Runs once per task; a restored task that already has a stored verdict is left alone.
     */
    private fun observeArchiveExtraction() {
        scope.launch {
            allTasksFlow.collect { tasks ->
                for (task in tasks) {
                    if (task.status == DownloadStatus.ERROR && task.scanResult == null && task.id !in scanResults.value &&
                        task.errorMessage?.contains(com.michelle.raouf.fetch_jet.feature.file_scan.ANTIVIRUS_BLOCKED_ERROR) == true
                    ) {
                        scanResults.update { it + (task.id to com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome.THREAT.code) }
                        _scanEvents.tryEmit(
                            com.michelle.raouf.fetch_jet.feature.file_scan.ScanEvent(
                                task.fileName, com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome.THREAT, "blocked while downloading"
                            )
                        )
                    }
                    if (task.status != DownloadStatus.COMPLETED) {
                        unfinishedSeen.add(task.id)
                        continue
                    }
                    if (!unfinishedSeen.remove(task.id)) continue
                    if (task.engineType == EngineType.BITTORRENT || task.fileName.isBlank()) continue
                    runCatching {
                        com.michelle.raouf.fetch_jet.feature.file_scan.FileScanner.markFromInternet(
                            task.destinationPath.trimEnd('/', '\\') + "/" + task.fileName,
                            if (task.hidden) "" else task.url,
                            if (task.hidden) null else task.originalUrl ?: task.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                        )
                    }
                    val scan = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.scanFiles.value &&
                            com.michelle.raouf.fetch_jet.feature.file_scan.FileScanner.supported
                    val target = extractTargetFor(task, tasks)
                    val extract = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.extractArchives.value && target != null
                    if (!scan && !extract) continue
                    scope.launch {
                        val outcome = if (scan) scanFinished(task) else null
                        val unsafe = outcome == com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome.THREAT ||
                                outcome == com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome.BLOCKED
                        if (extract && !unsafe && target != null) {
                            extractFinished(
                                target,
                                quietIfIncomplete = com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.isVolume(target.fileName)
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun scanFinished(task: DownloadTask): com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome? {
        if (task.id in scanState.value) return null
        scanState.update { it + (task.id to com.michelle.raouf.fetch_jet.core.util.currentEpochMs()) }
        try {
            val path = task.destinationPath.trimEnd('/', '\\') + "/" + task.fileName
            val result = try {
                com.michelle.raouf.fetch_jet.feature.file_scan.FileScanner.scan(
                    path,
                    task.url,
                    com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.scanCommand.value
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.michelle.raouf.fetch_jet.feature.file_scan.ScanResult(
                    com.michelle.raouf.fetch_jet.feature.file_scan.ScanOutcome.FAILED, e.message ?: "error"
                )
            }
            com.michelle.raouf.fetch_jet.core.log.AppLog.d { "Scan ${task.logName()}: ${result.outcome.code} ${if (task.hidden) "" else result.detail}" }
            scanResults.update { it + (task.id to result.outcome.code) }
            _scanEvents.emit(
                com.michelle.raouf.fetch_jet.feature.file_scan.ScanEvent(task.shownName(), result.outcome, if (task.hidden) "" else result.detail)
            )
            return result.outcome
        } finally {
            scanState.update { it - task.id }
        }
    }

    private fun extractTargetFor(task: DownloadTask, tasks: List<DownloadTask>): DownloadTask? {
        val set = com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.volumeSet(task.fileName)
        if (set == null) return task.takeIf { com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveTasks.isArchive(it) }
        val folder = task.destinationPath.trimEnd('/', '\\')
        val parts = tasks.filter {
            it.destinationPath.trimEnd('/', '\\') == folder &&
                    com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.volumeSet(it.fileName) == set
        }
        if (parts.any { it.status != DownloadStatus.COMPLETED }) return null
        val numbers = parts.mapNotNull { com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.volumeNumber(it.fileName) }.toSet()
        if (numbers.isEmpty() || (1..numbers.max()).any { it !in numbers }) return null
        return parts.firstOrNull { com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.volumeNumber(it.fileName) == 1 }
            ?.takeIf { com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveTasks.isArchive(it) }
    }

    private suspend fun extractFinished(
        task: DownloadTask,
        openWhenDone: Boolean = false,
        password: String? = null,
        quietIfIncomplete: Boolean = false,
    ) {
        if (task.id in extractingIds.value) return
        extractingIds.update { it + task.id }
        val startedAt = com.michelle.raouf.fetch_jet.core.util.currentEpochMs()
        extractState.update { it + (task.id to ExtractState(0f, startedAt)) }
        try {
            val path = (task.destinationPath.trimEnd('/', '\\') + "/" + task.fileName).toPath()
            if (!quietIfIncomplete) {
                _archiveEvents.emit(com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveEvent(task.shownName(), 0, null, started = true))
            }
            val event = try {
                val result = com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.extract(
                    path, password = password, place = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.extractPlace.value
                ) { fraction ->
                    val shown = extractState.value[task.id]?.fraction ?: 0f
                    if (fraction - shown >= 0.01f || fraction >= 1f) {
                        extractState.update { it + (task.id to ExtractState(fraction, startedAt)) }
                    }
                }
                result.items.forEach { markExtracted(task, it) }
                if (!task.hidden && com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.trashArchiveAfterExtract.value &&
                    com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.isSupported
                ) {
                    com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.moveToTrash(path.toString())
                }
                com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveEvent(task.shownName(), result.files, null, destination = result.destination)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.michelle.raouf.fetch_jet.core.log.AppLog.d { "Extract failed for ${task.logName()}: ${if (task.hidden) e::class.simpleName else e.message}" }
                com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveEvent(
                    task.shownName(), 0,
                    (e.message ?: "error").let {
                        if (task.hidden && it != com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.MISSING_PART) "error" else it
                    },
                    taskId = task.id,
                    needsPassword = e is com.michelle.raouf.fetch_jet.feature.archive_extract.ArchivePasswordException,
                    wrongPassword = (e as? com.michelle.raouf.fetch_jet.feature.archive_extract.ArchivePasswordException)?.wrong == true,
                )
            }
            if (quietIfIncomplete && event.error == com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor.MISSING_PART) return
            _archiveEvents.emit(event)
            if (openWhenDone && event.error == null && event.destination.isNotBlank()) {
                try {
                    pathResolver.openFileLocation(event.destination)
                } catch (_: Throwable) {
                }
            }
        } finally {
            extractingIds.update { it - task.id }
            extractState.update { it - task.id }
        }
    }

    private fun markExtracted(task: DownloadTask, destination: String) {
        val page = if (task.hidden) null else task.originalUrl ?: task.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
        val source = if (task.hidden) "" else task.url
        val top = destination.toPath()
        runCatching { com.michelle.raouf.fetch_jet.feature.file_scan.FileScanner.markFromInternet(top.toString(), source, page) }
        val isFolder = runCatching {
            com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem.metadataOrNull(top)?.isDirectory == true
        }.getOrDefault(false)
        if (!isFolder) return
        runCatching {
            com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem.listRecursively(top).forEach { path ->
                runCatching { com.michelle.raouf.fetch_jet.feature.file_scan.FileScanner.markFromInternet(path.toString(), source, page) }
            }
        }
    }

    /** The Extract button: opens a finished archive now, whatever the "extract when they finish" setting says. */
    fun extractNow(taskId: String, password: String? = null) {
        val task = allTasksFlow.value.firstOrNull { it.id == taskId } ?: return
        if (!com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveTasks.isArchive(task)) return
        scope.launch { extractFinished(task, openWhenDone = true, password = password) }
    }

    private fun observeChecksumVerification() {
        scope.launch {
            allTasksFlow.collect { tasks ->
                tasks.forEach { task ->
                    val needsCheck = task.status == DownloadStatus.COMPLETED &&
                            !task.expectedChecksum.isNullOrBlank() &&
                            task.checksumVerified == null &&
                            task.id !in _checksumResults.value &&
                            task.id !in checksumVerifyingIds
                    if (needsCheck) {
                        checksumVerifyingIds.add(task.id)
                        val path = task.destinationPath.trimEnd('/', '\\') + "/" + task.fileName
                        scope.launch {
                            val ok = verifyFileChecksum(path, task.expectedChecksum)
                            if (ok != null) _checksumResults.update { it + (task.id to ok) }
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
            if (list.isNotEmpty() && list.any { it.directUrl != url.trim() }) LearnedMediaHosts.learn(url)
        }
    }

    /** The last [inspectMediaQualities] gave nothing because the video is DRM-protected. */
    val lastInspectDrm: Boolean
        get() = mediaEngine.lastInspectDrm ||
                com.michelle.raouf.fetch_jet.feature.media_extractor.MediaDrm.errorSaysDrm(
                    com.michelle.raouf.fetch_jet.feature.media_extractor.lastMediaInspectionError()
                )

    /** Subtitle tracks the last [inspectMediaQualities] found (for the Add dialog). */
    val lastMediaSubtitles: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack>
        get() = mediaEngine.lastSubtitles

    /** The subtitle tracks the page of a finished video offers now (fresh links). */
    suspend fun inspectSubtitlesOf(task: DownloadTask): List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> {
        val page = task.originalUrl?.takeIf { it.isNotBlank() } ?: task.url
        if (page.startsWith("fjenc1:")) throw IllegalStateException("The link of this download cannot be read on this device")
        inspectMediaQualities(page, task.requestHeaders)
        return mediaEngine.lastSubtitles
    }

    suspend fun addSubtitles(
        taskId: String,
        tracks: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack>,
        onTrackDone: (Int) -> Unit = {}
    ): com.michelle.raouf.fetch_jet.feature.media_extractor.SubtitleAddResult? {
        val task = allTasksFlow.value.firstOrNull { it.id == taskId } ?: return null
        val result = mediaEngine.addSubtitlesToFinished(task, tracks, onTrackDone)
        if (result.added.isNotEmpty()) {
            val current = allTasksFlow.value.firstOrNull { it.id == taskId } ?: task
            val addedKeys = result.added.map { it.langCode to it.isAuto }.toSet()
            val merged = current.subtitleTracks.filter { (it.langCode to it.isAuto) !in addedKeys } +
                    result.added.map { it.copy(isSelected = true) }
            val changed = current.copy(subtitleTracks = merged)
            when (current.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(changed)
                EngineType.BITTORRENT -> Unit
                EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(changed)
            }
        }
        return result
    }

    /** Subtitle languages a media task picks up when it downloads (playlist "Add language"). */
    fun setSubtitleLanguages(taskId: String, languages: String) {
        val pending = pendingTasks.value[taskId]
        if (pending != null) pendingTasks.update { it + (taskId to pending.copy(subtitleLanguages = languages)) }
        else mediaEngine.updateTaskState(taskId) { it.copy(subtitleLanguages = languages) }
    }

    /** The videos of a playlist / channel link (null = not a playlist). */
    suspend fun inspectPlaylist(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? =
        com.michelle.raouf.fetch_jet.feature.media_extractor.inspectPlatformPlaylist(url, headers)

    private fun trashInsteadOfDelete(task: DownloadTask, path: String): Boolean =
        !task.hidden && task.status == DownloadStatus.COMPLETED &&
                com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.deleteToTrash.value &&
                com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.isSupported &&
                com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.moveToTrash(path)

    private fun putOnEngine(task: DownloadTask) {
        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task)
            EngineType.BITTORRENT -> Unit
            EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task)
        }
    }

    /** Removes the tasks with their clips and compressed copies (the list's Remove). */
    suspend fun removeTasksWithDerived(taskIds: List<String>, deleteFile: Boolean) {
        val copies = com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCopiesStore.let { it.load(); it.counts.value }
        val plan = com.michelle.raouf.fetch_jet.feature.derived.DerivedItems.removalPlan(taskIds, allTasksFlow.value, copies)
        (taskIds.distinct() + plan.clipIds).forEach { removeTask(it, deleteFile) }
        plan.copies.forEach { (owner, paths) ->
            paths.forEach { path ->
                if (deleteFile) {
                    val gone = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.deleteToTrash.value &&
                            com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.isSupported &&
                            com.michelle.raouf.fetch_jet.core.filesystem.SystemTrash.moveToTrash(path)
                    if (!gone) runCatching { AppFileSystem.delete(path.toPath()) }
                }
                com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCopiesStore.remove(owner, path)
            }
        }
    }

    /** A clip that replaces the file it was cut from: it takes the source's link and the source goes. */
    suspend fun promoteClip(clip: DownloadTask, sourceId: String): DownloadTask? {
        val source = allTasksFlow.value.firstOrNull { it.id == sourceId } ?: return null
        val promoted = com.michelle.raouf.fetch_jet.feature.derived.DerivedItems.promotedClip(clip, source)
        if (promoted.engineType != clip.engineType) {
            when (clip.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.cancel(clip.id)
                EngineType.BITTORRENT -> Unit
                EngineType.MEDIA_STREAM -> mediaEngine.cancel(clip.id)
            }
        }
        putOnEngine(promoted)
        removeTask(source.id, deleteFile = true)
        return promoted
    }

    /** The compressed copy takes the original's place in the same task; the original file goes. */
    fun replaceWithCompressed(taskId: String, copyPath: String): DownloadTask? {
        val task = allTasksFlow.value.firstOrNull { it.id == taskId } ?: return null
        val copy = copyPath.toPath()
        val bytes = AppFileSystem.metadataOrNull(copy)?.size ?: return null
        val old = "${task.destinationPath.trimEnd('/', '\\')}/${task.fileName}"
        if (old.toPath() != copy && AppFileSystem.exists(old.toPath())) {
            if (!trashInsteadOfDelete(task, old)) runCatching { AppFileSystem.delete(old.toPath()) }
        }
        val changed = com.michelle.raouf.fetch_jet.feature.derived.DerivedItems.compressedSource(task, copy.name, bytes)
        putOnEngine(changed)
        com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCopiesStore.remove(taskId, copyPath)
        return changed
    }

    internal fun freeNameAmongTasks(destDir: String, fileName: String, exceptId: String = ""): String {
        val dir = destDir.trimEnd('/', '\\')
        val others = (getAllCurrentTasks() + pendingTasks.value.values + allTasksFlow.value)
            .filter { it.id != exceptId && it.engineType != EngineType.BITTORRENT && it.destinationPath.trimEnd('/', '\\') == dir }
        return firstFreeFileName(fileName) { name -> others.any { it.fileName.equals(name, ignoreCase = true) } }
    }

    internal fun freeNameIn(destDir: String, fileName: String): String {
        val dir = destDir.trimEnd('/', '\\')
        val others = (getAllCurrentTasks() + pendingTasks.value.values + allTasksFlow.value)
            .filter { it.destinationPath.trimEnd('/', '\\') == dir }
        return firstFreeFileName(fileName) { name ->
            AppFileSystem.exists("$dir/$name".toPath()) || others.any { it.fileName.equals(name, ignoreCase = true) }
        }
    }

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
        /** Clip: whole video at full speed, then a local cut (see MediaClipCut.kt). Off = only the
         *  clip is fetched (--download-sections), which YouTube throttles to about playback rate. */
        clipByCutting: Boolean = false,
        requestHeaders: Map<String, String> = emptyMap(),
        sortByType: Boolean = true,
        /** Start at this time (epoch ms); 0 = now (or as soon as a slot is free). */
        scheduledAtMs: Long = 0L,
        /** Connections for a direct download; 0 = app setting / automatic. */
        maxConnections: Int = 0,
        pairedAudioUrl: String? = null,
        expectedChecksum: String? = null,
        subtitleTracks: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
        embedSubtitles: Boolean = false,
        subtitleFormat: String = "srt",
        playlistId: String = "",
        playlistTitle: String = "",
        subtitleLanguages: String = "",
        initialStepText: String? = null,
        displayTotalBytes: Long = 0L,
        displayDownloadedBytes: Long = 0L,
        /** Never reuse the name of a file already in the folder or of another task there. */
        keepExistingFiles: Boolean = false,
        /** A finished video taken from another device as a plain file: [pageUrl] still offers its subtitles. */
        subtitlesFromPage: Boolean = false,
        pageUrl: String = "",
        hidden: Boolean = false
    ): Result<DownloadTask> {
        // A quoted / file:// .torrent path is stored as a plain path.
        val trimmedUrl = url.trim().let {
            if (com.michelle.raouf.fetch_jet.feature.torrent_engine.isLocalTorrentPath(it))
                com.michelle.raouf.fetch_jet.feature.torrent_engine.normalizeLocalTorrentPath(it)
            else if (forcedEngineType == EngineType.MEDIA_STREAM) it
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
            ?.takeUnless { com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(targetUrl) }
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
        val safeFileName = customFileName?.let {
            if (engineType == EngineType.BITTORRENT) it else pathResolver.sanitizeFileName(it).ifBlank { null }
        }
        val siteRule = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.siteFolderFor(
            targetUrl, originalMediaUrl, requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
        )
        val fromSiteRule = siteRule != null && (customDestinationPath.isNullOrBlank() ||
                com.michelle.raouf.fetch_jet.core.settings.SiteFolderRules.samePath(customDestinationPath, siteRule.folder))
        val baseDir = when {
            !customDestinationPath.isNullOrBlank() -> customDestinationPath
            siteRule != null -> siteRule.folder
            else -> pathResolver.getDownloadsDirectory()
        }
        // "Sort into folders": Video / Music / Archives / ... under the chosen folder, by file type.
        val destDir =
            if (!fromSiteRule && sortByType && com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.sortIntoFolders.value) {
                val nameForType = (safeFileName ?: torrentName ?: targetUrl.substringBefore('?')
                    .substringAfterLast('/'))
                com.michelle.raouf.fetch_jet.core.settings.FileCategory.folderFor(
                    nameForType,
                    isMedia = engineType == EngineType.MEDIA_STREAM,
                    isTorrent = engineType == EngineType.BITTORRENT
                )?.let { sub ->
                    val trimmed = baseDir.trimEnd('/', '\\')
                    val remembered = com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.categoryFolder(sub)
                    if (remembered != null && remembered.trimEnd('/', '\\') == trimmed) return@let baseDir
                    // Already points at a type folder (the Add dialog shows the real target) → use as is.
                    val known = com.michelle.raouf.fetch_jet.core.settings.FileCategory.allFolders
                    if (known.any { trimmed.endsWith("/$it") || trimmed.endsWith("\\$it") }) baseDir
                    else trimmed + "/" + sub
                } ?: baseDir
            } else baseDir

        val shownTotal = if (initialTotalBytes > 0L) initialTotalBytes else displayTotalBytes.coerceAtLeast(0L)
        val shownDownloaded = if (shownTotal > 0L) displayDownloadedBytes.coerceIn(0L, shownTotal) else 0L
        val sameFolderList = if (playlistId.isBlank() && engineType != EngineType.BITTORRENT)
            com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistStore.inFolder(destDir) else null
        val plainTask = DownloadTask(
            id = taskId,
            url = targetUrl,
            fileName = safeFileName?.let {
                when {
                    engineType == EngineType.BITTORRENT -> it
                    keepExistingFiles -> freeNameIn(destDir, it)
                    else -> freeNameAmongTasks(destDir, it, taskId)
                }
            }
                ?: torrentName ?: "",
            destinationPath = destDir,
            engineType = engineType,
            disableUpload = disableUpload,
            totalBytes = shownTotal,
            downloadedBytes = shownDownloaded,
            progress = if (shownTotal > 0L) shownDownloaded.toFloat() / shownTotal.toFloat() else 0f,
            currentStepText = initialStepText,
            torrentFiles = torrentFiles,
            webSeedUrls = webSeedUrls,
            maxDownloadSpeedBytesPerSec = maxDownloadSpeedBytesPerSec,
            maxUploadSpeedBytesPerSec = maxUploadSpeedBytesPerSec,
            formatId = formatId,
            originalUrl = originalMediaUrl ?: pageUrl.takeIf { subtitlesFromPage && it.isNotBlank() },
            subtitlesFromPage = subtitlesFromPage && pageUrl.isNotBlank(),
            pairedAudioUrl = pairedAudioUrl?.takeIf { it.isNotBlank() },
            clipStart = clipStart,
            clipEnd = clipEnd,
            clipByCutting = cutAfterDownload,
            approximateTotalBytes = initialApproximateBytes,
            createdAtEpochMs = nowEpochMs(),
            requestHeaders = requestHeaders.filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() },
            scheduledAtMs = scheduledAtMs.takeIf { it > nowEpochMs() } ?: 0L,
            maxConnections = maxConnections.coerceIn(0, 32),
            expectedChecksum = expectedChecksum?.takeIf { it.isNotBlank() },
            subtitleTracks = if (engineType == EngineType.MEDIA_STREAM || (subtitlesFromPage && pageUrl.isNotBlank())) subtitleTracks.filter { it.isSelected } else emptyList(),
            embedSubtitles = embedSubtitles,
            subtitleFormat = subtitleFormat,
            playlistId = sameFolderList?.id ?: playlistId,
            playlistTitle = sameFolderList?.title ?: playlistTitle,
            subtitleLanguages = if (engineType == EngineType.MEDIA_STREAM) subtitleLanguages else ""
        )
        val wantsHidden = shouldStartHidden(plainTask, destDir, hidden)
        val task = if (wantsHidden) startHidden(plainTask, destDir) else plainTask
        val goHidden = task !== plainTask

        // Later (scheduled), others already waiting, or no free slot: join the queue. The check
        // and the start happen under the pump's lock, and a started task is in its engine (counted
        // as running) before the lock opens, so adds made in a tight loop never exceed the limit.
        val result = pumpLock.withLock {
            if (task.scheduledAtMs > 0L || pendingTasks.value.isNotEmpty() || !hasFreeSlot()) {
                pendingTasks.update { it + (task.id to task.copy(status = DownloadStatus.QUEUED)) }
                pumpQueue()
                return@withLock Result.success(task)
            }
            try {
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
        if (goHidden && result.isFailure) forgetHidden(taskId)
        return result
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
            forgetHidden(taskId)
            return Result.success(Unit)
        }
        val task = allTasksFlow.value.find { it.id == taskId }
            ?.let { if (!it.hidden && HiddenStore.state.value.hides(it)) it.copy(hidden = true) else it }
            ?: return Result.failure(Exception("Task not found"))
        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.cancel(taskId)
            EngineType.BITTORRENT -> torrentEngine.cancel(taskId, deleteFiles = deleteFile)
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
                        if (task.destinationPath.isNotBlank() && task.fileName.isNotBlank()) {
                            val destDir = task.destinationPath
                            val finalFile = "$destDir/${task.fileName}"
                            val filePathsToCheck = mutableListOf(
                                "$destDir/${task.fileName}.part",
                                "$destDir/${task.fileName}.ytdl",
                                "$destDir/${task.fileName}.temp"
                            )
                            for (i in 0..16) {
                                filePathsToCheck.add("$destDir/${task.fileName}.part$i")
                            }
                            try {
                                val okPath = finalFile.toPath()
                                if (ownsDestinationFile(task, AppFileSystem.metadataOrNull(okPath)?.size)) {
                                    if (!trashInsteadOfDelete(task, finalFile)) AppFileSystem.delete(okPath)
                                }
                            } catch (_: Exception) {
                            }
                            for (p in filePathsToCheck) {
                                try {
                                    val okPath = p.toPath()
                                    if (AppFileSystem.exists(okPath)) AppFileSystem.delete(okPath)
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
                                    if (AppFileSystem.exists(okPath)) {
                                        AppFileSystem.delete(okPath)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            // Also search tempDir for any leftover files starting with baseName
                            try {
                                val tempOkioPath = tempDir.toPath()
                                if (AppFileSystem.exists(tempOkioPath)) {
                                    val list = AppFileSystem.list(tempOkioPath)
                                    for (item in list) {
                                        if (ownsTempItem(item.name, effectiveFileName, baseName, task.engineType)) {
                                            try {
                                                AppFileSystem.delete(item)
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
                        val hash = magnetHexInfoHash(task.url)
                        val partFile = if (hash != null && task.destinationPath.isNotBlank()) "${task.destinationPath}/.$hash.parts".toPath() else null
                        val wroteFiles = torrentWroteFiles(task, partFile != null && AppFileSystem.exists(partFile))
                        if (wroteFiles && task.destinationPath.isNotBlank() && task.fileName.isNotBlank()) {
                            val dirPath = "${task.destinationPath}/${task.fileName}".toPath()
                            if (AppFileSystem.exists(dirPath)) {
                                try {
                                    if (!trashInsteadOfDelete(task, dirPath.toString())) AppFileSystem.deleteRecursively(dirPath)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        if (wroteFiles) for (fileEntry in task.torrentFiles) {
                            val filePathStr =
                                "${task.destinationPath}/${if (fileEntry.path.startsWith("${task.fileName}/")) fileEntry.path else "${task.fileName}/${fileEntry.path}"}"
                            val filePathObj = filePathStr.toPath()
                            if (AppFileSystem.exists(filePathObj)) {
                                try {
                                    AppFileSystem.delete(filePathObj)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        if (partFile != null) {
                            try {
                                if (AppFileSystem.exists(partFile)) AppFileSystem.delete(partFile)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
        }

        if (task.hidden) finishHiddenRemoval(task, deleteFile)
        else finishWatch.update { it - taskId }
        return Result.success(Unit)
    }

    internal fun magnetHexInfoHash(url: String): String? {
        if (!url.contains("xt=urn:btih:", ignoreCase = true)) return null
        val raw = url.substringAfter("xt=urn:btih:").substringBefore('&').trim()
        if (raw.length == 40 && raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return raw.lowercase()
        if (raw.length != 32) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0L
        var bits = 0
        for (c in raw.uppercase()) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v.toLong()
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.append(((buffer shr bits) and 0xFF).toInt().toString(16).padStart(2, '0'))
            }
        }
        return out.toString().takeIf { it.length == 40 }
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

    fun addFinishedFile(task: DownloadTask): DownloadTask {
        val done = task.copy(status = DownloadStatus.COMPLETED, engineType = EngineType.HTTP_DIRECT)
        return httpEngine.enqueueTask(done)
    }

    /** Makes a saved download one of a playlist's videos. */
    fun setPlaylist(taskId: String, playlistId: String, playlistTitle: String) {
        val pending = pendingTasks.value[taskId]
        if (pending != null) {
            pendingTasks.update { it + (taskId to pending.copy(playlistId = playlistId, playlistTitle = playlistTitle)) }
            return
        }
        val task = getAllCurrentTasks().firstOrNull { it.id == taskId } ?: return
        val changed = task.copy(playlistId = playlistId, playlistTitle = playlistTitle)
        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(changed)
            EngineType.BITTORRENT -> return
            EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(changed)
        }
    }

    /** Task-level queue priority (1 High / 0 Normal / -1 Low): decides which waiting task starts next. */
    fun setTaskPriority(taskId: String, priority: Int) {
        val pending = pendingTasks.value[taskId]
        if (pending != null) {
            pendingTasks.update { it + (taskId to pending.copy(priority = priority)) }
        } else {
            val task = getAllCurrentTasks().firstOrNull { it.id == taskId } ?: return
            when (task.engineType) {
                EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task.copy(priority = priority))
                EngineType.BITTORRENT -> torrentEngine.enqueueTask(task.copy(priority = priority))
                EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task.copy(priority = priority))
            }
        }
        pumpQueue()
    }

    private fun loadAndRestoreTasks() {
        try {
            if (!AppFileSystem.exists(persistencePath)) return
            val text = AppFileSystem.read(persistencePath) { readUtf8() }
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
                AppFileSystem.atomicMove(persistencePath, "$persistencePath.corrupt".toPath())
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
            if (!AppFileSystem.exists(backupPath)) return
            println("QueueManager: restoring tasks from backup")
            AppFileSystem.copy(backupPath, persistencePath)
            val text = AppFileSystem.read(persistencePath) { readUtf8() }
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
                        if (hasSeenTasks && AppFileSystem.exists(persistencePath)) {
                            AppFileSystem.delete(persistencePath)
                            AppFileSystem.delete(backupPath, mustExist = false)
                        }
                    } else {
                        hasSeenTasks = true
                        val jsonArray = TaskCache.encode(tasks)
                        // Atomic: a crash mid-write used to leave a half file and lose every task.
                        val tmp = "$persistencePath.tmp".toPath()
                        AppFileSystem.write(tmp) {
                            writeUtf8(jsonArray)
                        }
                        // The file being replaced was readable (we loaded it): keep it as the backup.
                        if (AppFileSystem.exists(persistencePath)) {
                            try {
                                // A file from before encryption holds plain links and cookies:
                                // the backup gets the new (encrypted) list instead.
                                val old = AppFileSystem.read(persistencePath) { readUtf8() }
                                if (old.contains("\"url\":\"http")) {
                                    AppFileSystem.write(backupPath) { writeUtf8(jsonArray) }
                                } else {
                                    AppFileSystem.copy(persistencePath, backupPath)
                                }
                            } catch (_: Exception) {
                            }
                        }
                        AppFileSystem.atomicMove(tmp, persistencePath)
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
    com.michelle.raouf.fetch_jet.feature.http_downloader.googleDriveDownloadUrl(url)?.let { return it }
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

internal fun ownsTempItem(itemName: String, fileName: String, baseName: String, engine: EngineType): Boolean {
    if (fileName.isBlank()) return false
    if (itemName == fileName || itemName.startsWith("$fileName.")) return true
    return engine == EngineType.MEDIA_STREAM && baseName.isNotBlank() && itemName.startsWith("$baseName.")
}
