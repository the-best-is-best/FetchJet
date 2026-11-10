package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.nativeapp.actions.UiAction
import com.michelle.raouf.fetch_jet.nativeapp.dto.ListKeyCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowActionCode
import com.michelle.raouf.fetch_jet.platform.win.winSetClipboardText
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Presenter.dispatch(action: UiAction) {
    when (action) {
        is UiAction.SystemTheme -> local.update { it.copy(systemDark = action.dark) }
        is UiAction.SetFilter -> {
            val filter = SideFilter.entries.getOrNull(action.filter) ?: return
            local.update { it.copy(sideFilter = filter) }
            vm.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.ALL))
        }

        UiAction.OpenSettings -> vm.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.SETTINGS))
        is UiAction.SetSearch -> local.update { it.copy(search = action.text) }
        UiAction.ToggleTurtle -> {
            val limit = vm.uiState.value.globalDownloadSpeedLimitBytes
            vm.onEvent(DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged(if (limit == TURTLE_BYTES) 0L else TURTLE_BYTES))
        }

        is UiAction.PickSpeedOption -> {
            val state = vm.uiState.value
            val value = when (action.index) {
                0 -> state.speedPresetLowBytes
                1 -> state.speedPresetMediumBytes
                2 -> state.speedPresetFastBytes
                3 -> 0L
                else -> return
            }
            vm.onEvent(DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged(value))
        }

        UiAction.ToggleTheme -> {
            val dark = resolveDark(vm.uiState.value.themeMode, local.value.systemDark)
            vm.onEvent(DashboardUiEvent.OnThemeModeChanged(if (dark) "light" else "dark"))
        }

        is UiAction.SetSort -> {
            val sort = TaskSortOption.entries.getOrNull(action.index) ?: return
            vm.onEvent(DashboardUiEvent.OnSortChanged(sort))
        }

        UiAction.PauseAll -> vm.onEvent(DashboardUiEvent.OnPauseAllClicked)
        UiAction.ResumeAll -> vm.onEvent(DashboardUiEvent.OnResumeAllClicked)
        is UiAction.RowClick -> rowClick(action.id, action.control, action.shift)
        is UiAction.RowCheck -> local.update {
            it.copy(
                anchorId = action.id,
                checkedIds = if (action.checked) it.checkedIds + action.id else it.checkedIds - action.id,
            )
        }

        is UiAction.RowQuickAction -> rowQuickAction(action.id)
        is UiAction.CyclePriority -> {
            val task = task(action.id) ?: return
            vm.onEvent(DashboardUiEvent.OnTaskPriorityChanged(task.id, nextPriority(task.priority)))
        }

        is UiAction.CheckAll -> {
            val ids = if (action.checked) shownIds.toSet() else emptySet()
            local.update { it.copy(checkedIds = ids) }
        }

        UiAction.ClearChecks -> local.update { it.copy(checkedIds = emptySet()) }
        UiAction.RemoveChecked -> removeChecked()
        UiAction.ExportChecked -> {
            val ids = local.value.checkedIds.toList()
            if (ids.isNotEmpty()) vm.onEvent(DashboardUiEvent.OnExportLinksClicked(ids))
        }

        is UiAction.ToggleGroup -> local.update {
            it.copy(
                collapsedGroups = if (action.key in it.collapsedGroups) it.collapsedGroups - action.key
                else it.collapsedGroups + action.key
            )
        }

        is UiAction.ListKey -> listKey(action.key)
        UiAction.CloseDetails -> local.update { it.copy(selectedTaskId = null) }
        UiAction.CancelWhenDone -> WhenDone.cancel()
        is UiAction.SetWhenDone -> WhenDoneAction.entries.getOrNull(action.index)?.let { WhenDone.choose(it) }
        is UiAction.SetUrl -> addSetUrl(action.text)
        is UiAction.ShowAddDialog -> vm.onEvent(DashboardUiEvent.OnToggleAddDialog(action.show))
        UiAction.StartDownload -> vm.onEvent(DashboardUiEvent.OnAddDownloadClicked)
        is UiAction.SetDestination -> {
            addFields.destination.fromUi(action.path)
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(action.path))
        }

        is UiAction.SetFileName -> {
            addFields.fileName.fromUi(action.name)
            vm.onEvent(DashboardUiEvent.OnFileNameChanged(action.name))
        }
        is UiAction.Pause -> vm.onEvent(DashboardUiEvent.OnPauseClicked(action.id))
        is UiAction.Resume -> vm.onEvent(DashboardUiEvent.OnResumeClicked(action.id))
        is UiAction.Remove -> vm.onEvent(DashboardUiEvent.OnRemoveClicked(action.id))
        is UiAction.ConfirmRemove -> vm.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(action.deleteFiles))
        UiAction.DismissRemove -> vm.onEvent(DashboardUiEvent.OnDismissRemoveDialog)
        is UiAction.SetLanguage -> vm.onEvent(DashboardUiEvent.OnLanguageChanged(action.choice))
        is UiAction.DetailsAction -> detailsAction(action.code, action.primary)
        UiAction.DetailsRemove -> local.value.selectedTaskId?.let { vm.onEvent(DashboardUiEvent.OnCancelClicked(it)) }
        UiAction.DetailsShare -> local.value.selectedTaskId?.let { vm.onEvent(DashboardUiEvent.OnShareTaskClicked(it)) }
        is UiAction.ToggleDetailFile -> local.value.selectedTaskId?.let {
            vm.onEvent(DashboardUiEvent.OnTaskTorrentFileSelectionToggled(it, action.index, action.selected))
        }

        is UiAction.CycleDetailFilePriority -> {
            val task = local.value.selectedTaskId?.let { task(it) } ?: return
            val file = task.torrentFiles.firstOrNull { it.index == action.index } ?: return
            val next = when (file.priority) {
                0 -> 1
                1 -> -1
                else -> 0
            }
            vm.onEvent(DashboardUiEvent.OnTaskTorrentFilePriorityChanged(task.id, action.index, next))
        }

        is UiAction.CopyText -> scope.launch(Dispatchers.IO) { winSetClipboardText(action.text) }
        is UiAction.AlertConfirm -> alertConfirm(action.kind, action.checked)
        is UiAction.AlertDismiss -> alertDismiss(action.kind, action.fromScrim)
        is UiAction.AlertExtra -> alertExtra(action.kind)
        is UiAction.SetSettingsSection -> settingsOpenSection(action.index)
        is UiAction.SettingToggle -> settingToggle(action.id, action.on)
        is UiAction.SettingPick -> settingPick(action.id, action.index)
        is UiAction.SettingText -> settingText(action.id, action.text)
        is UiAction.SettingClick -> settingClick(action.id)
        UiAction.InspectTorrent -> vm.onEvent(DashboardUiEvent.OnInspectTorrentClicked)
        is UiAction.ToggleTorrentFile -> vm.onEvent(DashboardUiEvent.OnTorrentFileSelectionToggled(action.index, action.selected))
        is UiAction.CycleTorrentFilePriority -> cycleTorrentFilePriority(action.index)
        is UiAction.SelectAllTorrentFiles -> vm.onEvent(DashboardUiEvent.OnSelectAllTorrentFilesToggled(action.selected))
        is UiAction.ToggleLinkTorrentFile ->
            vm.onEvent(DashboardUiEvent.OnLinkListTorrentFileToggled(action.link, action.index, action.selected))

        is UiAction.CycleLinkTorrentFilePriority -> cycleLinkTorrentFilePriority(action.link, action.index)
        is UiAction.SelectAllLinkTorrentFiles -> vm.onEvent(DashboardUiEvent.OnLinkListTorrentSelectAll(action.link, action.selected))
        is UiAction.DeviceSelect -> deviceSelect(action.id)
        is UiAction.DeviceSetMode -> deviceSetMode(action.transfer)
        is UiAction.DeviceSetDeleteAfter -> deviceSetDeleteAfter(action.on)
        is UiAction.DeviceSearch -> deviceSearch(action.text)
        UiAction.DeviceToggleManual -> deviceToggleManual()
        is UiAction.DeviceManualIp -> deviceManualIp(action.text)
        UiAction.DeviceManualConnect -> deviceManualConnect()
        UiAction.DeviceSend -> deviceSend()
        UiAction.DeviceDismiss -> deviceDismiss()
        UiAction.PairingConfirm -> vm.onEvent(DashboardUiEvent.OnConfirmPairingClicked)
        UiAction.PairingCancel -> vm.onEvent(DashboardUiEvent.OnCancelPairingClicked)
        UiAction.PairingBlock -> vm.onEvent(DashboardUiEvent.OnBlockPairingClicked)
        UiAction.UpdateConfirm -> updateConfirm()
        UiAction.UpdateSkip -> updateSkip()
        is UiAction.UpdateLater -> updateLater(action.fromScrim)
        UiAction.PasteUrl -> addPasteUrl()
        UiAction.ImportLinksFile -> addImportLinks()
        UiAction.OpenTorrentFile -> addOpenTorrent()
        is UiAction.SetEngine -> EngineType.entries.getOrNull(action.engine)?.let { vm.onEvent(DashboardUiEvent.OnEngineSelected(it)) }
        is UiAction.ScheduleQuick -> addScheduleQuick(action.minutes)
        UiAction.ScheduleAtTime -> addScheduleAtTime()
        is UiAction.SetScheduleDay -> addLocal.update { it.copy(dayOffset = action.offset) }
        is UiAction.SetScheduleHour -> {
            val digits = action.text.filter { c -> c.isDigit() }.take(2)
            if (digits == action.text) addFields.hour.fromUi(action.text) else addFields.hour.force()
            addLocal.update { it.copy(hour = digits) }
        }

        is UiAction.SetScheduleMinute -> {
            val digits = action.text.filter { c -> c.isDigit() }.take(2)
            if (digits == action.text) addFields.minute.fromUi(action.text) else addFields.minute.force()
            addLocal.update { it.copy(minute = digits) }
        }

        is UiAction.SetSchedulePm -> addLocal.update { it.copy(pm = action.pm) }
        UiAction.ApplyScheduleTime -> addApplyScheduleTime()
        is UiAction.SetConnections -> vm.onEvent(DashboardUiEvent.OnConnectionsChanged(action.count))
        UiAction.ResetFileName -> {
            addFields.fileName.force()
            vm.onEvent(DashboardUiEvent.OnFileNameChanged(""))
        }

        is UiAction.SetChecksum -> {
            addFields.checksum.fromUi(action.value)
            vm.onEvent(DashboardUiEvent.OnChecksumChanged(action.value))
        }

        UiAction.PickDestination -> addPickDestination()
        is UiAction.SetQuickFolder -> {
            addFields.destination.force()
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(action.path))
        }

        is UiAction.SetLeech -> vm.onEvent(DashboardUiEvent.OnDisableTorrentUploadToggled(action.on))
        UiAction.RetryInspect -> addRetryInspect()
        UiAction.InspectMedia -> vm.onEvent(DashboardUiEvent.OnInspectMediaClicked)
        UiAction.DownloadTools -> vm.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked)
        is UiAction.ToggleQuality -> vm.onEvent(DashboardUiEvent.OnMediaQualitySelectionToggled(action.index, action.selected))
        is UiAction.SelectAllQualities -> vm.onEvent(DashboardUiEvent.OnSelectAllMediaQualitiesToggled(action.selected))
        is UiAction.ToggleSubtitle -> vm.onEvent(DashboardUiEvent.OnSubtitleToggled(action.code, action.selected))
        is UiAction.SetEmbedSubtitles -> vm.onEvent(DashboardUiEvent.OnToggleEmbedSubtitles(action.embed))
        is UiAction.SetClipEnabled -> vm.onEvent(DashboardUiEvent.OnToggleClipRange(action.on))
        is UiAction.SetClipRange -> {
            vm.onEvent(DashboardUiEvent.OnClipStartTimeChanged(action.start))
            vm.onEvent(DashboardUiEvent.OnClipEndTimeChanged(action.end))
        }

        is UiAction.SetClipFastMode -> vm.onEvent(DashboardUiEvent.OnToggleClipFastMode(action.on))
        is UiAction.OpenClipPicker -> clipPickerOpen(action.end)
        is UiAction.ClipPickerType -> clipPickerType(action.field, action.text)
        is UiAction.ClipPickerStep -> clipPickerStep(action.field, action.up)
        is UiAction.ClipPickerQuick -> clipPickerQuick(action.code)
        UiAction.ClipPickerApply -> clipPickerApply()
        UiAction.ClipPickerClose -> clipPicker.value = ClipPicker()
        is UiAction.SetAudioFormat -> vm.onEvent(DashboardUiEvent.OnAudioConvertFormatSelected(action.format.ifEmpty { null }))
        is UiAction.TogglePlaylistEntry -> vm.onEvent(DashboardUiEvent.OnPlaylistEntryToggled(action.index, action.selected))
        is UiAction.SelectAllPlaylist -> vm.onEvent(DashboardUiEvent.OnPlaylistSelectAll(action.selected))
        is UiAction.ToggleLink -> vm.onEvent(DashboardUiEvent.OnLinkListEntryToggled(action.index, action.selected))
        is UiAction.SelectAllLinks -> vm.onEvent(DashboardUiEvent.OnLinkListSelectAll(action.selected))
        is UiAction.SetLinkFolder -> {
            addFields.linkFolder.fromUi(action.name)
            vm.onEvent(DashboardUiEvent.OnLinkListFolderChanged(action.name))
        }

        is UiAction.SetLinkQuality -> vm.onEvent(DashboardUiEvent.OnLinkListQualitySelected(action.index, action.quality))
        is UiAction.RetryLinkInspect -> vm.onEvent(DashboardUiEvent.OnLinkListRetryInspect(action.index))
        is UiAction.SetPlaylistFolder -> {
            addFields.playlistFolder.fromUi(action.name)
            vm.onEvent(DashboardUiEvent.OnPlaylistFolderChanged(action.name))
        }

        is UiAction.SetPlaylistQuality -> PlaylistQuality.entries.getOrNull(action.quality)?.let {
            vm.onEvent(DashboardUiEvent.OnPlaylistQualityChanged(it))
        }

        UiAction.UseSingleVideo -> vm.onEvent(DashboardUiEvent.OnPlaylistUseSingleVideo)
        is UiAction.ChoicePrimary -> choicePrimary(action.kind)
        is UiAction.ChoiceSecondary -> choiceSecondary(action.kind)
        is UiAction.ChoiceDismiss -> choiceDismiss(action.kind)
    }
}

