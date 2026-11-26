package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.feature.video_merge.VideoMerge
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

private var videoMergeJob: Job? = null
private var videoMergeCheck: Job? = null

internal fun DashboardViewModel.canMergeVideos(taskIds: List<String>): Boolean {
    val tasks = queueRepository.allTasksFlow.value
    return VideoMerge.canMergeAll(taskIds.distinct().mapNotNull { id -> tasks.firstOrNull { it.id == id } })
}

internal fun DashboardViewModel.handleVideoMergeEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnMergeVideosClicked -> openVideoMerge(event.taskIds)

        is DashboardUiEvent.OnMergeMoveClicked -> {
            val items = _uiState.value.videoMerge?.items ?: return
            val from = items.indexOfFirst { it.taskId == event.taskId }
            if (from >= 0) moveMergeItem(from, if (event.up) from - 1 else from + 1)
        }

        is DashboardUiEvent.OnMergeMoveTo -> moveMergeItem(event.from, event.to)

        is DashboardUiEvent.OnMergeNameChanged -> updateVideoMerge { page ->
            if (page.running) page else page.copy(fileName = event.name, failed = false, message = "", conflictName = "", copyName = "")
        }

        DashboardUiEvent.OnMergeStartClicked -> startVideoMerge(MergeTarget.ASK)

        DashboardUiEvent.OnMergeSaveAsCopyClicked -> startVideoMerge(MergeTarget.COPY)

        DashboardUiEvent.OnMergeOverwriteClicked -> startVideoMerge(MergeTarget.OVERWRITE)

        DashboardUiEvent.OnMergeConflictDismissed -> updateVideoMerge { it.copy(conflictName = "", copyName = "") }

        DashboardUiEvent.OnMergeCancelClicked -> {
            val page = _uiState.value.videoMerge ?: return
            if (!page.running || page.stopping) return
            updateVideoMerge { it.copy(stopping = true) }
            videoMergeJob?.cancel()
        }

        DashboardUiEvent.OnDismissMerge -> {
            if (_uiState.value.videoMerge?.running == true) return
            videoMergeCheck?.cancel()
            _uiState.update { it.copy(videoMerge = null) }
        }

        else -> Unit
    }
}

private fun DashboardViewModel.updateVideoMerge(change: (VideoMergeUi) -> VideoMergeUi) {
    _uiState.update { state -> state.videoMerge?.let { state.copy(videoMerge = change(it)) } ?: state }
}

private fun DashboardViewModel.mergeTasks(ids: List<String>): List<DownloadTask> {
    val tasks = queueRepository.allTasksFlow.value
    return ids.mapNotNull { id -> tasks.firstOrNull { it.id == id } }
}

private fun DashboardViewModel.openVideoMerge(taskIds: List<String>) {
    if (_uiState.value.videoMerge?.running == true) return
    val ordered = queueRepository.allTasksFlow.value.filter { it.id in taskIds.toSet() }
        .sortedWith(compareBy<DownloadTask>({ it.createdAtEpochMs }, { it.fileName.lowercase() }))
    if (!VideoMerge.canMergeAll(ordered)) return
    val first = ordered.first()
    val extension = first.fileName.substringAfterLast('.', "mp4")
    _uiState.update {
        it.copy(
            videoMerge = VideoMergeUi(
                items = ordered.map { task ->
                    val bytes = runCatching { AppFileSystem.metadataOrNull(VideoMerge.sourcePath(task).toPath())?.size }.getOrNull()
                    VideoMergeItem(task.id, task.fileName, bytes ?: task.totalBytes)
                },
                fileName = VideoMerge.mergedName(first.fileName).substringBeforeLast('.'),
                extension = extension,
                checking = true
            )
        )
    }
    checkMergeItems()
}

private fun DashboardViewModel.moveMergeItem(from: Int, to: Int) {
    val page = _uiState.value.videoMerge ?: return
    if (page.running || from == to || from !in page.items.indices || to !in page.items.indices) return
    val items = page.items.toMutableList()
    items.add(to, items.removeAt(from))
    val firstChanged = page.items.first().taskId != items.first().taskId
    _uiState.update { state ->
        state.copy(
            videoMerge = page.copy(
                items = items,
                extension = if (firstChanged) items.first().fileName.substringAfterLast('.', page.extension) else page.extension,
                failed = false,
                message = ""
            )
        )
    }
    if (firstChanged) checkMergeItems()
}

