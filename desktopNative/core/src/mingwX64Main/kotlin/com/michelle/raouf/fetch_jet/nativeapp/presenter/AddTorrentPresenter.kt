package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddTorrentDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentFileRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentStateCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class TorrentSlice(
    val shown: Boolean,
    val hasUrl: Boolean,
    val inspecting: Boolean,
    val error: String?,
    val name: String?,
    val files: List<TorrentFileEntry>,
)

private fun DashboardUiState.torrentSlice() = TorrentSlice(
    shown = isAddDialogVisible && linkList.isEmpty() && detectedEngine == EngineType.BITTORRENT,
    hasUrl = inputUrl.isNotBlank(),
    inspecting = isInspectingTorrent,
    error = inspectError,
    name = inspectedTorrentName,
    files = inspectedTorrentFiles,
)

internal fun torrentFileRow(file: TorrentFileEntry): TorrentFileRowDto {
    val strings = LocalizationManager.Strings
    return TorrentFileRowDto(
        index = file.index,
        path = file.path,
        size = formatBytes(file.size),
        selected = file.isSelected,
        priority = file.priority,
        priorityLabel = when (file.priority) {
            1 -> strings.torrentPriorityHigh
            -1 -> strings.torrentPriorityLow
            else -> strings.torrentPriorityNormal
        },
    )
}

internal fun nextTorrentPriority(priority: Int): Int = when (priority) {
    0 -> 1
    1 -> -1
    else -> 0
}

internal fun Presenter.startAddTorrent() {
    scope.launch {
        combine(vm.uiState.map { it.torrentSlice() }.distinctUntilChanged(), AppLanguage.current) { slice, _ -> slice }
            .collect { s ->
                if (!s.shown) {
                    addTorrent.publish(AddTorrentDto())
                    addTorrentFiles.publish(emptyList())
                    return@collect
                }
                val state = when {
                    s.inspecting -> TorrentStateCode.INSPECTING
                    s.error != null -> TorrentStateCode.ERROR
                    s.files.isEmpty() -> TorrentStateCode.NOT_INSPECTED
                    else -> TorrentStateCode.FILES
                }
                addTorrentFiles.publish(if (state == TorrentStateCode.FILES) s.files.map { torrentFileRow(it) } else emptyList())
                addTorrent.publish(
                    AddTorrentDto(
                        state = state.ordinal,
                        error = if (state == TorrentStateCode.ERROR) s.error.orEmpty() else "",
                        inspectEnabled = s.hasUrl,
                        name = s.name ?: LocalizationManager.Strings.torrentFiles,
                        allSelected = s.files.isNotEmpty() && s.files.all { it.isSelected },
                    )
                )
            }
    }
}

internal fun Presenter.cycleTorrentFilePriority(index: Int) {
    val file = vm.uiState.value.inspectedTorrentFiles.firstOrNull { it.index == index } ?: return
    vm.onEvent(DashboardUiEvent.OnTorrentFilePriorityChanged(index, nextTorrentPriority(file.priority)))
}

internal fun Presenter.cycleLinkTorrentFilePriority(link: Int, index: Int) {
    val file = vm.uiState.value.linkList.getOrNull(link)?.torrentFiles?.firstOrNull { it.index == index } ?: return
    vm.onEvent(DashboardUiEvent.OnLinkListTorrentFilePriority(link, index, nextTorrentPriority(file.priority)))
}
