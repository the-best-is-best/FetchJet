package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskSortOption
import com.michelle.raouf.fetch_jet.theme.EngineBitTorrent
import com.michelle.raouf.fetch_jet.theme.EngineMediaStream
import com.michelle.raouf.fetch_jet.theme.FetchJetTheme
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.michelle.raouf.fetch_jet.ui.AddDownloadModalDialog
import com.michelle.raouf.fetch_jet.ui.DuplicateDownloadDialog
import com.michelle.raouf.fetch_jet.ui.FdmSettingsPanel
import com.michelle.raouf.fetch_jet.ui.FileOverwriteDialog
import com.michelle.raouf.fetch_jet.ui.RemoveConfirmationDialog
import com.michelle.raouf.fetch_jet.ui.RowAction
import com.michelle.raouf.fetch_jet.ui.SideFilter
import com.michelle.raouf.fetch_jet.ui.TaskRow
import com.michelle.raouf.fetch_jet.ui.TaskRowsHeader
import com.michelle.raouf.fetch_jet.utils.decodeUnicodeText
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import fetchjet.sharedui.generated.resources.Res
import fetchjet.sharedui.generated.resources.ic_fetchjet_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Opens a finished download with the system's default app. If the file cannot be opened
 * (no associated app, the engine renamed it, or the OS refused), reveal it in its folder
 * instead so the click never does nothing.
 */
