package com.michelle.raouf.fetch_jet

import androidx.compose.runtime.Composable
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
