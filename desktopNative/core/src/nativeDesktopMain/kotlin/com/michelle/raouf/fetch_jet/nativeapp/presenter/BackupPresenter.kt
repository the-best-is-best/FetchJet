package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.backup.AppBackup
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.backupNoticeText
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun Presenter.startBackup() {
    scope.launch {
        vm.uiState.map { it.backup.text to it.backup.fileName }.distinctUntilChanged().collect { (text, name) ->
            if (text != null) writeBackup(text, name)
        }
    }
    scope.launch {
        vm.uiState.map { it.backup.notice }.distinctUntilChanged().filterNotNull().collect { notice ->
            showToast(backupNoticeText(notice))
            vm.onEvent(DashboardUiEvent.OnBackupNoticeShown)
        }
    }
}

private suspend fun Presenter.writeBackup(text: String, name: String) {
    val st = LocalizationManager.Strings
    val folder = pathResolver.getDownloadsDirectory()
    val saved = withContext(Dispatchers.IO) { saveBackupFile(folder, name, text) }
    showToast(if (saved != null) st.detailsSavedTo + ": " + saved else st.backupFailed)
    vm.onEvent(DashboardUiEvent.OnBackupWritten)
}

private fun saveBackupFile(folder: String, name: String, text: String): String? {
    DesktopOs.createDirectories(folder)
    val stem = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var path = DesktopOs.join(folder, name)
    var copy = 1
    while (DesktopOs.fileInfo(path) != null && copy < 1000) {
        path = DesktopOs.join(folder, "$stem ($copy)$extension")
        copy++
    }
    return if (DesktopOs.writeText(path, text)) path else null
}

internal fun Presenter.backupExport() {
    vm.onEvent(DashboardUiEvent.OnBackupExportRequested)
}

internal fun Presenter.backupRestorePick() {
    val pick = HostHooks.pickFile ?: return
    scope.launch {
        val st = LocalizationManager.Strings
        val picked = withContext(Dispatchers.IO) {
            val path = pick(st.backupRestore, "FetchJet backup", "*." + AppBackup.EXTENSION + ";*.json") ?: return@withContext null
            DesktopOs.readText(path) ?: ""
        }
        if (picked == null) return@launch
        if (picked.isEmpty()) showToast(st.backupInvalid) else vm.onEvent(DashboardUiEvent.OnBackupRestoreText(picked))
    }
}
