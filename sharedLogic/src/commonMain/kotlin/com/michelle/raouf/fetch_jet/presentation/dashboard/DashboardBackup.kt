package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.backup.AppBackup
import com.michelle.raouf.fetch_jet.feature.backup.BackupExport
import com.michelle.raouf.fetch_jet.feature.backup.BackupFailure
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupNoticeKind { RESTORED, NOTHING_NEW, INVALID, HIDDEN_BLOCKED, FAILED }

data class BackupNotice(
    val kind: BackupNoticeKind,
    val tasksAdded: Int = 0,
    val tasksSkipped: Int = 0,
    val needRestart: Int = 0,
    val wrongPlatform: Boolean = false
)

data class BackupUiState(
    val text: String? = null,
    val fileName: String = "",
    val notice: BackupNotice? = null
)

fun backupNoticeText(notice: BackupNotice): String {
    val s = LocalizationManager.Strings
    fun details(first: String): String = buildList {
        add(first)
        if (notice.needRestart > 0) add(s.backupNeedRestart.replace("{n}", notice.needRestart.toString()))
        if (notice.tasksSkipped > 0) add(s.backupSkipped.replace("{n}", notice.tasksSkipped.toString()))
        if (notice.wrongPlatform) add(s.backupWrongPlatform)
    }.joinToString(" ")
    return when (notice.kind) {
        BackupNoticeKind.RESTORED ->
            details(if (notice.tasksAdded > 0) s.backupDone.replace("{n}", notice.tasksAdded.toString()) else s.backupSettingsDone)
        BackupNoticeKind.NOTHING_NEW -> details(s.backupNothingNew)
        BackupNoticeKind.INVALID -> s.backupInvalid
        BackupNoticeKind.HIDDEN_BLOCKED -> s.backupHiddenBlocked
        BackupNoticeKind.FAILED -> s.backupFailed
    }
}

internal fun DashboardViewModel.handleBackupEvent(event: DashboardUiEvent) {
    when (event) {
        DashboardUiEvent.OnBackupExportRequested -> scope.launch {
            when (val made = AppBackup.create(queueRepository)) {
                is BackupExport.Ready -> _uiState.update { it.copy(backup = BackupUiState(text = made.text, fileName = made.fileName)) }
                is BackupExport.Failed -> _uiState.update {
                    val kind = if (made.failure == BackupFailure.BACKUP_HIDDEN_BLOCKED) BackupNoticeKind.HIDDEN_BLOCKED else BackupNoticeKind.FAILED
                    it.copy(backup = BackupUiState(notice = BackupNotice(kind)))
                }
            }
        }

        DashboardUiEvent.OnBackupWritten ->
            _uiState.update { it.copy(backup = it.backup.copy(text = null, fileName = "")) }

        is DashboardUiEvent.OnBackupRestoreText -> scope.launch {
            val notice = try {
                val result = AppBackup.restore(event.text, queueRepository)
                if (result.settingsApplied > 0) applyLoadedSettings(settingsRepository.loadSettings(), force = true)
                val kind = when {
                    !result.valid -> BackupNoticeKind.INVALID
                    result.tasksAdded + result.settingsApplied + result.playlistsAdded > 0 -> BackupNoticeKind.RESTORED
                    result.errors > 0 -> BackupNoticeKind.FAILED
                    else -> BackupNoticeKind.NOTHING_NEW
                }
                BackupNotice(
                    kind,
                    tasksAdded = result.tasksAdded,
                    tasksSkipped = result.tasksSkipped,
                    needRestart = result.needRestart,
                    wrongPlatform = result.wrongPlatform
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                BackupNotice(BackupNoticeKind.FAILED)
            }
            _uiState.update { it.copy(backup = it.backup.copy(notice = notice)) }
        }

        DashboardUiEvent.OnBackupNoticeShown ->
            _uiState.update { it.copy(backup = it.backup.copy(notice = null)) }

        else -> Unit
    }
}
