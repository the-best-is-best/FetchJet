package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun DashboardViewModel.handleSettingsEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnMonitorClipboardChanged -> BehaviorSettings.setMonitorClipboard(
            event.on
        )

        is DashboardUiEvent.OnClipboardVideoPagesChanged -> BehaviorSettings.setClipboardVideoPages(
            event.on
        )

        is DashboardUiEvent.OnSortIntoFoldersChanged -> BehaviorSettings.setSortIntoFolders(
            event.on
        )

        is DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged -> {

            _uiState.update {

                it.copy(

                    globalDownloadSpeedLimitBytes = event.limitBytesPerSec

                )

            }

            queueRepository.setGlobalDownloadSpeedLimit(event.limitBytesPerSec)

            persistCurrentSettings()

        }

        is DashboardUiEvent.OnGlobalUploadSpeedLimitChanged -> {

            _uiState.update {

                it.copy(

                    globalUploadSpeedLimitBytes = event.limitBytesPerSec

                )

            }

            queueRepository.setGlobalUploadSpeedLimit(event.limitBytesPerSec)

            persistCurrentSettings()

        }

        is DashboardUiEvent.OnSpeedDisplayUnitChanged -> {
            _uiState.update { it.copy(speedDisplayUnit = event.unit) }
            persistCurrentSettings()
        }

        is DashboardUiEvent.OnSpeedPresetChanged -> {
            val current = _uiState.value
            val oldBytes = when (event.preset) {
                SpeedPreset.LOW -> current.speedPresetLowBytes
                SpeedPreset.MEDIUM -> current.speedPresetMediumBytes
                SpeedPreset.FAST -> current.speedPresetFastBytes
                SpeedPreset.HIGH -> current.speedPresetHighBytes
            }
            _uiState.update {
                when (event.preset) {
                    SpeedPreset.LOW -> it.copy(
                        speedPresetLowBytes = event.bytes,
                        speedPresetLowUnit = event.unit
                    )

                    SpeedPreset.MEDIUM -> it.copy(
                        speedPresetMediumBytes = event.bytes,
                        speedPresetMediumUnit = event.unit
                    )

                    SpeedPreset.FAST -> it.copy(
                        speedPresetFastBytes = event.bytes,
                        speedPresetFastUnit = event.unit
                    )

                    // HIGH is always Unlimited; it is not edited through this event.
                    SpeedPreset.HIGH -> it
                }
            }
            // If this preset is the one currently capping downloads, apply the new value live.
            if (event.bytes != oldBytes &&
                _uiState.value.globalDownloadSpeedLimitBytes == oldBytes
            ) {
                _uiState.update { it.copy(globalDownloadSpeedLimitBytes = event.bytes) }
                queueRepository.setGlobalDownloadSpeedLimit(event.bytes)
            }
            persistCurrentSettings()
        }

        DashboardUiEvent.OnApplySettingsClicked -> {

            queueRepository.setGlobalDownloadSpeedLimit(_uiState.value.globalDownloadSpeedLimitBytes)

            queueRepository.setGlobalUploadSpeedLimit(_uiState.value.globalUploadSpeedLimitBytes)

            persistCurrentSettings()

        }

        is DashboardUiEvent.OnLanguageChanged -> {
            com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.change(event.choice)
            persistCurrentSettings()
        }

        DashboardUiEvent.OnThemeToggled -> {
            _uiState.update {
                val dark = it.themeMode != "dark"
                it.copy(themeMode = if (dark) "dark" else "light", isDarkMode = dark)
            }
            persistCurrentSettings()
        }

        is DashboardUiEvent.OnThemeModeChanged -> {
            _uiState.update {
                it.copy(
                    themeMode = event.mode,
                    isDarkMode = when (event.mode) { "light" -> false; "dark" -> true; else -> it.isDarkMode }
                )
            }
            persistCurrentSettings()
        }

        is DashboardUiEvent.OnTabSelected -> {

            _uiState.update { state ->

                state.copy(

                    selectedTab = event.tab,

                    filteredTasks = filterTasks(state.allTasks, event.tab, state.sortOption)

                )

            }

        }

        is DashboardUiEvent.OnSortChanged -> {
            _uiState.update { state ->
                state.copy(
                    sortOption = event.sort,
                    filteredTasks = filterTasks(state.allTasks, state.selectedTab, event.sort)
                )
            }
        }

        is DashboardUiEvent.OnDefaultConnectionsChanged -> BehaviorSettings.setConnections(event.connections)
        DashboardUiEvent.OnDownloadYtDlpClicked -> {

            _uiState.update { it.copy(isDownloadingYtDlp = true) }

            scope.launch {

                val success = toolsMutex.withLock {
                    com.michelle.raouf.fetch_jet.feature.media_extractor.downloadMediaExtractorTool(
                        ::reportToolProgress
                    )
                }

                // Same button also installs the BitTorrent engine if it is missing.
                val torrentInstall =
                    runCatching { queueRepository.torrentEngine.installEngine() }
                        .getOrElse { Result.failure(it) }
                val torrentStatus2 =
                    runCatching { queueRepository.torrentEngine.engineStatus() }
                        .getOrElse { LocalizationManager.Strings.sysTorrentEngineError(it.message.toString()) }
                val torrentReady2 = torrentInstall.isSuccess ||
                        runCatching { queueRepository.torrentEngine.isSupported }.getOrDefault(
                            false
                        )
                val newStatus =

                    com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaExtractorToolStatus()

                val isYtDlp =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()

                val isFfmpeg =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.isFfmpegToolInstalled()

                val jsRuntime =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.getJsRuntimeStatus()

                val toolsDir =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaToolsDirectory()

                _uiState.update {

                    it.copy(

                        isDownloadingYtDlp = false,
                        toolDownloadName = null,
                        toolDownloadFraction = null,

                        ytDlpStatus = newStatus,

                        isYtDlpInstalled = isYtDlp,

                        isFfmpegInstalled = isFfmpeg,

                        jsRuntimeName = jsRuntime.name,
                        isJsRuntimeReady = jsRuntime.isReady,

                        toolsDirectoryPath = toolsDir,
                        torrentEngineStatus = torrentInstall.exceptionOrNull()?.let { e ->
                            LocalizationManager.Strings.sysTorrentEngineInstallFailed(e.message.toString())
                        } ?: torrentStatus2,
                        isTorrentEngineReady = torrentReady2

                    )

                }

                val msg =

                    if (success) LocalizationManager.Strings.sysMediaToolsDownloaded else LocalizationManager.Strings.sysMediaToolsDownloadFailed

                _uiEffect.emit(DashboardUiEffect.ShowToast(msg))

            }

        }

        else -> Unit
    }
}
