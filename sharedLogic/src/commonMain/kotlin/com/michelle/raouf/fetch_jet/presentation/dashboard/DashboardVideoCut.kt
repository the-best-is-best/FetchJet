package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCopiesStore
import com.michelle.raouf.fetch_jet.feature.derived.DerivedItems
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

private var videoCutJob: Job? = null

internal fun DashboardViewModel.observeVideoClips() {
    scope.launch {
        queueRepository.allTasksFlow
            .map { tasks -> tasks.filter { it.cutFromTaskId.isNotBlank() } }
            .distinctUntilChanged()
            .collect { clipTasks ->
                val clips = clipTasks.map { it.cutFromTaskId }.distinct().associateWith { VideoCut.clipsOf(it, clipTasks) }
                _uiState.update { state ->
                    val page = state.videoCut
                    state.copy(
                        videoClips = clips,
                        videoCut = page?.copy(clips = clips[page.taskId].orEmpty())
                    )
                }
                refreshDeleteOriginal()
            }
    }
}

internal fun DashboardViewModel.derivedCount(taskId: String): Int {
    AudioCopiesStore.load()
    return DerivedItems.count(taskId, queueRepository.allTasksFlow.value, AudioCopiesStore.counts.value)
}

internal fun DashboardViewModel.refreshDeleteOriginal() {
    _uiState.update { state ->
        val cut = state.videoCut
        val compress = state.audioCompress
        state.copy(
            videoCut = cut?.let { page ->
                val can = derivedCount(page.taskId) == 0
                if (page.running || page.canDeleteOriginal == can) page else page.copy(canDeleteOriginal = can, deleteOriginal = page.deleteOriginal && can)
            },
            audioCompress = compress?.let { page ->
                val can = derivedCount(page.taskId) == 0
                if (page.running || page.canDeleteOriginal == can) page else page.copy(canDeleteOriginal = can, deleteOriginal = page.deleteOriginal && can)
            }
        )
    }
}

internal fun DashboardViewModel.handleVideoCutEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnVideoCutClicked -> openVideoCut(event.taskId)

        is DashboardUiEvent.OnVideoCutShowClips -> showVideoClips(event.taskId)

        is DashboardUiEvent.OnVideoCutModeChosen -> updateVideoCut { page ->
            if (page.running || page.clipsOnly) page
            else page.copy(mode = event.mode, failed = false, message = "").withFullRangeIfUnset()
        }

        is DashboardUiEvent.OnVideoCutRangeChanged -> updateVideoCut { page ->
            if (page.running) page
            else {
                val (start, end) = VideoCut.clampRange(event.startMs, event.endMs, page.durationMs)
                page.copy(startMs = start, endMs = end, failed = false, message = "")
            }
        }

        DashboardUiEvent.OnVideoCutStartClicked -> startVideoCut()

        DashboardUiEvent.OnVideoCutCancelClicked -> {
            val page = _uiState.value.videoCut ?: return
            if (!page.running || page.stopping) return
            updateVideoCut { it.copy(stopping = true) }
            videoCutJob?.cancel()
        }

        DashboardUiEvent.OnDismissVideoCut -> _uiState.update { state ->
            if (state.videoCut?.running == true) state else state.copy(videoCut = null)
        }

        is DashboardUiEvent.OnVideoCutDeleteClip -> deleteVideoClip(event.path)

        is DashboardUiEvent.OnVideoCutDeleteOriginalChanged -> updateVideoCut { page ->
            if (page.running || page.clipsOnly || !page.canDeleteOriginal) page else page.copy(deleteOriginal = event.on)
        }

        else -> Unit
    }
}

private fun DashboardViewModel.updateVideoCut(change: (VideoCutUi) -> VideoCutUi) {
    _uiState.update { state -> state.videoCut?.let { state.copy(videoCut = change(it)) } ?: state }
}

private fun VideoCutUi.withFullRangeIfUnset(): VideoCutUi =
    if (durationMs > 0L && endMs <= startMs) copy(startMs = 0L, endMs = durationMs) else this

private fun DashboardViewModel.task(taskId: String): DownloadTask? =
    queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId }

private fun DashboardViewModel.clipsFor(taskId: String) =
    VideoCut.clipsOf(taskId, queueRepository.allTasksFlow.value)

private fun DashboardViewModel.openVideoCut(taskId: String) {
    if (_uiState.value.videoCut?.running == true) return
    val task = task(taskId)?.takeIf { VideoCut.canCut(it) } ?: return
    val source = VideoCut.sourcePath(task)
    if (AudioCompress.copyOf(source) == null) return
    _uiState.update {
        it.copy(
            videoCut = VideoCutUi(
                taskId = taskId,
                fileName = task.fileName,
                sourcePath = source,
                reading = true,
                isAudio = VideoCut.isAudio(task),
                clips = clipsFor(taskId),
                canDeleteOriginal = derivedCount(taskId) == 0
            )
        )
    }
    scope.launch {
        val duration = VideoCut.durationMs(source)
        val extension = VideoCut.outputExtension(source, task.fileName)
        _uiState.update { state ->
            val page = state.videoCut?.takeIf { it.taskId == taskId } ?: return@update state
            val read = page.copy(
                durationMs = duration,
                reading = false,
                outputExtension = extension,
                failed = duration <= 0L,
                message = if (duration <= 0L) LocalizationManager.Strings.videoCutFailed else ""
            )
            state.copy(videoCut = if (read.mode != null) read.withFullRangeIfUnset() else read)
        }
    }
}

