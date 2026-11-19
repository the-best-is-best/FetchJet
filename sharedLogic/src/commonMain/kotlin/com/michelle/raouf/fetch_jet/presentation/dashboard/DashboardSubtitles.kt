package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import com.michelle.raouf.fetch_jet.feature.media_extractor.canAddSubtitles
import com.michelle.raouf.fetch_jet.feature.media_extractor.subtitleKeysPresent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun DashboardViewModel.handleSubtitlesEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnSubtitlesClicked -> openSubtitles(event.taskId)

        is DashboardUiEvent.OnSubtitleTrackToggled -> _uiState.update { state ->
            val page = state.subtitlesPage ?: return@update state
            if (page.running || event.key in page.present) return@update state
            state.copy(subtitlesPage = page.copy(tracks = page.tracks.map {
                if (subtitleKey(it) == event.key) it.copy(isSelected = event.selected) else it
            }))
        }

        DashboardUiEvent.OnSubtitlesAddClicked -> startAddingSubtitles()

        DashboardUiEvent.OnDismissSubtitles -> _uiState.update { state ->
            if (state.subtitlesPage?.running == true) state else state.copy(subtitlesPage = null)
        }

        else -> Unit
    }
}

private fun DashboardViewModel.subtitleTask(taskId: String): DownloadTask? =
    queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId }?.takeIf { it.canAddSubtitles() }

private fun DashboardViewModel.openSubtitles(taskId: String) {
    val task = subtitleTask(taskId) ?: return
    val present = task.subtitleKeysPresent()
    _uiState.update {
        it.copy(subtitlesPage = SubtitlesUi(taskId = taskId, fileName = task.fileName, embed = task.embedSubtitles, format = task.subtitleFormat, present = present))
    }
    scope.launch {
        val tracks = try {
            queueRepository.inspectSubtitlesOf(task)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        _uiState.update { state ->
            val page = state.subtitlesPage?.takeIf { it.taskId == taskId && it.reading } ?: return@update state
            state.copy(
                subtitlesPage = page.copy(
                    reading = false,
                    readFailed = tracks == null,
                    tracks = tracks.orEmpty().map { it.copy(isSelected = subtitleKey(it) in present) }
                )
            )
        }
    }
}

private fun DashboardViewModel.startAddingSubtitles() {
    val page = _uiState.value.subtitlesPage ?: return
    if (!page.canAdd) return
    val wanted = page.tracks.filter { it.isSelected && subtitleKey(it) !in page.present }
    _uiState.update {
        it.copy(subtitlesPage = page.copy(running = true, done = 0, total = wanted.size, added = emptyList(), refused = emptyList(), failed = emptyList()))
    }
    scope.launch {
        val result = try {
            queueRepository.addSubtitles(page.taskId, wanted) { done ->
                _uiState.update { state ->
                    val current = state.subtitlesPage?.takeIf { it.taskId == page.taskId && it.running } ?: return@update state
                    state.copy(subtitlesPage = current.copy(done = done))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val names = { list: List<SubtitleTrack> -> list.map { trackLabel(it) } }
        _uiState.update { state ->
            val current = state.subtitlesPage?.takeIf { it.taskId == page.taskId } ?: return@update state
            val added = result?.added.orEmpty()
            state.copy(
                subtitlesPage = current.copy(
                    running = false,
                    present = current.present + added.map { subtitleKey(it) },
                    tracks = current.tracks.map { t ->
                        if (added.any { subtitleKey(it) == subtitleKey(t) }) t.copy(isSelected = true)
                        else if (subtitleKey(t) in current.present) t else t.copy(isSelected = false)
                    },
                    added = names(added),
                    refused = names(result?.refused.orEmpty()),
                    failed = names(result?.failed ?: wanted)
                )
            )
        }
    }
}

fun trackLabel(track: SubtitleTrack): String =
    track.langName.ifBlank { track.langCode } + if (track.isAuto) " (${LocalizationManager.Strings.subtitlesAuto})" else ""

fun subtitlesNoteText(page: SubtitlesUi): String =
    if (page.embed) LocalizationManager.Strings.subtitlesGoInside
    else LocalizationManager.Strings.subtitlesGoBeside.replace(".srt", "." + page.format)

fun subtitlesProgressText(done: Int, total: Int): String =
    LocalizationManager.Strings.subtitlesAddingProgress.replace("{done}", done.toString()).replace("{total}", total.toString())

fun subtitlesListText(template: String, names: List<String>): String =
    template.replace("{langs}", names.joinToString(", "))
