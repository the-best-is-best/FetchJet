package com.michelle.raouf.fetch_jet.presentation.dashboard

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun DashboardViewModel.handleTaskEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnPauseClicked -> {

            scope.launch { queueRepository.pauseTask(event.taskId) }

        }

        is DashboardUiEvent.OnResumeClicked -> {

            scope.launch { queueRepository.resumeTask(event.taskId) }

        }

        is DashboardUiEvent.OnCancelClicked, is DashboardUiEvent.OnRemoveClicked -> {

            val taskId = when (event) {

                is DashboardUiEvent.OnCancelClicked -> event.taskId

                is DashboardUiEvent.OnRemoveClicked -> event.taskId

                else -> return

            }

            _uiState.update { it.copy(removeConfirmationTaskId = taskId) }

        }

        is DashboardUiEvent.OnRemoveSelectedClicked -> {
            if (event.taskIds.isNotEmpty()) {
                _uiState.update { it.copy(removeConfirmationTaskIds = event.taskIds) }
            }
        }

        is DashboardUiEvent.OnConfirmRemoveClicked -> {

            val taskId = _uiState.value.removeConfirmationTaskId
            val taskIds = _uiState.value.removeConfirmationTaskIds

            if (taskId != null) {

                _uiState.update { it.copy(removeConfirmationTaskId = null) }

                scope.launch { queueRepository.removeTask(taskId, event.deleteFile) }

            }

            if (taskIds.isNotEmpty()) {
                _uiState.update { it.copy(removeConfirmationTaskIds = emptyList()) }
                scope.launch {
                    taskIds.forEach {
                        queueRepository.removeTask(
                            it,
                            event.deleteFile
                        )
                    }
                }
            }

        }

        DashboardUiEvent.OnConfirmBrowserFallback -> {
            val url = _uiState.value.browserFallbackUrl
            _uiState.update { it.copy(browserFallbackUrl = null) }
            if (url != null) {
                com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.triggerFallback(
                    url
                )
            }
        }

        DashboardUiEvent.OnDismissBrowserFallback -> {
            _uiState.update { it.copy(browserFallbackUrl = null) }
        }

        DashboardUiEvent.OnConfirmCancelClicked -> {

            val taskId = _uiState.value.removeConfirmationTaskId

            if (taskId != null) {

                _uiState.update { it.copy(removeConfirmationTaskId = null) }

                scope.launch { queueRepository.removeTask(taskId, false) }

            }

        }

        DashboardUiEvent.OnResumeExistingDuplicateClicked -> {
            val duplicate = _uiState.value.duplicateTaskAlert
            if (duplicate != null) {
                scope.launch {
                    queueRepository.resumeTask(duplicate.id)
                    _uiState.update {
                        it.copy(
                            duplicateTaskAlert = null,
                            isAddDialogVisible = false,
                            inputUrl = ""
                        )
                    }
                }
            }
        }

        DashboardUiEvent.OnRestartDuplicateClicked -> {
            val duplicate = _uiState.value.duplicateTaskAlert
            if (duplicate != null) {
                scope.launch {
                    queueRepository.removeTask(duplicate.id, deleteFile = true)
                    _uiState.update { it.copy(duplicateTaskAlert = null) }
                    addDownload(forceOverwrite = true, skipDuplicateCheck = true)
                }
            }
        }

        DashboardUiEvent.OnDismissDuplicateDialog -> {
            _uiState.update { it.copy(duplicateTaskAlert = null) }
        }

        DashboardUiEvent.OnConfirmOverwriteClicked -> {
            val conflict = _uiState.value.fileConflictAlert
            _uiState.update { it.copy(fileConflictAlert = null) }
            val external = pendingExternalConflict
            pendingExternalConflict = null
            scope.launch {
                // The file is replaced: the old entry for it would show a stale download.
                if (conflict != null) {
                    queueRepository.allTasksFlow.value
                        .filter { it.fileName == conflict.fileName && it.destinationPath == conflict.destinationPath }
                        .forEach { queueRepository.removeTask(it.id, deleteFile = false) }
                }
                if (external != null) startExternalDirectly(external, forceOverwrite = true)
                else addDownload(forceOverwrite = true, skipDuplicateCheck = true)
            }
        }

        is DashboardUiEvent.OnSaveAsCopyClicked -> {
            val newName = event.newFileName
            _uiState.update { it.copy(fileConflictAlert = null) }
            pendingExternalConflict?.let { external ->
                pendingExternalConflict = null
                startExternalDirectly(external, overrideFileName = newName)
                return
            }
            addDownload(
                forceOverwrite = false,
                overrideFileName = newName,
                skipDuplicateCheck = true
            )
        }

        DashboardUiEvent.OnDismissFileConflictDialog -> {
            pendingExternalConflict = null
            _uiState.update { it.copy(fileConflictAlert = null) }
        }

        DashboardUiEvent.OnDismissCancelDialog, DashboardUiEvent.OnDismissRemoveDialog -> {

            _uiState.update {
                it.copy(
                    removeConfirmationTaskId = null,
                    removeConfirmationTaskIds = emptyList()
                )
            }

        }

        is DashboardUiEvent.OnDownloadSpeedLimitChanged -> {

            _uiState.update { it.copy(selectedDownloadSpeedLimitBytes = event.limitBytesPerSec) }

        }

        is DashboardUiEvent.OnUploadSpeedLimitChanged -> {

            _uiState.update { it.copy(selectedUploadSpeedLimitBytes = event.limitBytesPerSec) }

        }

        DashboardUiEvent.OnPauseAllClicked -> queueRepository.pauseAll()
        DashboardUiEvent.OnResumeAllClicked -> queueRepository.resumeAll()
        is DashboardUiEvent.OnTaskTorrentFileSelectionToggled -> {

            queueRepository.updateTorrentFileSelection(

                event.taskId,

                event.fileIndex,

                event.isSelected

            )

        }

        is DashboardUiEvent.OnTaskTorrentFilePriorityChanged -> {

            queueRepository.updateTorrentFilePriority(

                event.taskId,

                event.fileIndex,

                event.priority

            )

        }

        else -> Unit
    }
}
