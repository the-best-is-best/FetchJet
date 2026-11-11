package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCopiesStore
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private var lastAudioSettings = AudioCompressSettings()

internal fun DashboardViewModel.observeAudioCopies() {
    scope.launch {
        AudioCopiesStore.load()
        AudioCopiesStore.counts.collect { all ->
            _uiState.update { it.copy(audioCopyCounts = all.mapValues { (_, paths) -> paths.size }) }
        }
    }
}

internal fun DashboardViewModel.handleAudioCompressEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnAudioCompressClicked -> openAudioCompress(event.taskId)

        is DashboardUiEvent.OnAudioCompressSettingsChanged -> {
            if (_uiState.value.audioCompress?.running == true) return
            lastAudioSettings = event.settings
            _uiState.update { state ->
                val page = state.audioCompress ?: return@update state
                if (page.running) return@update state
                state.copy(audioCompress = page.copy(settings = event.settings, failed = false).withEstimate())
            }
        }

        DashboardUiEvent.OnAudioCompressStartClicked -> startAudioCompress()

        is DashboardUiEvent.OnAudioCompressDeleteCopy -> {
            val page = _uiState.value.audioCompress ?: return
            if (page.running || !AudioCopiesStore.owns(page.taskId, event.path)) return
            AudioCompress.delete(event.path)
            if (AudioCompress.copyOf(event.path) == null) AudioCopiesStore.remove(page.taskId, event.path)
            _uiState.update { state ->
                state.copy(audioCompress = state.audioCompress?.copy(copies = AudioCopiesStore.copies(page.taskId)))
            }
        }

        DashboardUiEvent.OnDismissAudioCompress -> _uiState.update { state ->
            if (state.audioCompress?.running == true) state else state.copy(audioCompress = null)
        }

        else -> Unit
    }
}

private fun AudioCompressUi.withEstimate(): AudioCompressUi =
    copy(estimatedBytes = AudioCompress.estimateBytes(durationMs, AudioCompress.encodeFor(settings)))

private fun DashboardViewModel.audioTask(taskId: String): DownloadTask? =
    queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId }?.takeIf { AudioCompress.canCompress(it) }

private fun DashboardViewModel.openAudioCompress(taskId: String) {
    val task = audioTask(taskId) ?: return
    val source = AudioCompress.sourcePath(task)
    val bytes = AudioCompress.copyOf(source)?.bytes
    _uiState.update {
        it.copy(
            audioCompress = AudioCompressUi(
                taskId = taskId,
                fileName = task.fileName,
                originalBytes = bytes ?: 0L,
                reading = bytes != null,
                failed = bytes == null,
                settings = lastAudioSettings,
                copies = AudioCopiesStore.copies(taskId)
            )
        )
    }
    if (bytes == null) return
    scope.launch {
        val duration = AudioCompress.durationMs(source)
        _uiState.update { state ->
            val page = state.audioCompress?.takeIf { it.taskId == taskId } ?: return@update state
            state.copy(audioCompress = page.copy(durationMs = duration, reading = false).withEstimate())
        }
    }
}

private fun DashboardViewModel.startAudioCompress() {
    val page = _uiState.value.audioCompress ?: return
    if (page.running || page.reading || page.originalBytes <= 0L) return
    val task = audioTask(page.taskId) ?: return
    val source = AudioCompress.sourcePath(task)
    val encode = AudioCompress.encodeFor(page.settings)
    val output = AudioCompress.freePath(
        task.destinationPath.trimEnd('/', '\\'), AudioCompress.outputName(task.fileName, encode)
    )
    _uiState.update { it.copy(audioCompress = page.copy(running = true, progress = 0f, failed = false)) }
    scope.launch {
        val ok = runCatching {
            AudioCompress.compress(source, output, encode, page.durationMs) { done ->
                _uiState.update { state ->
                    val current = state.audioCompress?.takeIf { it.taskId == page.taskId && it.running }
                        ?: return@update state
                    state.copy(audioCompress = current.copy(progress = done))
                }
            }
        }.getOrDefault(false)
        if (ok) AudioCopiesStore.add(page.taskId, output)
        _uiState.update { state ->
            val current = state.audioCompress?.takeIf { it.taskId == page.taskId } ?: return@update state
            state.copy(
                audioCompress = current.copy(
                    running = false,
                    progress = 0f,
                    failed = !ok,
                    copies = AudioCopiesStore.copies(page.taskId)
                )
            )
        }
    }
}