private fun openDownloadedFile(fullPath: String) {
    val resolver = PathResolver()
    if (!resolver.openFile(fullPath)) {
        resolver.openFileLocation(fullPath)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(onDarkModeChanged: ((Boolean) -> Unit)? = null) {
    val viewModel: DashboardViewModel = remember { DashboardViewModel() }

    val state by viewModel.uiState.collectAsState()
    val pathResolver = remember { PathResolver() }
    // The download whose details are open (side panel on wide screens, a sheet / page on
    // tablets and phones). Gone from the list (cancelled, removed) = closed.
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    // Wide layout: sidebar filter and the search box in the top bar.
    var sideFilter by remember { mutableStateOf(SideFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedTask = state.allTasks.firstOrNull { it.id == selectedTaskId }
    var wideLayout by remember { mutableStateOf(false) }
    // The desktop sidebar filters on its own over "All": coming from a tablet/phone tab
    // (e.g. Completed) switch the list back to All so it matches the sidebar.
    LaunchedEffect(wideLayout) {
        val tab = viewModel.uiState.value.selectedTab
        if (wideLayout && tab != DashboardTab.ALL && tab != DashboardTab.SETTINGS) {
            viewModel.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.ALL))
        }
    }

    LaunchedEffect(state.isDarkMode) {
        onDarkModeChanged?.invoke(state.isDarkMode)
    }

    FetchJetTheme(isDarkMode = state.isDarkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val screenWidth = maxWidth
                val isCompact = screenWidth < 680.dp
                // tablet: icon rail; wide: full sidebar. The details side panel needs the
                // extra room, so below 1280dp it opens as a sheet over the list instead.
                val isMedium = screenWidth in 680.dp..<1100.dp
                val sidePanel = screenWidth >= 1280.dp
                SideEffect { wideLayout = !isCompact }

                if (isCompact) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FjPhoneHeader(
                            tasks = state.allTasks,
                            showListActions = state.selectedTab != DashboardTab.SETTINGS,
                            totalDlSpeed = state.totalDownloadSpeedBytesPerSec,
                            totalUlSpeed = state.totalUploadSpeedBytesPerSec,
                            speedDisplayUnit = state.speedDisplayUnit,
                            sortOption = state.sortOption,
                            isDarkMode = state.isDarkMode,
                            onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnPauseAllClicked) },
                            onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnResumeAllClicked) },
                            onSort = { viewModel.onEvent(DashboardUiEvent.OnSortChanged(it)) },
                            onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeToggled) },
                            title = if (state.selectedTab == DashboardTab.SETTINGS) "Settings" else "Downloads"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (state.selectedTab == DashboardTab.SETTINGS) {
                                SettingsContentView(
                                    state = state,
                                    viewModel = viewModel,
                                    pathResolver = pathResolver
                                )
                            } else {
                                TaskListArea(
                                    tasks = state.filteredTasks,
                                    viewModel = viewModel,
                                    speedDisplayUnit = state.speedDisplayUnit,
                                    selectedTab = state.selectedTab,
                                    sortOption = state.sortOption,
                                    selectedTaskId = selectedTaskId,
                                    onSelect = { selectedTaskId = it },
                                    showToolbar = false
                                )
                                // Floating "add" button (the header has no room for it).
                                androidx.compose.material3.FloatingActionButton(
                                    onClick = {
                                        viewModel.onEvent(
                                            DashboardUiEvent.OnToggleAddDialog(
                                                true
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                        .padding(end = 10.dp, bottom = 14.dp)
                                ) { Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                            }
                        }

                        FdmBottomNavigation(
                            selectedTab = state.selectedTab,
                            allCount = state.allTasks.size,
                            activeCount = state.allTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING },
                            completedCount = state.allTasks.count { it.status == DownloadStatus.COMPLETED },
                            torrentCount = state.allTasks.count { it.engineType == EngineType.BITTORRENT },
                            mediaCount = state.allTasks.count { it.engineType == EngineType.MEDIA_STREAM },
                            onTabSelected = { tab ->
                                viewModel.onEvent(DashboardUiEvent.OnTabSelected(tab))
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isMedium) {
                            FjRail(
                                tasks = state.allTasks,
                                current = sideFilter,
                                settingsSelected = state.selectedTab == DashboardTab.SETTINGS,
                                onSelect = { f ->
                                    sideFilter = f
                                    viewModel.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.ALL))
                                },
                                onSettings = {
                                    viewModel.onEvent(
                                        DashboardUiEvent.OnTabSelected(
                                            DashboardTab.SETTINGS
                                        )
                                    )
                                }
                            )
                        } else {
                            FjSidebar(
                                tasks = state.allTasks,
                                current = sideFilter,
                                settingsSelected = state.selectedTab == DashboardTab.SETTINGS,
                                onSelect = { f ->
                                    sideFilter = f
                                    // the sidebar filters by itself: the list behind it is "All"
                                    viewModel.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.ALL))
                                },
                                onSettings = {
                                    viewModel.onEvent(
                                        DashboardUiEvent.OnTabSelected(
                                            DashboardTab.SETTINGS
                                        )
                                    )
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .displayCutoutPadding()
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 1400.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                FjTopBar(
                                    showSearch = state.selectedTab != DashboardTab.SETTINGS,
                                    compact = isMedium,
                                    title = if (state.selectedTab == DashboardTab.SETTINGS) "Settings" else sideFilter.label,
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    totalDlSpeed = state.totalDownloadSpeedBytesPerSec,
                                    totalUlSpeed = state.totalUploadSpeedBytesPerSec,
                                    speedDisplayUnit = state.speedDisplayUnit,
                                    isDarkMode = state.isDarkMode,
                                    onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeToggled) },
                                    onAdd = {
                                        viewModel.onEvent(
                                            DashboardUiEvent.OnToggleAddDialog(
                                                true
                                            )
                                        )
                                    }
                                )

                                if (state.selectedTab == DashboardTab.SETTINGS) {
                                    SettingsContentView(
                                        state = state,
                                        viewModel = viewModel,
                                        pathResolver = pathResolver
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        TaskListArea(
                                            tasks = state.filteredTasks.filter {
                                                sideFilter.matches(
                                                    it
                                                )
                                            },
                                            viewModel = viewModel,
                                            speedDisplayUnit = state.speedDisplayUnit,
                                            selectedTab = state.selectedTab,
                                            // state groups only when the view mixes states
                                            groupByState = sideFilter in setOf(
                                                SideFilter.ALL,
                                                SideFilter.MEDIA,
                                                SideFilter.FILES,
                                                SideFilter.TORRENTS
                                            ),
                                            sortOption = state.sortOption,
                                            selectedTaskId = selectedTaskId,
                                            onSelect = { selectedTaskId = it },
                                            externalSearch = searchQuery
                                        )
                                    }
                                    QueueStatusBar(
                                        tasks = state.allTasks,
                                        globalLimitBytes = state.globalDownloadSpeedLimitBytes,
                                        speedDisplayUnit = state.speedDisplayUnit
                                    )
                                }
                            }
                        }

                        // Wide screens: the selected download's details stay open on the right.
                        if (sidePanel && selectedTask != null && state.selectedTab != DashboardTab.SETTINGS) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(340.dp)
                                    .fillMaxHeight()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = 0.4f
                                                )
                                            )
                                    )
                                    TaskDetailsPane(
                                        task = selectedTask,
                                        viewModel = viewModel,
                                        speedDisplayUnit = state.speedDisplayUnit,
                                        fullScreen = false,
                                        onClose = { selectedTaskId = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tablets and phones: details open over the list (a sheet / a full page).
            if (selectedTask != null && state.selectedTab != DashboardTab.SETTINGS) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val phone = maxWidth < 680.dp
                    val tablet = maxWidth < 1280.dp
                    if (phone || tablet) {
                        Dialog(
                            onDismissRequest = { selectedTaskId = null },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = if (phone) Alignment.TopCenter else Alignment.BottomCenter
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = if (phone) RoundedCornerShape(0.dp) else RoundedCornerShape(
                                        topStart = 22.dp,
                                        topEnd = 22.dp
                                    ),
                                    modifier = if (phone) Modifier.fillMaxSize()
                                    else Modifier.fillMaxWidth().widthIn(max = 820.dp)
                                        .heightIn(max = 620.dp)
                                ) {
                                    Column {
                                        if (!phone) {
                                            // sheet handle
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterHorizontally)
                                                    .padding(top = 10.dp)
                                                    .size(width = 44.dp, height = 5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.18f
                                                        )
                                                    )
                                            )
                                        }
                                        TaskDetailsPane(
                                            task = selectedTask,
                                            viewModel = viewModel,
                                            speedDisplayUnit = state.speedDisplayUnit,
                                            fullScreen = phone,
                                            onClose = { selectedTaskId = null }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Modal Add Download Dialog
            if (state.isAddDialogVisible) {
                AddDownloadModalDialog(
                    inputUrl = state.inputUrl,
                    destinationPath = state.destinationPath,
                    disableTorrentUpload = state.disableTorrentUpload,
                    detectedEngine = state.detectedEngine,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    pathResolver = pathResolver,
                    isInspectingTorrent = state.isInspectingTorrent,
                    inspectedTorrentName = state.inspectedTorrentName,
                    inspectedTorrentFiles = state.inspectedTorrentFiles,
                    inspectError = state.inspectError,
                    isInspectingMedia = state.isInspectingMedia,
                    inspectedMediaQualities = state.inspectedMediaQualities,
                    inspectMediaError = state.inspectMediaError,
                    isYtDlpInstalled = state.isYtDlpInstalled,
                    ytDlpStatus = state.ytDlpStatus,
                    isDownloadingYtDlp = state.isDownloadingYtDlp,
                    onDownloadYtDlp = { viewModel.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked) },
                    onUrlChanged = { viewModel.onEvent(DashboardUiEvent.OnUrlChanged(it)) },
                    suggestedFileName = state.suggestedFileName,
                    fileNameOverride = state.fileNameOverride,
                    onFileNameChanged = { viewModel.onEvent(DashboardUiEvent.OnFileNameChanged(it)) },
                    onDestinationPathChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnDestinationPathChanged(
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
                    onInspectTorrent = { viewModel.onEvent(DashboardUiEvent.OnInspectTorrentClicked) },
                    onInspectMedia = { viewModel.onEvent(DashboardUiEvent.OnInspectMediaClicked) },
                    onFileSelectionToggled = { index, selected ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnTorrentFileSelectionToggled(
                                index,
                                selected
                            )
                        )
                    },
                    onFilePriorityChanged = { index, priority ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnTorrentFilePriorityChanged(
                                index,
                                priority
                            )
                        )
                    },
                    onSelectAllFilesToggled = { selectAll ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnSelectAllTorrentFilesToggled(
                                selectAll
                            )
                        )
                    },
                    onMediaQualitySelectionToggled = { index, selected ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnMediaQualitySelectionToggled(
                                index,
                                selected
                            )
                        )
                    },
                    onSelectAllMediaQualitiesToggled = { selectAll ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnSelectAllMediaQualitiesToggled(
                                selectAll
                            )
                        )
                    },
                    clipStartTime = state.clipStartTime,
                    clipEndTime = state.clipEndTime,
                    isClipRangeEnabled = state.isClipRangeEnabled,
                    mediaDurationSeconds = state.mediaDurationSeconds,
                    onClipStartTimeChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnClipStartTimeChanged(
                                it
                            )
                        )
                    },
                    onClipEndTimeChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnClipEndTimeChanged(
                                it
                            )
                        )
                    },
                    onToggleClipRange = { viewModel.onEvent(DashboardUiEvent.OnToggleClipRange(it)) },
                    clipFastMode = state.clipFastMode,
                    onToggleClipFastMode = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnToggleClipFastMode(
                                it
                            )
                        )
                    },
                    inspectedPlaylist = state.inspectedPlaylist,
                    playlistQuality = state.playlistQuality,
                    onPlaylistEntryToggled = { i, sel ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnPlaylistEntryToggled(
                                i,
                                sel
                            )
                        )
                    },
                    onPlaylistSelectAll = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnPlaylistSelectAll(
                                it
                            )
                        )
                    },
                    onPlaylistQualityChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnPlaylistQualityChanged(
                                it
                            )
                        )
                    },
                    onPlaylistUseSingleVideo = { viewModel.onEvent(DashboardUiEvent.OnPlaylistUseSingleVideo) },
                    scheduleStartMs = state.scheduleStartMs,
                    onScheduleChanged = { viewModel.onEvent(DashboardUiEvent.OnScheduleChanged(it)) },
                    onEngineSelected = { viewModel.onEvent(DashboardUiEvent.OnEngineSelected(it)) },
                    onAddDownload = { viewModel.onEvent(DashboardUiEvent.OnAddDownloadClicked) },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnToggleAddDialog(false)) }
                )
            }

            // Remove Confirmation Dialog
            if (state.removeConfirmationTaskId != null) {
                RemoveConfirmationDialog(
                    onConfirm = { deleteFile ->
                        viewModel.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(deleteFile))
                    },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnDismissRemoveDialog) }
                )
            }

            // New FetchJet version: automatic check on start + "what's new" dialog
            com.michelle.raouf.fetch_jet.ui.UpdateDialogHost()

            // "Download in browser instead?" (only for plain file links sent by the extension)
            state.browserFallbackUrl?.let { url ->
                BrowserFallbackDialog(
                    url = url,
                    onConfirm = { viewModel.onEvent(DashboardUiEvent.OnConfirmBrowserFallback) },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnDismissBrowserFallback) }
                )
            }

            // Duplicate Download Alert Dialog
            state.duplicateTaskAlert?.let { duplicateTask ->
                DuplicateDownloadDialog(
                    task = duplicateTask,
                    onResumeExisting = { viewModel.onEvent(DashboardUiEvent.OnResumeExistingDuplicateClicked) },
                    onRestart = { viewModel.onEvent(DashboardUiEvent.OnRestartDuplicateClicked) },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnDismissDuplicateDialog) }
                )
            }

            // File Overwrite Conflict Alert Dialog
            state.fileConflictAlert?.let { conflictData ->
                FileOverwriteDialog(
                    data = conflictData,
                    onOverwrite = { viewModel.onEvent(DashboardUiEvent.OnConfirmOverwriteClicked) },
                    onSaveAsCopy = { newName ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnSaveAsCopyClicked(
                                newName
                            )
                        )
                    },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnDismissFileConflictDialog) }
                )
            }
        }
    }
}

