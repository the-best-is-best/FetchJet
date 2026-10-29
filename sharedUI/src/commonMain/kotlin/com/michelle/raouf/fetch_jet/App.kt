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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                val isMedium = screenWidth in 680.dp..1024.dp

                if (isCompact) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FdmMobileTopHeader(
                            totalDlSpeed = state.totalDownloadSpeedBytesPerSec,
                            totalUlSpeed = state.totalUploadSpeedBytesPerSec,
                            speedDisplayUnit = state.speedDisplayUnit,
                            isDarkMode = state.isDarkMode,
                            onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeToggled) },
                            onNewDownloadClicked = {
                                viewModel.onEvent(DashboardUiEvent.OnToggleAddDialog(true))
                            }
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
                                    sortOption = state.sortOption
                                )
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
                            FdmNavigationRail(
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
                        } else {
                            FdmNavigationSidebar(
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
                                FdmTopHeader(
                                    selectedTab = state.selectedTab,
                                    totalDlSpeed = state.totalDownloadSpeedBytesPerSec,
                                    totalUlSpeed = state.totalUploadSpeedBytesPerSec,
                                    speedDisplayUnit = state.speedDisplayUnit,
                                    isDarkMode = state.isDarkMode,
                                    onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeToggled) },
                                    onNewDownloadClicked = {
                                        viewModel.onEvent(DashboardUiEvent.OnToggleAddDialog(true))
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

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
                                        sortOption = state.sortOption
                                    )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Filename / Title: Clickable when completed to open file directly on JVM or Android
            val isCompleted = task.status == DownloadStatus.COMPLETED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCompleted) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { openDownloadedFile(fullPath) }
                                .padding(vertical = 2.dp)
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isCompleted) {
                    Text(
                        text = " ↗",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Badges row: Engine Type + Clip Range + Resume support (wraps on narrow screens)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EngineBadge(engineType = task.engineType)
                if (task.status != DownloadStatus.COMPLETED) {
                    ResumeBadge(task.canResume())
                }

                if (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            // Fast clip mode downloads the whole video first, then cuts it: say so,
                            // otherwise the full video's size on a clip card looks like a bug.
                            text = "✂️ ${task.clipStart ?: "00:00"} - ${task.clipEnd ?: "End"}" +
                                    if (task.clipByCutting && task.status != DownloadStatus.COMPLETED) " · cut after download" else "",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Save To: $fullPath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "~" while the total is still an estimate (video downloading, audio size not known yet)
                val totalIsEstimate = task.status == DownloadStatus.DOWNLOADING &&
                        task.approximateTotalBytes > 0L && task.approximateTotalBytes == task.totalBytes
                // A clip downloaded as a section has no known size until it ends (only a guess):
                // like other download managers, show only what has been downloaded; the step line
                // already shows the clip time (09:49 / 10:00).
                val sectionClipRunning =
                    task.status == DownloadStatus.DOWNLOADING && !task.clipByCutting &&
                            (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank())
                Text(
                    text = if (sectionClipRunning) formatBytes(task.downloadedBytes)
                    else "${formatBytes(task.downloadedBytes)} / ${
                        if (task.totalBytes > 0) (if (totalIsEstimate) "~" else "") + formatBytes(
                            task.totalBytes
                        ) else "Unknown"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )

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
                } else {
                    task.status.name
                }

                // Time left at the current speed, next to the speed: "2.4 Mbps · 3m 20s left"
                val eta = task.etaSeconds()
                Text(
                    text = if (eta != null) "$speedText · ${formatEta(eta)} left" else speedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (task.status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        DownloadStatus.COMPLETED -> StatusCompleted
                        DownloadStatus.PAUSED -> StatusPaused
                        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
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

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactActions = maxWidth < 520.dp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.status == DownloadStatus.COMPLETED) {
                    // Phone: one row - [Open File][Open Folder] stretched, Remove as a small
                    // trash button - instead of three pills wrapping onto two lines.
                    val buttonModifier =
                        if (compactActions) Modifier.weight(1f).height(36.dp) else Modifier.height(
                            36.dp
                        )
                    Button(
                        onClick = { openDownloadedFile(fullPath) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = buttonModifier
                    ) {
                        Text(
                            "▶ Open File",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (pathResolver.isOpenFolderSupported()) {
                        OutlinedButton(
                            onClick = { pathResolver.openFileLocation(fullPath) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = buttonModifier
                        ) {
                            Text(
                                if (compactActions) "📁 Folder" else "📁 Open Folder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    if (compactActions) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.size(width = 44.dp, height = 36.dp)
                        ) {
                            Text("🗑", fontSize = 14.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING) {
                    OutlinedButton(
                        onClick = pauseClicked,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.ERROR) {
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            }
        }
    }
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
    sortOption: TaskSortOption = TaskSortOption.DATE_DESC
) {
    // The ViewModel already orders the list (unfinished first, then the chosen sort).
    // In "All" we show the two groups with their own headers so a finished file never
    // sits between two running downloads.
    val grouped = selectedTab == DashboardTab.ALL
    val unfinished = remember(tasks) { tasks.filter { it.status != DownloadStatus.COMPLETED } }
    val finished = remember(tasks) { tasks.filter { it.status == DownloadStatus.COMPLETED } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tasks.isNotEmpty()) {
            SortBar(
                current = sortOption,
                onChange = { viewModel.onEvent(DashboardUiEvent.OnSortChanged(it)) }
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
                        text = "No tasks in this view",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Click '+ New Download' to start downloading files!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
        ) {
            val taskItem: @Composable (DownloadTask) -> Unit = { task ->
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

            if (grouped) {
                if (unfinished.isNotEmpty()) {
                    item(key = "header-active") {
                        SectionHeader(
                            emoji = "⬇️",
                            title = "In progress",
                            count = unfinished.size,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(unfinished, key = { it.id }) { taskItem(it) }
                }
                if (finished.isNotEmpty()) {
                    item(key = "header-done") {
                        SectionHeader(
                            emoji = "✅",
                            title = "Completed",
                            count = finished.size,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(finished, key = { it.id }) { taskItem(it) }
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
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
