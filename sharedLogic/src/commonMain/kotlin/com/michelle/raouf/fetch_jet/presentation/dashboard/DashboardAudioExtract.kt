package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.feature.audio_extract.AudioExtract
import com.michelle.raouf.fetch_jet.feature.audio_extract.AudioExtractNoAudioException
import com.michelle.raouf.fetch_jet.feature.audio_extract.ExtractedAudio
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

internal fun DashboardViewModel.extractAudio(taskId: String) {
    val state = _uiState.value
    if (taskId in state.audioExtracting) return
    val task = queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId }
        ?.takeIf { AudioExtract.canExtract(it) } ?: return
    val strings = LocalizationManager.Strings
    if (!state.isFfmpegInstalled) {
        scope.launch { _uiEffect.emit(DashboardUiEffect.ShowToast(strings.videoCutNeedsTools)) }
        return
    }
    _uiState.update { it.copy(audioExtracting = it.audioExtracting + taskId) }
    scope.launch {
        var message = strings.audioExtractFailed
        try {
            val made: ExtractedAudio? = AudioExtract.extractDetailed(
                VideoCut.sourcePath(task), task.destinationPath, task.fileName
            )
            if (made != null) {
                val bytes = AppFileSystem.metadataOrNull(made.path.toPath())?.size ?: 0L
                queueRepository.addFinishedFile(
                    VideoCut.clipTask(queueRepository.generateUniqueTaskId(), task, made.path, 0L, made.durationMs, bytes)
                )
                message = strings.audioExtractDone
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: AudioExtractNoAudioException) {
            message = strings.audioExtractNoAudio
        } catch (_: Throwable) {
            message = strings.audioExtractFailed
        } finally {
            withContext(NonCancellable) {
                _uiState.update { it.copy(audioExtracting = it.audioExtracting - taskId) }
                _uiEffect.emit(DashboardUiEffect.ShowToast(message))
            }
        }
    }
}