internal fun Presenter.task(id: String): DownloadTask? = vm.uiState.value.allTasks.firstOrNull { it.id == id }

private fun Presenter.rowClick(id: String, control: Boolean, shift: Boolean) {
    val ui = local.value
    when {
        control -> local.update {
            it.copy(anchorId = id, checkedIds = if (id in it.checkedIds) it.checkedIds - id else it.checkedIds + id)
        }

        shift -> {
            val ids = shownOrder
            val from = ids.indexOf(ui.anchorId ?: ui.selectedTaskId).takeIf { it >= 0 } ?: 0
            val to = ids.indexOf(id)
            if (to >= 0) {
                val range = ids.subList(minOf(from, to), maxOf(from, to) + 1)
                local.update { it.copy(checkedIds = it.checkedIds + range) }
            }
        }

        ui.checkedIds.isNotEmpty() -> local.update {
            it.copy(anchorId = id, checkedIds = if (id in it.checkedIds) it.checkedIds - id else it.checkedIds + id)
        }

        else -> local.update { it.copy(anchorId = id, selectedTaskId = id) }
    }
}

private fun Presenter.rowQuickAction(id: String) {
    val task = task(id) ?: return
    when (rowActionFor(task)) {
        RowActionCode.PAUSE ->
            if (task.canResume() == false) local.update { it.copy(selectedTaskId = id) }
            else vm.onEvent(DashboardUiEvent.OnPauseClicked(id))

        RowActionCode.RESUME, RowActionCode.START_NOW -> vm.onEvent(DashboardUiEvent.OnResumeClicked(id))
        RowActionCode.OPEN -> openDownloadedFile(taskFilePath(task), id)
        RowActionCode.NONE -> Unit
    }
}

