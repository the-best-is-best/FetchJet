package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenu
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuEntry
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuOptions
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowMenuItemDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun Presenter.rowMenuEntries(id: String): List<RowMenuEntry> {
    val task = task(id) ?: return emptyList()
    return RowMenu.entries(task, RowMenuOptions(canSend = DEVICE_SYNC_READY))
}

internal fun Presenter.openRowMenu(id: String) {
    val s = LocalizationManager.Strings
    val inHidden = local.value.hiddenOpen
    if (rowIsGroup(id)) {
        rowMenuTask.value = id
        rowMenu.publish(listOf(if (inHidden) RowMenuItemDto(UNHIDE_KEY, s.hiddenUnhide) else RowMenuItemDto(HIDE_KEY, s.hiddenHide)))
        return
    }
    val entries = rowMenuEntries(id)
    if (entries.isEmpty()) return closeRowMenu()
    rowMenuTask.value = id
    val items = entries.map { RowMenuItemDto(it.ordinal, RowMenu.label(it, s), it.danger, it.dividerBefore) }.toMutableList()
    if (!inHidden) items.add((items.size - 1).coerceAtLeast(0), RowMenuItemDto(HIDE_KEY, s.hiddenHide))
    rowMenu.publish(items)
}

private fun Presenter.menuRows(id: String): Set<String> {
    val checked = local.value.checkedIds
    return if (id in checked && checked.size > 1) checked else setOf(id)
}

private fun Presenter.unhideMenuRows(id: String) {
    if (!local.value.hiddenOpen) return
    vm.onEvent(DashboardUiEvent.OnUnhideTasks(expandPlaylistRows(menuRows(id))))
    local.update { it.copy(checkedIds = emptySet(), selectedTaskId = null, focusId = null) }
}

internal fun Presenter.closeRowMenu() {
    rowMenuTask.value = null
    rowMenu.publish(emptyList())
}

internal fun Presenter.pickRowMenu(key: Int) {
    val id = rowMenuTask.value
    closeRowMenu()
    id ?: return
    if (key == HIDE_KEY) return hideTargets(hiddenTargetOf(menuRows(id)))
    if (key == UNHIDE_KEY) return unhideMenuRows(id)
    val entry = RowMenuEntry.entries.getOrNull(key) ?: return
    if (entry !in rowMenuEntries(id)) return
    val task = task(id) ?: return
    when (entry) {
        RowMenuEntry.OPEN -> openDownloadedFile(taskFilePath(task), id)
        RowMenuEntry.OPEN_FOLDER -> if (!headless) scope.launch(Dispatchers.IO) { pathResolver.openFileLocation(taskFullPath(task)) }
        RowMenuEntry.PAUSE ->
            if (task.canResume() == false) local.update { it.copy(restartPauseTaskId = id) }
            else vm.onEvent(DashboardUiEvent.OnPauseClicked(id))

        RowMenuEntry.RESUME -> vm.onEvent(DashboardUiEvent.OnResumeClicked(id))
        RowMenuEntry.EXTRACT -> vm.onEvent(DashboardUiEvent.OnExtractTaskClicked(id))
        RowMenuEntry.COMPRESS -> vm.onEvent(DashboardUiEvent.OnAudioCompressClicked(id))
        RowMenuEntry.SUBTITLES -> vm.onEvent(DashboardUiEvent.OnSubtitlesClicked(id))
        RowMenuEntry.SEND_TO_DEVICE -> vm.onEvent(DashboardUiEvent.OnShareTaskClicked(id))
        RowMenuEntry.COPY_LINK -> vm.onEvent(DashboardUiEvent.OnCopyTaskLink(id))
        RowMenuEntry.SELECT -> local.update { it.copy(checkedIds = it.checkedIds + id) }
        RowMenuEntry.DETAILS -> local.update { it.copy(selectedTaskId = id) }
        RowMenuEntry.UNHIDE -> unhideMenuRows(id)
        RowMenuEntry.DELETE -> vm.onEvent(DashboardUiEvent.OnRemoveClicked(id))
    }
}
