package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import kotlinx.coroutines.launch
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.ui.FdmSettingsPanel

@Composable
internal fun SettingsContentView(
    state: com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState,
    viewModel: DashboardViewModel,
    pathResolver: PathResolver
) {
    FdmSettingsPanel(
        destinationPath = state.destinationPath,
        disableTorrentUpload = state.disableTorrentUpload,
        isDarkMode = state.isDarkMode,
        globalDownloadSpeedLimitBytes = state.globalDownloadSpeedLimitBytes,
        globalUploadSpeedLimitBytes = state.globalUploadSpeedLimitBytes,
        speedDisplayUnit = state.speedDisplayUnit,
        speedPresetLowBytes = state.speedPresetLowBytes,
        speedPresetLowUnit = state.speedPresetLowUnit,
        speedPresetMediumBytes = state.speedPresetMediumBytes,
        speedPresetMediumUnit = state.speedPresetMediumUnit,
        speedPresetFastBytes = state.speedPresetFastBytes,
        speedPresetFastUnit = state.speedPresetFastUnit,
        speedPresetHighBytes = state.speedPresetHighBytes,
        speedPresetHighUnit = state.speedPresetHighUnit,
        ytDlpStatus = state.ytDlpStatus,
        isYtDlpInstalled = state.isYtDlpInstalled,
        isFfmpegInstalled = state.isFfmpegInstalled,
        jsRuntimeName = state.jsRuntimeName,
        isJsRuntimeReady = state.isJsRuntimeReady,
        toolsDirectoryPath = state.toolsDirectoryPath,
        torrentEngineStatus = state.torrentEngineStatus,
        isTorrentEngineReady = state.isTorrentEngineReady,
        isDownloadingYtDlp = state.isDownloadingYtDlp,
        toolDownloadName = state.toolDownloadName,
        toolDownloadFraction = state.toolDownloadFraction,
        monitorClipboard = state.monitorClipboard,
        clipboardVideoPages = state.clipboardVideoPages,
        sortIntoFolders = state.sortIntoFolders,
        extractArchives = state.extractArchives,
        defaultConnections = state.defaultConnections,
        pathResolver = pathResolver,
        onDestinationPathChanged = { viewModel.onEvent(DashboardUiEvent.OnDestinationPathChanged(it)) },
        onMonitorClipboardChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnMonitorClipboardChanged(
                    it
                )
            )
        },
        onClipboardVideoPagesChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnClipboardVideoPagesChanged(
                    it
                )
            )
        },
        onSortIntoFoldersChanged = { viewModel.onEvent(DashboardUiEvent.OnSortIntoFoldersChanged(it)) },
        onExtractArchivesChanged = { viewModel.onEvent(DashboardUiEvent.OnExtractArchivesChanged(it)) },
        themeMode = state.themeMode,
        onThemeModeChanged = { viewModel.onEvent(DashboardUiEvent.OnThemeModeChanged(it)) },
        pairedDevices = state.pairedDevices,
        onUnpairDevice = { viewModel.onEvent(DashboardUiEvent.OnUnpairDeviceClicked(it)) },
        blockedDevices = state.blockedDevices,
        onUnblockDevice = { viewModel.onEvent(DashboardUiEvent.OnUnblockDeviceClicked(it)) },
        onDefaultConnectionsChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnDefaultConnectionsChanged(
                    it
                )
            )
        },
        onDisableTorrentUploadToggled = {
            viewModel.onEvent(
                DashboardUiEvent.OnDisableTorrentUploadToggled(
                    it
                )
            )
        },
        onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeToggled) },
        onGlobalDownloadSpeedLimitChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged(
                    it
                )
            )
        },
        onGlobalUploadSpeedLimitChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnGlobalUploadSpeedLimitChanged(
                    it
                )
            )
        },
        onSpeedDisplayUnitChanged = {
            viewModel.onEvent(
                DashboardUiEvent.OnSpeedDisplayUnitChanged(
                    it
                )
            )
        },
        onSpeedPresetChanged = { preset, bytes, unit ->
            viewModel.onEvent(DashboardUiEvent.OnSpeedPresetChanged(preset, bytes, unit))
        },
        onDownloadYtDlpClicked = { viewModel.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked) },
        onLanguageChanged = { viewModel.onEvent(DashboardUiEvent.OnLanguageChanged(it)) },
        onApplySettings = { viewModel.onEvent(DashboardUiEvent.OnApplySettingsClicked) }
    )
}

internal class MoreNav {
    var inSettings by androidx.compose.runtime.mutableStateOf(false)
    var hiddenFlow by androidx.compose.runtime.mutableStateOf(false)

    fun reset() {
        inSettings = false
        hiddenFlow = false
    }
}

@Composable
internal fun MoreContentView(
    state: com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState,
    viewModel: DashboardViewModel,
    pathResolver: PathResolver,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    nav: MoreNav
) {
    var inSettings by nav::inSettings
    val jump by com.michelle.raouf.fetch_jet.ui.SettingsJump.section.collectAsState()
    androidx.compose.runtime.LaunchedEffect(jump) {
        if (jump != null) inSettings = true
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hiddenFlow by nav::hiddenFlow
    val hiddenUnlocked = state.hidden.unlocked
    androidx.compose.runtime.LaunchedEffect(hiddenUnlocked) {
        if (!hiddenUnlocked) hiddenFlow = false
    }
    if (hiddenFlow && hiddenUnlocked) {
        com.michelle.raouf.fetch_jet.ui.HiddenScreen(
            state = state,
            viewModel = viewModel,
            onClose = { hiddenFlow = false },
            stillOpen = { nav.hiddenFlow && viewModel.uiState.value.selectedTab == com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab.SETTINGS }
        )
    } else if (!inSettings) {
        com.michelle.raouf.fetch_jet.ui.MorePage(
            onOpenSettings = { inSettings = true },
            onOpenHidden = { hiddenFlow = true },
            onOpenBrowser = { com.michelle.raouf.fetch_jet.core.network.BrowserRequests.open("") },
            onBackup = { viewModel.onEvent(com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent.OnBackupExportRequested) },
            onRestore = {
                com.michelle.raouf.fetch_jet.core.filesystem.TextFilePicker.pick { text ->
                    if (text != null) viewModel.onEvent(com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent.OnBackupRestoreText(text))
                }
            },
            onToast = { text -> scope.launch { snackbarHostState.showSnackbar(text) } }
        )
        if (hiddenFlow) {
            if (state.hidden.lockType == null) {
                com.michelle.raouf.fetch_jet.ui.SetPinDialog(
                    viewModel = viewModel,
                    onDismiss = { hiddenFlow = false },
                    onDone = {}
                )
            } else {
                com.michelle.raouf.fetch_jet.ui.UnlockPinDialog(
                    viewModel = viewModel,
                    onDismiss = { hiddenFlow = false }
                )
            }
        }
    } else {
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .padding(start = 2.dp, bottom = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .clickable { inSettings = false }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "→" else "←",
                    fontSize = 18.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    com.tbib.kmp_localization.LocalizationManager.Strings.moreTitle,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f).fillMaxSize()) {
                SettingsContentView(state = state, viewModel = viewModel, pathResolver = pathResolver)
            }
        }
    }
}