internal fun Presenter.openDownloadedFile(fullPath: String, taskId: String) {
    scope.launch(Dispatchers.IO) {
        if (!pathResolver.fileExists(fullPath)) {
            local.update { it.copy(missingFileTaskId = taskId) }
            return@launch
        }
        if (headless) return@launch
        if (!pathResolver.openFile(fullPath)) pathResolver.openFileLocation(fullPath)
    }
}

private fun Presenter.removeChecked() {
    val ids = local.value.checkedIds.toList()
    if (ids.isNotEmpty()) vm.onEvent(DashboardUiEvent.OnRemoveSelectedClicked(ids))
}

private fun Presenter.listKey(key: Int) {
    when (ListKeyCode.entries.getOrNull(key)) {
        ListKeyCode.SELECT_ALL -> {
            val ids = shownOrder.toSet()
            local.update { it.copy(checkedIds = ids) }
        }

        ListKeyCode.ESCAPE -> local.update { it.copy(checkedIds = emptySet()) }
        ListKeyCode.DELETE -> {
            val ui = local.value
            if (ui.checkedIds.isNotEmpty()) removeChecked()
            else ui.selectedTaskId?.let { vm.onEvent(DashboardUiEvent.OnRemoveClicked(it)) }
        }

        null -> Unit
    }
}
