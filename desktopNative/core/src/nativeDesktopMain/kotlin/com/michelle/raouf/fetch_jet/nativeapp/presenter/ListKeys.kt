package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowActionCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Presenter.keyTarget(): DownloadTask? {
    val ui = local.value
    return (ui.focusId ?: ui.selectedTaskId)?.let { task(it) }
}

internal fun Presenter.keyTargets(): List<DownloadTask> {
    val checked = local.value.checkedIds
    return if (checked.isNotEmpty()) checked.mapNotNull { task(it) } else listOfNotNull(keyTarget())
}

internal fun Presenter.moveFocus(shift: Boolean, target: (current: Int, last: Int) -> Int) {
    val ids = shownOrder
    if (ids.isEmpty()) return
    val ui = local.value
    val current = ids.indexOf(ui.focusId ?: ui.selectedTaskId ?: ui.anchorId)
    val to = target(current, ids.lastIndex).coerceIn(0, ids.lastIndex)
    val id = ids[to]
    val follow = ui.selectedTaskId?.let { task(it) } != null
    if (shift) {
        val from = ids.indexOf(ui.anchorId).takeIf { it >= 0 } ?: current.takeIf { it >= 0 } ?: to
        val range = ids.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
        local.update {
            it.copy(
                focusId = id,
                focusRev = it.focusRev + 1,
                anchorId = ids[from],
                checkedIds = range,
                selectedTaskId = if (follow) id else it.selectedTaskId,
            )
        }
    } else {
        local.update {
            it.copy(
                focusId = id,
                focusRev = it.focusRev + 1,
                anchorId = id,
                checkedIds = emptySet(),
                selectedTaskId = if (follow) id else it.selectedTaskId,
            )
        }
    }
}

internal fun Presenter.keyOpen() {
    val task = keyTarget() ?: return
    if (rowActionFor(task) == RowActionCode.OPEN) openDownloadedFile(taskFilePath(task), task.id)
    else local.update { it.copy(selectedTaskId = task.id) }
}

internal fun Presenter.keyOpenFile() {
    val task = keyTarget() ?: return
    if (rowActionFor(task) == RowActionCode.OPEN) openDownloadedFile(taskFilePath(task), task.id)
}

internal fun Presenter.keyOpenFolder() {
    val task = keyTarget() ?: return
    if (headless) return
    scope.launch(Dispatchers.IO) { pathResolver.openFileLocation(taskFullPath(task)) }
}

internal fun Presenter.keyResume() {
    for (task in keyTargets()) {
        val action = rowActionFor(task)
        if (action == RowActionCode.RESUME || action == RowActionCode.START_NOW) {
            vm.onEvent(DashboardUiEvent.OnResumeClicked(task.id))
        }
    }
}

internal fun Presenter.keyPause() {
    val running = keyTargets().filter { rowActionFor(it) == RowActionCode.PAUSE }
    val single = running.singleOrNull()
    if (single != null && single.canResume() == false) {
        local.update { it.copy(restartPauseTaskId = single.id) }
        return
    }
    for (task in running) {
        if (task.canResume() != false) vm.onEvent(DashboardUiEvent.OnPauseClicked(task.id))
    }
}
