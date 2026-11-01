package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
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

        DashboardUiEvent.OnApplySettingsClicked -> {

            queueRepository.setGlobalDownloadSpeedLimit(_uiState.value.globalDownloadSpeedLimitBytes)

            queueRepository.setGlobalUploadSpeedLimit(_uiState.value.globalUploadSpeedLimitBytes)

            persistCurrentSettings()

        }

        DashboardUiEvent.OnThemeToggled -> {

            _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }

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
                        .getOrElse { "BitTorrent engine error: ${it.message}" }
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
                            "BitTorrent engine install failed: ${e.message}"
                        } ?: torrentStatus2,
                        isTorrentEngineReady = torrentReady2

                    )

                }

                val msg =

                    if (success) "Media tools downloaded successfully!" else "Failed to download media tools"

                _uiEffect.emit(DashboardUiEffect.ShowToast(msg))

            }

        }

        else -> Unit
    }
}