private fun DashboardViewModel.checkMergeItems() {
    val page = _uiState.value.videoMerge ?: return
    val ids = page.items.map { it.taskId }
    updateVideoMerge { it.copy(checking = true, items = it.items.map { item -> item.copy(notSame = false) }, failed = false, message = "") }
    videoMergeCheck?.cancel()
    videoMergeCheck = scope.launch {
        val tasks = mergeTasks(ids)
        val infos = tasks.map { VideoMerge.info(VideoMerge.sourcePath(it)) }
        val durations = tasks.map { VideoCut.durationMs(VideoMerge.sourcePath(it)) }
        val bad = VideoMerge.firstMismatch(infos)
        _uiState.update { state ->
            val current = state.videoMerge?.takeIf { now -> now.items.map { it.taskId } == ids } ?: return@update state
            val needsTools = infos.all { it == null } && !state.isFfmpegInstalled
            state.copy(
                videoMerge = current.copy(
                    checking = false,
                    items = current.items.mapIndexed { index, item ->
                        item.copy(
                            durationMs = durations.getOrElse(index) { 0L },
                            notSame = bad != null && (index == bad || (bad == 0 && index == 0))
                        )
                    },
                    failed = bad != null,
                    message = when {
                        needsTools -> LocalizationManager.Strings.mergeNeedsTools
                        bad != null -> LocalizationManager.Strings.mergeNotSame
                        else -> ""
                    }
                )
            )
        }
    }
}

private enum class MergeTarget { ASK, COPY, OVERWRITE }

private fun DashboardViewModel.startVideoMerge(target: MergeTarget) {
    val page = _uiState.value.videoMerge ?: return
    if (target == MergeTarget.ASK && !page.canStart) return
    if (target != MergeTarget.ASK && (!page.hasConflict || page.running)) return
    val tasks = mergeTasks(page.items.map { it.taskId })
    if (tasks.size != page.items.size || !VideoMerge.canMergeAll(tasks)) return
    val first = tasks.first()
    val folder = first.destinationPath.trimEnd('/', '\\')
    val name = VideoMerge.withExtension(page.fileName, page.extension).ifBlank { VideoMerge.mergedName(first.fileName) }
    val taken = AppFileSystem.exists("$folder/$name".toPath()) ||
            queueRepository.allTasksFlow.value.any { it.destinationPath.trimEnd('/', '\\') == folder && it.fileName.equals(name, ignoreCase = true) }
    if (target == MergeTarget.ASK && taken) {
        val copy = queueRepository.freeNameIn(folder, name)
        updateVideoMerge { it.copy(conflictName = name, copyName = copy) }
        return
    }
    val replace = target == MergeTarget.OVERWRITE
    val output = if (replace) "$folder/$name" else "$folder/${queueRepository.freeNameIn(folder, name)}"
    val total = page.items.sumOf { it.durationMs }
    updateVideoMerge { it.copy(running = true, stopping = false, progress = 0f, elapsedMs = 0L, etaMs = -1L, failed = false, message = "", lastPath = "", conflictName = "", copyName = "") }
    videoMergeJob = scope.launch {
        var ok = false
        try {
            ok = VideoMerge.merge(tasks.map { VideoMerge.sourcePath(it) }, output, total, replace) { step ->
                _uiState.update { state ->
                    val current = state.videoMerge?.takeIf { it.running } ?: return@update state
                    state.copy(videoMerge = current.copy(progress = step.fraction, elapsedMs = step.elapsedMs, etaMs = step.etaMs))
                }
            }
            if (ok) {
                val bytes = AppFileSystem.metadataOrNull(output.toPath())?.size ?: 0L
                if (replace) queueRepository.allTasksFlow.value
                    .filter { it.destinationPath.trimEnd('/', '\\') == folder && it.fileName.equals(name, ignoreCase = true) }
                    .forEach { queueRepository.removeTask(it.id, deleteFile = false) }
                queueRepository.addFinishedFile(VideoMerge.mergedTask(queueRepository.generateUniqueTaskId(), first, output, bytes))
            }
        } catch (e: CancellationException) {
            ok = false
            throw e
        } catch (_: Throwable) {
            ok = false
        } finally {
            withContext(NonCancellable) {
                val cancelled = _uiState.value.videoMerge?.stopping == true
                if (!ok) VideoCut.deleteWhenFree(output)
                _uiState.update { state ->
                    val current = state.videoMerge ?: return@update state
                    state.copy(
                        videoMerge = current.copy(
                            running = false,
                            stopping = false,
                            progress = if (ok) 1f else 0f,
                            etaMs = -1L,
                            failed = !ok && !cancelled,
                            message = when {
                                ok -> LocalizationManager.Strings.mergeSaved
                                !cancelled && !state.isFfmpegInstalled -> LocalizationManager.Strings.mergeNeedsTools
                                !cancelled -> LocalizationManager.Strings.mergeFailed
                                else -> ""
                            },
                            lastPath = if (ok) output else ""
                        )
                    )
                }
            }
        }
    }
}
