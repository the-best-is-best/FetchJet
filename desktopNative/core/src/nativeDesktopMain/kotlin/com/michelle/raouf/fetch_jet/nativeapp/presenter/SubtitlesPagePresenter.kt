package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.nativeapp.dto.SubtitlePageRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SubtitlesPageDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.SubtitlesUi
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitleKey
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesListText
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesNoteText
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesProgressText
import com.michelle.raouf.fetch_jet.presentation.dashboard.trackLabel
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun buildSubtitlesPage(page: SubtitlesUi?): SubtitlesPageDto {
    if (page == null) return SubtitlesPageDto()
    val s = LocalizationManager.Strings
    val message = when {
        page.reading -> s.subtitlesReading
        page.readFailed -> s.subtitlesReadFailed
        page.tracks.isEmpty() -> s.subtitlesNone
        else -> ""
    }
    return SubtitlesPageDto(
        open = true,
        fileName = page.fileName,
        reading = page.reading,
        message = message,
        messageError = !page.reading && page.readFailed,
        note = if (message.isEmpty()) subtitlesNoteText(page) else "",
        rows = page.tracks.map { track ->
            val key = subtitleKey(track)
            val there = key in page.present
            SubtitlePageRowDto(
                key = key,
                name = trackLabel(track),
                checked = track.isSelected || there,
                present = there,
                enabled = !there && !page.running,
            )
        },
        running = page.running,
        progress = if (page.total > 0) page.done.toFloat() / page.total else 0f,
        progressText = if (page.running) subtitlesProgressText(page.done, page.total) else "",
        added = if (page.added.isEmpty()) "" else subtitlesListText(s.subtitlesAddedList, page.added),
        refused = if (page.refused.isEmpty()) "" else subtitlesListText(s.subtitlesRefusedList, page.refused),
        failed = if (page.failed.isEmpty()) "" else subtitlesListText(s.subtitlesFailedList, page.failed),
        canAdd = page.canAdd,
    )
}

internal fun Presenter.startSubtitlesPage() {
    scope.launch {
        combine(vm.uiState.map { it.subtitlesPage }.distinctUntilChanged(), AppLanguage.current) { page, _ ->
            buildSubtitlesPage(page)
        }.collect { subtitlesPage.publish(it) }
    }
}