private fun DashboardViewModel.showVideoClips(taskId: String) {
    if (_uiState.value.videoCut?.running == true) return
    val task = task(taskId) ?: return
    _uiState.update {
        it.copy(
            videoCut = VideoCutUi(
                taskId = taskId,
                fileName = task.fileName,
                sourcePath = VideoCut.sourcePath(task),
                clipsOnly = true,
                isAudio = VideoCut.isAudio(task),
                clips = clipsFor(taskId)
            )
        )
    }
}

private fun DashboardViewModel.startVideoCut() {
    val page = _uiState.value.videoCut ?: return
    if (!page.canStart || page.clipsOnly) return
    val task = task(page.taskId)?.takeIf { VideoCut.canCut(it) } ?: return
    val source = VideoCut.sourcePath(task)
    val startMs = page.startMs
    val endMs = page.endMs
    val extension = page.outputExtension.ifBlank { task.fileName.substringAfterLast('.', "mp4") }
    val output = AudioCompress.freePath(
        task.destinationPath.trimEnd('/', '\\'), VideoCut.clipName(task.fileName, startMs, endMs, extension)
    )
    val replaceSource = page.deleteOriginal && page.canDeleteOriginal && derivedCount(task.id) == 0
    updateVideoCut {
        it.copy(running = true, stopping = false, progress = 0f, elapsedMs = 0L, etaMs = -1L, failed = false, message = "", lastClipPath = "")
    }
    videoCutJob = scope.launch {
        var ok = false
        var replaced = false
        try {
            ok = VideoCut.cut(source, output, startMs, endMs, { step ->
                _uiState.update { state ->
                    val current = state.videoCut?.takeIf { it.taskId == page.taskId && it.running } ?: return@update state
                    state.copy(videoCut = current.copy(progress = step.fraction, elapsedMs = step.elapsedMs, etaMs = step.etaMs))
                }
            }, sourceDurationMs = page.durationMs)
            if (ok) {
                val bytes = AppFileSystem.metadataOrNull(output.toPath())?.size ?: 0L
                val clip = queueRepository.addFinishedFile(
                    VideoCut.clipTask(queueRepository.generateUniqueTaskId(), task, output, startMs, endMs, bytes)
                )
                if (replaceSource) withContext(NonCancellable) {
                    replaced = queueRepository.promoteClip(clip, task.id) != null
                }
            }
        } catch (e: CancellationException) {
            ok = false
            throw e
        } catch (_: Throwable) {
            ok = false
        } finally {
            withContext(NonCancellable) {
                val cancelled = _uiState.value.videoCut?.stopping == true
                if (!ok) VideoCut.deleteWhenFree(output)
                if (replaced) {
                    _uiState.update { state ->
                        if (state.videoCut?.taskId == page.taskId) state.copy(videoCut = null) else state
                    }
                    _uiEffect.emit(DashboardUiEffect.ShowToast(LocalizationManager.Strings.videoCutSaved))
                    return@withContext
                }
                _uiState.update { state ->
                    val current = state.videoCut?.takeIf { it.taskId == page.taskId } ?: return@update state
                    state.copy(
                        videoCut = current.copy(
                            running = false,
                            stopping = false,
                            progress = if (ok) 1f else 0f,
                            etaMs = -1L,
                            failed = !ok && !cancelled,
                            message = when {
                                ok -> LocalizationManager.Strings.videoCutSaved
                                !cancelled && !state.isFfmpegInstalled -> LocalizationManager.Strings.videoCutNeedsTools
                                !cancelled -> LocalizationManager.Strings.videoCutFailed
                                else -> ""
                            },
                            lastClipPath = if (ok) output else "",
                            clips = clipsFor(page.taskId)
                        )
                    )
                }
            }
        }
    }
}

private fun DashboardViewModel.deleteVideoClip(path: String) {
    val state = _uiState.value
    val clip = state.videoCut?.clips?.firstOrNull { it.path == path }
        ?: state.videoClips.values.flatten().firstOrNull { it.path == path }
        ?: return
    val owned = task(clip.taskId)?.takeIf { it.cutFromTaskId.isNotEmpty() } ?: return
    scope.launch {
        queueRepository.removeTask(owned.id, deleteFile = true)
        if (AudioCompress.copyOf(path) != null) AudioCompress.delete(path)
        _uiState.update { current ->
            val page = current.videoCut ?: return@update current
            current.copy(videoCut = page.copy(clips = page.clips.filter { it.path != path }))
        }
    }
}