@Composable
private fun SettingsContentView(
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
        ytDlpStatus = state.ytDlpStatus,
        isYtDlpInstalled = state.isYtDlpInstalled,
        isFfmpegInstalled = state.isFfmpegInstalled,
        toolsDirectoryPath = state.toolsDirectoryPath,
        torrentEngineStatus = state.torrentEngineStatus,
        isTorrentEngineReady = state.isTorrentEngineReady,
        isDownloadingYtDlp = state.isDownloadingYtDlp,
        pathResolver = pathResolver,
        onDestinationPathChanged = { viewModel.onEvent(DashboardUiEvent.OnDestinationPathChanged(it)) },
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
        onDownloadYtDlpClicked = { viewModel.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked) },
        onApplySettings = { viewModel.onEvent(DashboardUiEvent.OnApplySettingsClicked) }
    )
}

@Composable
private fun FdmNavigationRail(
    selectedTab: DashboardTab,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    torrentCount: Int,
    mediaCount: Int,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_fetchjet_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(20.dp))

            val items = listOfNotNull(
                Triple(DashboardTab.ALL, "All", "📥" to allCount),
                Triple(DashboardTab.ACTIVE, "Active", "⚡" to activeCount),
                Triple(DashboardTab.COMPLETED, "Done", "✅" to completedCount),
                if (AppFeatures.torrent) Triple(
                    DashboardTab.TORRENT,
                    "P2P",
                    "🧲" to torrentCount
                ) else null,
                if (AppFeatures.mediaExtractor) Triple(
                    DashboardTab.MEDIA,
                    "Media",
                    "🎬" to mediaCount
                ) else null
            )

            items.forEach { (tab, label, iconBadge) ->
                val isSelected = selectedTab == tab
                val (icon, badgeCount) = iconBadge
                RailNavItem(
                    icon = icon,
                    label = label,
                    badgeCount = badgeCount,
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            RailNavItem(
                icon = "⚙️",
                label = "Settings",
                badgeCount = null,
                isSelected = selectedTab == DashboardTab.SETTINGS,
                onClick = { onTabSelected(DashboardTab.SETTINGS) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RailNavItem(
    icon: String,
    label: String,
    badgeCount: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (badgeCount != null && badgeCount > 0) {
            BadgedBox(badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("$badgeCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }) {
                Text(icon, fontSize = 20.sp)
            }
        } else {
            Text(icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun FdmNavigationSidebar(
    selectedTab: DashboardTab,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    torrentCount: Int,
    mediaCount: Int,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // App Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fetchjet_logo),
                    contentDescription = "FetchJet Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FetchJet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Download Accelerator",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Items
            SidebarNavItem("📥 All Downloads", allCount, selectedTab == DashboardTab.ALL) {
                onTabSelected(DashboardTab.ALL)
            }

            SidebarNavItem("⚡ Active Tasks", activeCount, selectedTab == DashboardTab.ACTIVE) {
                onTabSelected(DashboardTab.ACTIVE)
            }

            SidebarNavItem("✅ Completed", completedCount, selectedTab == DashboardTab.COMPLETED) {
                onTabSelected(DashboardTab.COMPLETED)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (AppFeatures.torrent) {
                SidebarNavItem(
                    "🧲 BitTorrent P2P",
                    torrentCount,
                    selectedTab == DashboardTab.TORRENT
                ) {
                    onTabSelected(DashboardTab.TORRENT)
                }
            }

            if (AppFeatures.mediaExtractor) {
                SidebarNavItem("🎬 Media Extractor", mediaCount, selectedTab == DashboardTab.MEDIA) {
                    onTabSelected(DashboardTab.MEDIA)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SidebarNavItem("⚙️ Settings & Paths", null, selectedTab == DashboardTab.SETTINGS) {
                onTabSelected(DashboardTab.SETTINGS)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    badgeCount: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )

        if (badgeCount != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$badgeCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FdmTopHeader(
    selectedTab: DashboardTab = DashboardTab.ALL,
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String = "MB/s",
    isDarkMode: Boolean,
    onThemeToggled: () -> Unit,
    onNewDownloadClicked: () -> Unit
) {
    val pageTitle = when (selectedTab) {
        DashboardTab.ALL -> "Dashboard"
        DashboardTab.ACTIVE -> "Active Tasks"
        DashboardTab.COMPLETED -> "Completed"
        DashboardTab.TORRENT -> "BitTorrent P2P"
        DashboardTab.MEDIA -> "Media Extractor"
        DashboardTab.SETTINGS -> "Settings & Paths"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pageTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live Speed Gauge Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬇️ ", fontSize = 12.sp)
                        Text(
                            text = formatSpeed(totalDlSpeed, speedDisplayUnit),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬆️ ", fontSize = 12.sp)
                        Text(
                            text = formatSpeed(totalUlSpeed, speedDisplayUnit),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Theme Switcher Button
            IconButton(
                onClick = onThemeToggled,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(if (isDarkMode) "☀️" else "🌙", fontSize = 18.sp)
            }

            // Primary Add Download Button
            Button(
                onClick = onNewDownloadClicked,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("+ New Download", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    speedDisplayUnit: String = "MB/s",
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit
) {
    val rawDisplayName =
        task.fileName.ifBlank { task.url.substringBefore('?').substringAfterLast('/') }
    val displayName = decodeUnicodeText(rawDisplayName).trim()
    val rawFullPath = if (task.fileName.isNotBlank()) {
        val dest = task.destinationPath.trimEnd('/', '\\')
        val sep = if (dest.contains('\\')) "\\" else "/"
        "$dest$sep${task.fileName}"
    } else {
        task.destinationPath
    }
    val fullPath = decodeUnicodeText(rawFullPath).trim()
    val pathResolver = remember { PathResolver() }
    var showFiles by remember { mutableStateOf(false) }
    var confirmRestartPause by remember { mutableStateOf(false) }
    // Pausing a download that cannot resume throws its data away: ask first.
    val pauseClicked: () -> Unit = {
        if (task.canResume() == false) confirmRestartPause = true else onPause()
    }
    if (confirmRestartPause) {
        RestartOnPauseDialog(
            isClip = task.engineType == EngineType.MEDIA_STREAM,
            onConfirm = { confirmRestartPause = false; onPause() },
            onDismiss = { confirmRestartPause = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // Shown inside the details panel / sheet: no card of its own.
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            val isCompleted = task.status == DownloadStatus.COMPLETED
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Small soft chips: type · resume support · clip range
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (task.engineType) {
                    EngineType.MEDIA_STREAM -> DetailChip("Media", EngineMediaStream)
                    EngineType.BITTORRENT -> DetailChip("Torrent", EngineBitTorrent)
                    EngineType.HTTP_DIRECT -> DetailChip("File", MaterialTheme.colorScheme.primary)
                }
                if (!isCompleted) {
                    when (task.canResume()) {
                        true -> DetailChip("Resumable", StatusCompleted)
                        false -> DetailChip("No resume", StatusPaused)
                        null -> {}
                    }
                }
                if (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()) {
                    // Fast clip mode downloads the whole video first, then cuts it: say so,
                    // otherwise the full video's size on a clip looks like a bug.
                    DetailChip(
                        "Clip ${task.clipStart ?: "00:00"}–${task.clipEnd ?: "End"}" +
                                if (task.clipByCutting && !isCompleted) " · cut after download" else "",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Torrent Files Accordion if torrent
            if (task.torrentFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFiles = !showFiles }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 Torrent Files (${task.torrentFiles.count { it.isSelected }} selected / ${task.torrentFiles.size}) ${if (showFiles) "▲" else "▼"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (showFiles) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(task.torrentFiles, key = { it.index }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = file.isSelected,
                                        onCheckedChange = { checked ->
                                            onFileSelectionToggled(file.index, checked)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${if (file.isCompleted) "✅" else "⚪"} ${file.path}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                        color = if (file.isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.5f
                                        )
                                    )
                                    val (priorityLabel, priorityColor) = when (file.priority) {
                                        1 -> "High" to MaterialTheme.colorScheme.primary
                                        -1 -> "Low" to MaterialTheme.colorScheme.error
                                        else -> "Normal" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val next = when (file.priority) {
                                                0 -> 1
                                                1 -> -1
                                                else -> 0
                                            }
                                            onFilePriorityChanged(file.index, next)
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = priorityColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = priorityLabel,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = priorityColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val fileProgressText = if (file.isCompleted) {
                                        formatBytes(file.size)
                                    } else if (file.downloadedBytes > 0) {
                                        "${formatBytes(file.downloadedBytes)} / ${formatBytes(file.size)}"
                                    } else {
                                        formatBytes(file.size)
                                    }
                                    Text(
                                        text = fileProgressText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary card: big percentage, bar, then the numbers in a grid.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = when (task.status) {
                                DownloadStatus.COMPLETED -> "Completed"
                                DownloadStatus.PAUSED -> "Paused"
                                DownloadStatus.ERROR -> "Failed"
                                else -> if (task.waitingInQueue) "Waiting" else ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
            if (!task.currentStepText.isNullOrBlank() && (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING)) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "⚡ ${task.currentStepText}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            // Second line: what happens after the download (merging video + audio, MP3
            // conversion, saving to the folder). On a long video this takes minutes; without it
            // the task sat at 100 % looking frozen.
            val postLabel = task.postProcessLabel
            if (postLabel != null && (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING)) {
                Spacer(modifier = Modifier.height(6.dp))
                PostProcessLine(
                    label = postLabel,
                    progress = task.postProcessProgress,
                    startedAtMs = task.postProcessStartedAtMs
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Task Progress & Speed Details
                    Column(modifier = Modifier.fillMaxWidth()) {
                // "~" while the total is still an estimate (video downloading, audio size not known yet)
                val totalIsEstimate = task.status == DownloadStatus.DOWNLOADING &&
                        task.approximateTotalBytes > 0L && task.approximateTotalBytes == task.totalBytes
                // A clip downloaded as a section has no known size until it ends (only a guess):
                // like other download managers, show only what has been downloaded; the step line
                // already shows the clip time (09:49 / 10:00).
                val sectionClipRunning =
                    task.status == DownloadStatus.DOWNLOADING && !task.clipByCutting &&
                            (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank())
                        val bytesText = if (sectionClipRunning) formatBytes(task.downloadedBytes)
                    else "${formatBytes(task.downloadedBytes)} / ${
                        if (task.totalBytes > 0) (if (totalIsEstimate) "~" else "") + formatBytes(
                            task.totalBytes
                        ) else "Unknown"
                        }"


                val speedText = if (task.status == DownloadStatus.DOWNLOADING) {
                    if (task.postProcessLabel != null) {
                        "${task.postProcessLabel}..."
                    } else if (task.currentStepText?.contains(
                            "Merging",
                            ignoreCase = true
                        ) == true
                    ) {
                        "Merging Audio & Video..."
                    } else if (task.engineType == EngineType.BITTORRENT && task.disableUpload) {
                        "DL: ${
                            formatSpeed(
                                task.downloadSpeedBytesPerSec,
                                speedDisplayUnit
                            )
                        } (UL Disabled)"
                    } else if (task.engineType == EngineType.BITTORRENT) {
                        "DL: ${
                            formatSpeed(
                                task.downloadSpeedBytesPerSec,
                                speedDisplayUnit
                            )
                        } | UL: ${formatSpeed(task.uploadSpeedBytesPerSec, speedDisplayUnit)}"
                    } else {
                        formatSpeed(task.downloadSpeedBytesPerSec, speedDisplayUnit)
                    }
                } else if (task.status == DownloadStatus.CONNECTING) {
                    "Connecting..."
                } else if (task.waitingInQueue) {
                    if (task.scheduledAtMs > com.michelle.raouf.fetch_jet.core.util.currentEpochMs())
                        "🕑 Starts ${com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule(task.scheduledAtMs)}"
                    else if (task.queuePosition > 0) "⏳ Waiting in queue (#${task.queuePosition})"
                    else "⏳ Waiting in queue"
                } else {
                    when (task.status) {
                        DownloadStatus.COMPLETED -> "Completed"
                        DownloadStatus.PAUSED -> "Paused"
                        DownloadStatus.ERROR -> "Failed"
                        DownloadStatus.QUEUED -> "Queued"
                        else -> "Not started"
                    }
                }

                // Time left at the current speed, next to the speed: "2.4 Mbps · 3m 20s left"
                val eta = task.etaSeconds()
                        val speedColor = when (task.status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        DownloadStatus.COMPLETED -> StatusCompleted
                        DownloadStatus.PAUSED -> StatusPaused
                        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailStat(
                                "Downloaded",
                                bytesText,
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                            DetailStat(
                                if (task.status == DownloadStatus.DOWNLOADING) "Speed" else "State",
                                speedText,
                                speedColor,
                                Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailStat(
                                "Time left",
                                if (eta != null) formatEta(eta) else "—",
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                            DetailStat(
                                "Added",
                                formatAddedTime(task.createdAtEpochMs),
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (task.status == DownloadStatus.ERROR && !task.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ ${task.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Saved to",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = fullPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Actions: two equal buttons, then the red one across the full width.
            val running =
                task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING
            val canFolder = pathResolver.isOpenFolderSupported()
            val openFolder = { pathResolver.openFileLocation(fullPath); Unit }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier.weight(1f).height(42.dp)
                when {
                    isCompleted -> {
                        DetailPrimaryButton("Open file", m) { openDownloadedFile(fullPath) }
                        if (canFolder) DetailSecondaryButton("Open folder", m, openFolder)
                    }

                    running -> {
                        DetailPrimaryButton("Pause", m, pauseClicked)
                        if (canFolder) DetailSecondaryButton("Open folder", m, openFolder)
                    }

                    task.waitingInQueue -> {
                        DetailPrimaryButton("Start now", m, onResume)
                        DetailSecondaryButton("Pause", m, onPause)
                    }

                    else -> {
                        DetailPrimaryButton(
                            if (task.status == DownloadStatus.ERROR) "Retry" else "Resume",
                            m,
                            onResume
                        )
                        if (canFolder) DetailSecondaryButton("Open folder", m, openFolder)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(
                    if (isCompleted) "Remove from list" else "Cancel download",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Small soft label in the details: tinted text on a faint tint, no border. */
@Composable
private fun DetailChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DetailPrimaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun DetailSecondaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun EngineBadge(engineType: EngineType) {
    val bg = when (engineType) {
        EngineType.HTTP_DIRECT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        EngineType.BITTORRENT -> EngineBitTorrent.copy(alpha = 0.15f)
        EngineType.MEDIA_STREAM -> EngineMediaStream.copy(alpha = 0.15f)
    }
    val border = when (engineType) {
        EngineType.HTTP_DIRECT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        EngineType.BITTORRENT -> EngineBitTorrent.copy(alpha = 0.4f)
        EngineType.MEDIA_STREAM -> EngineMediaStream.copy(alpha = 0.4f)
    }
    val text = when (engineType) {
        EngineType.HTTP_DIRECT -> MaterialTheme.colorScheme.primary
        EngineType.BITTORRENT -> EngineBitTorrent
        EngineType.MEDIA_STREAM -> EngineMediaStream
    }
    val label = when (engineType) {
        EngineType.HTTP_DIRECT -> "⚡ HTTP Direct"
        EngineType.BITTORRENT -> "🧲 BitTorrent P2P"
        EngineType.MEDIA_STREAM -> "🎬 Media Stream"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TaskListArea(
    tasks: List<DownloadTask>,
    viewModel: DashboardViewModel,
    speedDisplayUnit: String = "MB/s",
    selectedTab: DashboardTab = DashboardTab.ALL,
    sortOption: TaskSortOption = TaskSortOption.DATE_DESC,
    selectedTaskId: String? = null,
    onSelect: (String?) -> Unit = {},
    /** Search typed in the top bar (wide layouts); null = this list shows its own search box. */
    externalSearch: String? = null,
    /** Downloading / Waiting / Paused & failed / Completed headers. */
    groupByState: Boolean = selectedTab == DashboardTab.ALL,
    /** Phone: Pause all / Resume all / Sort live in the header's ⋮ menu, not above the list. */
    showToolbar: Boolean = true
) {
    // The ViewModel already orders the list (running first, then waiting, paused, failed).
    // Mixed views show one header per state; tap a header to fold / unfold it.
    val grouped = groupByState
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    // Search by file name (or link) in the current view.
    var localSearch by remember { mutableStateOf("") }
    val searchQuery = externalSearch ?: localSearch
    val allTasks = tasks
    val shownTasks = remember(allTasks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allTasks
        else allTasks.filter {
            it.fileName.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) ||
                    (it.originalUrl?.contains(q, ignoreCase = true) == true)
        }
    }
    val unfinished =
        remember(shownTasks) { shownTasks.filter { it.status != DownloadStatus.COMPLETED } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allTasks.isNotEmpty() && externalSearch == null) {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { localSearch = it },
                placeholder = { Text("🔍 Search downloads by name…", fontSize = 13.sp) },
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { localSearch = "" }) { Text("✕", fontSize = 14.sp) } }
                } else null,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
        val tasks = shownTasks
        if (tasks.isNotEmpty() && showToolbar) {
            ListToolbar(
                tasks = unfinished,
                sortOption = sortOption,
                onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnPauseAllClicked) },
                onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnResumeAllClicked) },
                onSort = { viewModel.onEvent(DashboardUiEvent.OnSortChanged(it)) }
            )
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No downloads match \"${searchQuery.trim()}\"" else "No tasks in this view",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try another name" else "Add a download to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Column
        }

        TaskRowsHeader()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Phone: room so the floating add button never hides the last row.
            contentPadding = PaddingValues(top = 4.dp, bottom = if (showToolbar) 16.dp else 88.dp)
        ) {
            val taskItem: @Composable (DownloadTask) -> Unit = { task ->
                TaskRow(
                    task = task,
                    selected = task.id == selectedTaskId,
                    speedDisplayUnit = speedDisplayUnit,
                    onClick = { onSelect(task.id) },
                    onQuickAction = { action ->
                        when (action) {
                            // A download that cannot resume warns before pausing: in its details.
                            RowAction.PAUSE -> if (task.canResume() == false) onSelect(task.id)
                            else viewModel.onEvent(DashboardUiEvent.OnPauseClicked(task.id))

                            RowAction.RESUME, RowAction.START_NOW ->
                                viewModel.onEvent(DashboardUiEvent.OnResumeClicked(task.id))

                            RowAction.OPEN -> {
                                val dest = task.destinationPath.trimEnd('/', '\\')
                                val sep = if (dest.contains('\\')) "\\" else "/"
                                openDownloadedFile(decodeUnicodeText("$dest$sep${task.fileName}").trim())
                            }

                            RowAction.NONE -> {}
                        }
                    }
                )
            }

            if (grouped) {
                taskStateGroups(tasks).forEach { group ->
                    val collapsed = collapsedGroups[group.key] == true
                    item(key = "header-" + group.key) {
                        SectionHeader(
                            emoji = group.emoji,
                            title = group.title,
                            count = group.tasks.size,
                            color = group.color(),
                            collapsed = collapsed,
                            onToggle = { collapsedGroups[group.key] = !collapsed }
                        )
                    }
                    if (!collapsed) items(group.tasks, key = { it.id }) { taskItem(it) }
                }
            } else {
                items(tasks, key = { it.id }) { taskItem(it) }
            }
        }
    }
}

/** "Resumable" / "No resume" chip; nothing while unknown (HTTP link not connected yet). */
@Composable
private fun ResumeBadge(canResume: Boolean?) {
    if (canResume == null) return
    val color = if (canResume) StatusCompleted else StatusPaused
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = if (canResume) "↻ Resumable" else "⚠ No resume",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 45s · 12m 05s · 1h 07m */
private fun formatEta(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60L -> "${s}s"
        s < 3600L -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        s < 100L * 3600L -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
        else -> "> 99h"
    }
}

@Composable
private fun RestartOnPauseDialog(isClip: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("This download can't be resumed", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (isClip) "Clips cut while downloading can't continue where they stopped. " +
                        "If you pause now, the clip will start again from the beginning when you resume."
                else "The server doesn't support resuming. If you pause now, the download will " +
                        "start again from the beginning when you resume.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text("Pause anyway")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Keep downloading")
            }
        }
    )
}

@Composable
private fun BrowserFallbackDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Download in browser instead?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "You cancelled this file in FetchJet. The browser stopped its own download when it " +
                            "handed the link over, so it will only continue if you send it back.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    url.substringAfterLast('/').substringBefore('?').ifBlank { url },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text("Download in browser")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("No, discard")
            }
        }
    )
}

@Composable
private fun SectionHeader(
    emoji: String,
    title: String,
    count: Int,
    color: Color,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggle != null) Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle() } else Modifier)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggle != null) {
            Text(
                text = if (collapsed) "▶" else "▼",
                fontSize = 11.sp,
                color = color,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Text(emoji, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}

/** Sort chips: Date (newest/oldest), Size (largest/smallest), Name. Tapping again flips. */
@Composable
private fun SortBar(current: TaskSortOption, onChange: (TaskSortOption) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sort",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )
        SortChip(
            label = "Date",
            arrow = when (current) {
                TaskSortOption.DATE_DESC -> " ↓"
                TaskSortOption.DATE_ASC -> " ↑"
                else -> ""
            },
            selected = current == TaskSortOption.DATE_DESC || current == TaskSortOption.DATE_ASC,
            onClick = {
                onChange(if (current == TaskSortOption.DATE_DESC) TaskSortOption.DATE_ASC else TaskSortOption.DATE_DESC)
            }
        )
        SortChip(
            label = "Size",
            arrow = when (current) {
                TaskSortOption.SIZE_DESC -> " ↓"
                TaskSortOption.SIZE_ASC -> " ↑"
                else -> ""
            },
            selected = current == TaskSortOption.SIZE_DESC || current == TaskSortOption.SIZE_ASC,
            onClick = {
                onChange(if (current == TaskSortOption.SIZE_DESC) TaskSortOption.SIZE_ASC else TaskSortOption.SIZE_DESC)
            }
        )
        SortChip(
            label = "Name",
            arrow = if (current == TaskSortOption.NAME_ASC) " ↓" else "",
            selected = current == TaskSortOption.NAME_ASC,
            onClick = { onChange(TaskSortOption.NAME_ASC) }
        )
    }
}

@Composable
private fun SortChip(label: String, arrow: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label + arrow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.height(30.dp)
    )
}

@Composable
private fun FdmMobileTopHeader(
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String = "MB/s",
    isDarkMode: Boolean,
    onThemeToggled: () -> Unit,
    onNewDownloadClicked: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.ic_fetchjet_logo),
                        contentDescription = "FetchJet Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FetchJet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onThemeToggled,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Text(if (isDarkMode) "☀️" else "🌙", fontSize = 16.sp)
                    }

                    Button(
                        onClick = onNewDownloadClicked,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("+ Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Live Traffic Sub-banner when transfers are active
            if (totalDlSpeed > 0L || totalUlSpeed > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Live Traffic",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⬇ ${formatSpeed(totalDlSpeed, speedDisplayUnit)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (totalUlSpeed > 0L) {
                                Text(
                                    text = "⬆ ${formatSpeed(totalUlSpeed, speedDisplayUnit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun FdmBottomNavigation(
    selectedTab: DashboardTab,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    torrentCount: Int,
    mediaCount: Int,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                val items = listOfNotNull(
                    Triple(DashboardTab.ALL, "All", "📥" to allCount),
                    Triple(DashboardTab.ACTIVE, "Active", "⚡" to activeCount),
                    Triple(DashboardTab.COMPLETED, "Done", "✅" to completedCount),
                    if (AppFeatures.torrent) Triple(
                        DashboardTab.TORRENT,
                        "Torrent",
                        "🧲" to torrentCount
                    ) else null,
                    if (AppFeatures.mediaExtractor) Triple(
                        DashboardTab.MEDIA,
                        "Media",
                        "🎬" to mediaCount
                    ) else null,
                    Triple(DashboardTab.SETTINGS, "Settings", "⚙️" to 0)
                )

                items.forEach { (tab, label, iconBadge) ->
                    val isSelected = selectedTab == tab
                    val (icon, badgeCount) = iconBadge

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        alwaysShowLabel = true,
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            "$badgeCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }) {
                                    Text(icon, fontSize = 18.sp)
                                }
                            } else {
                                Text(icon, fontSize = 18.sp)
                            }
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

/**
 * The post-processing progress line under a task's main bar: label, percentage (or a moving bar
 * when the length is unknown) and the time the step has been running.
 */
@Composable
@OptIn(kotlin.time.ExperimentalTime::class)
private fun PostProcessLine(label: String, progress: Float, startedAtMs: Long) {
    // Elapsed time comes from when the step really started (kept in the task), so it does not
    // restart at 0:00 when the card scrolls out of view and back.
    val fallbackStart = remember(label) { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    val start = if (startedAtMs > 0L) startedAtMs else fallbackStart
    var elapsedSec by remember(label, start) { mutableStateOf(0L) }
    LaunchedEffect(label, start) {
        while (true) {
            elapsedSec = ((kotlin.time.Clock.System.now()
                .toEpochMilliseconds() - start) / 1000L).coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }
    val m = elapsedSec / 60
    val sec = elapsedSec % 60
    val elapsedText =
        if (m >= 60) "${m / 60}:${(m % 60).toString().padStart(2, '0')}:${
            sec.toString().padStart(2, '0')
        }"
        else "$m:${sec.toString().padStart(2, '0')}"
    val icon = when {
        label.startsWith("Merging", ignoreCase = true) -> "🔗"
        label.startsWith("Saving", ignoreCase = true) -> "📁"
        label.contains("MP3", ignoreCase = true) -> "🎵"
        else -> "⚙️"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$icon $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = (if (progress >= 0f) "${(progress * 100).toInt()}% · " else "") + elapsedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        val barModifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = barModifier,
                color = MaterialTheme.colorScheme.tertiary,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/** All of one download: the full card (every detail and action) under a header with close. */
@Composable
private fun TaskDetailsPane(
    task: DownloadTask,
    viewModel: DashboardViewModel,
    speedDisplayUnit: String,
    fullScreen: Boolean,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullScreen) {
                IconButton(onClick = onClose) { Text("←", fontSize = 20.sp) }
            }
            Text(
                text = if (fullScreen) "Download" else "DETAILS",
                style = if (fullScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (fullScreen) 0.sp else 0.8.sp,
                color = if (fullScreen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = if (fullScreen) 0.dp else 8.dp)
            )
            if (!fullScreen) {
                IconButton(onClick = onClose) { Text("✕", fontSize = 15.sp) }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            DownloadTaskItem(
                task = task,
                speedDisplayUnit = speedDisplayUnit,
                onPause = { viewModel.onEvent(DashboardUiEvent.OnPauseClicked(task.id)) },
                onResume = { viewModel.onEvent(DashboardUiEvent.OnResumeClicked(task.id)) },
                onCancel = { viewModel.onEvent(DashboardUiEvent.OnCancelClicked(task.id)) },
                onFileSelectionToggled = { fileIndex, selected ->
                    viewModel.onEvent(
                        DashboardUiEvent.OnTaskTorrentFileSelectionToggled(
                            task.id,
                            fileIndex,
                            selected
                        )
                    )
                },
                onFilePriorityChanged = { fileIndex, priority ->
                    viewModel.onEvent(
                        DashboardUiEvent.OnTaskTorrentFilePriorityChanged(
                            task.id,
                            fileIndex,
                            priority
                        )
                    )
                }
            )
        }
    }
}

/** Bottom line of the wide layout: what runs, what waits, the queue size and the speed limit. */
@Composable
private fun QueueStatusBar(
    tasks: List<DownloadTask>,
    globalLimitBytes: Long,
    speedDisplayUnit: String
) {
    val maxAtOnce by com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository.getInstance()
        .maxConcurrentDownloads.collectAsState()
    val running = tasks.count {
        !it.waitingInQueue && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING)
    }
    val waiting = tasks.count { it.waitingInQueue }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text("$running downloading · $waiting waiting", style = style, color = color, maxLines = 1)
        Text(
            "Queue: " + if (maxAtOnce <= 0) "no limit" else "$maxAtOnce at a time",
            style = style,
            color = color,
            maxLines = 1
        )
        Text(
            "Speed limit: " + if (globalLimitBytes > 0) formatSpeed(
                globalLimitBytes,
                speedDisplayUnit
            ) else "off",
            style = style, color = color
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "v" + com.michelle.raouf.fetch_jet.core.update.APP_VERSION,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DetailStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** "Today 2:05 PM", "Yesterday 9:10 AM" or "21/10/2026". */
private fun formatAddedTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val offset = com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs()
    val dayMs = 24L * 60 * 60 * 1000
    val local = epochMs + offset
    val day = local.floorDiv(dayMs)
    val today = (com.michelle.raouf.fetch_jet.core.util.currentEpochMs() + offset).floorDiv(dayMs)
    val minutes = local.mod(dayMs) / 60_000L
    val h24 = (minutes / 60).toInt()
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    val clock = "$h12:${(minutes % 60).toString().padStart(2, '0')} ${if (h24 < 12) "AM" else "PM"}"
    return when (today - day) {
        0L -> "Today $clock"
        1L -> "Yesterday $clock"
        else -> {
            // days since 1970-01-01 -> civil date (Howard Hinnant's algorithm)
            val z = day + 719468
            val era = z.floorDiv(146097)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era * 400 + if (m <= 2) 1 else 0
            "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}/$y"
        }
    }
}

/** Wide-layout sidebar: brand, state filters with counts, type filters, Settings at the bottom. */
@Composable
private fun FjSidebar(
    tasks: List<DownloadTask>,
    current: SideFilter,
    settingsSelected: Boolean,
    onSelect: (SideFilter) -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.width(232.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, bottom = 18.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fetchjet_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "FetchJet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            listOf(
                SideFilter.ALL,
                SideFilter.DOWNLOADING,
                SideFilter.WAITING,
                SideFilter.PAUSED,
                SideFilter.COMPLETED,
                SideFilter.FAILED
            )
                .forEach { f ->
                    FjSidebarItem(
                        f.label,
                        tasks.count { f.matches(it) },
                        !settingsSelected && current == f
                    ) { onSelect(f) }
                }
            Box(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp).fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Text(
                "TYPE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
            )
            listOfNotNull(
                if (AppFeatures.mediaExtractor) SideFilter.MEDIA else null,
                SideFilter.FILES,
                if (AppFeatures.torrent) SideFilter.TORRENTS else null
            ).forEach { f ->
                FjSidebarItem(
                    f.label,
                    tasks.count { f.matches(it) },
                    !settingsSelected && current == f
                ) { onSelect(f) }
            }
            Spacer(modifier = Modifier.weight(1f))
            FjSidebarItem("Settings", null, settingsSelected, onSettings)
        }
    }
}

@Composable
private fun FjSidebarItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            ) {
                Text(
                    count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** Wide-layout top bar: search, live speeds, theme, Add download. */
@Composable
private fun FjTopBar(
    showSearch: Boolean,
    compact: Boolean = false,
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String,
    isDarkMode: Boolean,
    onThemeToggled: () -> Unit,
    onAdd: () -> Unit
) {
    val searchField: @Composable (Modifier) -> Unit = { m ->
        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    "Search downloads",
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false
                )
            },
            leadingIcon = {
                Text(
                    "⌕",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { onSearchChange("") }) { Text("✕", fontSize = 13.sp) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = m.heightIn(min = 44.dp)
        )
    }
    // Tablet: the title takes the bar and the search box sits under it, as on the phone.
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                IconButton(onClick = onThemeToggled) {
                    Text(
                        if (isDarkMode) "☀" else "☾",
                        fontSize = 18.sp
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(44.dp)
                ) { Text("+  Add", fontWeight = FontWeight.Bold, maxLines = 1) }
            }
            if (showSearch) {
                Spacer(modifier = Modifier.height(10.dp))
                searchField(Modifier.fillMaxWidth())
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showSearch) {
            searchField(Modifier.weight(1f))
        } else {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onThemeToggled) {
            Text(if (isDarkMode) "☀" else "☾", fontSize = 18.sp)
        }
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Text("+  Add download", fontWeight = FontWeight.Bold)
        }
    }
}


/** One header group in the list: Downloading / Waiting / Paused & failed / Completed. */
private class TaskStateGroup(
    val key: String,
    val emoji: String,
    val title: String,
    val tasks: List<DownloadTask>,
    val color: @Composable () -> Color
)

private fun taskStateGroups(tasks: List<DownloadTask>): List<TaskStateGroup> {
    val downloading = tasks.filter { SideFilter.DOWNLOADING.matches(it) }
    val waiting = tasks.filter { SideFilter.WAITING.matches(it) }
    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val used = (downloading + waiting + completed).map { it.id }.toSet()
    // Paused, failed and anything idle.
    val stopped = tasks.filter { it.id !in used }
    return listOf(
        TaskStateGroup(
            "run",
            "⬇",
            "Downloading",
            downloading
        ) { MaterialTheme.colorScheme.primary },
        TaskStateGroup(
            "wait",
            "⏳",
            "Waiting",
            waiting
        ) { MaterialTheme.colorScheme.onSurfaceVariant },
        TaskStateGroup("stop", "⏸", "Paused & failed", stopped) { StatusPaused },
        TaskStateGroup("done", "✓", "Completed", completed) { MaterialTheme.colorScheme.tertiary }
    ).filter { it.tasks.isNotEmpty() }
}

private fun anyPausable(tasks: List<DownloadTask>) = tasks.any {
    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING ||
            it.status == DownloadStatus.QUEUED
}

private fun anyResumable(tasks: List<DownloadTask>) =
    tasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }

/** Above the list: Pause all · Resume all ········ Sort: Newest first ▾ */
@Composable
private fun ListToolbar(
    tasks: List<DownloadTask>,
    sortOption: TaskSortOption,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSort: (TaskSortOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPauseAll,
            enabled = anyPausable(tasks),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) { Text("⏸  Pause all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        OutlinedButton(
            onClick = onResumeAll,
            enabled = anyResumable(tasks),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) { Text("▶  Resume all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.weight(1f))
        var open by remember { mutableStateOf(false) }
        Box {
            androidx.compose.material3.TextButton(
                onClick = { open = true },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    "Sort: ${sortOption.label}  ▾",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                TaskSortOption.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (opt == sortOption) "✓  " else "     ") + opt.label,
                                fontSize = 13.sp
                            )
                        },
                        onClick = { open = false; onSort(opt) }
                    )
                }
            }
        }
    }
}

/** Phone header: title, total speed and the ⋮ menu with the list-wide actions. */
@Composable
private fun FjPhoneHeader(
    tasks: List<DownloadTask>,
    showListActions: Boolean,
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String,
    sortOption: TaskSortOption,
    isDarkMode: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSort: (TaskSortOption) -> Unit,
    onThemeToggled: () -> Unit,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        var open by remember { mutableStateOf(false) }
        var sortOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Text(
                    "⋮",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false; sortOpen = false }) {
                if (showListActions) {
                    val unfinished = tasks.filter { it.status != DownloadStatus.COMPLETED }
                    DropdownMenuItem(
                        text = { Text("Pause all") },
                        enabled = anyPausable(unfinished),
                        onClick = { open = false; onPauseAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("Resume all") },
                        enabled = anyResumable(unfinished),
                        onClick = { open = false; onResumeAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sort: ${sortOption.label}  " + if (sortOpen) "▴" else "▾") },
                        onClick = { sortOpen = !sortOpen }
                    )
                    if (sortOpen) {
                        TaskSortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (opt == sortOption) "✓  " else "     ") + opt.label,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = { open = false; sortOpen = false; onSort(opt) }
                            )
                        }
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(if (isDarkMode) "Light theme" else "Dark theme") },
                    onClick = { open = false; onThemeToggled() }
                )
            }
        }
    }
}


/** Tablet navigation: an 80dp rail of state filters with counts, Settings at the bottom. */
@Composable
private fun FjRail(
    tasks: List<DownloadTask>,
    current: SideFilter,
    settingsSelected: Boolean,
    onSelect: (SideFilter) -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.width(84.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_fetchjet_logo),
                contentDescription = null,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            listOf(
                SideFilter.ALL to "All",
                SideFilter.DOWNLOADING to "Active",
                SideFilter.WAITING to "Waiting",
                SideFilter.COMPLETED to "Done",
                SideFilter.FAILED to "Failed"
            ).forEach { (f, label) ->
                FjRailItem(
                    label,
                    tasks.count { f.matches(it) },
                    !settingsSelected && current == f
                ) { onSelect(f) }
            }
            Spacer(modifier = Modifier.weight(1f))
            FjRailItem("Settings", null, settingsSelected, glyph = "⚙", onClick = onSettings)
        }
    }
}

@Composable
private fun FjRailItem(
    label: String,
    count: Int?,
    selected: Boolean,
    glyph: String? = null,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = if (selected && count != null) accent else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.07f
            )
        ) {
            Text(
                text = count?.toString() ?: (glyph ?: ""),
                fontSize = if (glyph != null) 15.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected && count != null) MaterialTheme.colorScheme.onPrimary
                else if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 26.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
