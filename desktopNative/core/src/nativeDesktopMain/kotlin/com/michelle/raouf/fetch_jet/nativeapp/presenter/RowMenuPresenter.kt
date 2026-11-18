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
    val entries = rowMenuEntries(id)
    if (entries.isEmpty()) return closeRowMenu()
    val s = LocalizationManager.Strings
    rowMenuTask.value = id
    rowMenu.publish(entries.map { RowMenuItemDto(it.ordinal, RowMenu.label(it, s), it.danger, it.dividerBefore) })
}

internal fun Presenter.closeRowMenu() {
    rowMenuTask.value = null
    rowMenu.publish(emptyList())
}

internal fun Presenter.pickRowMenu(key: Int) {
    val id = rowMenuTask.value
    closeRowMenu()
    id ?: return
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
        RowMenuEntry.SEND_TO_DEVICE -> vm.onEvent(DashboardUiEvent.OnShareTaskClicked(id))
        RowMenuEntry.COPY_LINK -> vm.onEvent(DashboardUiEvent.OnCopyTaskLink(id))
        RowMenuEntry.SELECT -> local.update { it.copy(checkedIds = it.checkedIds + id) }
        RowMenuEntry.DETAILS -> local.update { it.copy(selectedTaskId = id) }
        RowMenuEntry.DELETE -> vm.onEvent(DashboardUiEvent.OnRemoveClicked(id))
    }
}
