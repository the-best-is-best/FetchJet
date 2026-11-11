package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEffect
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.theme.FetchJetTheme
import com.michelle.raouf.fetch_jet.ui.AddDownloadModalDialog
import com.michelle.raouf.fetch_jet.ui.DuplicateDownloadDialog
import com.michelle.raouf.fetch_jet.ui.FileOverwriteDialog
import com.michelle.raouf.fetch_jet.ui.RemoveConfirmationDialog
import com.michelle.raouf.fetch_jet.ui.SideFilter
import com.michelle.raouf.fetch_jet.ui.clearFocusOnTapOutside
import com.tbib.kmp_localization.LocalizationManager

/** The task whose file is gone from disk: shown in [MissingFileDialog]. */
internal val missingFileTaskId = androidx.compose.runtime.mutableStateOf<String?>(null)

/**
 * Opens a finished download with the system's default app. A file that is no longer on disk
 * shows [MissingFileDialog]; a file no app can open is revealed in its folder instead.
 */
internal fun openDownloadedFile(fullPath: String, taskId: String) {
    val resolver = PathResolver()
    if (!resolver.fileExists(fullPath)) {
        missingFileTaskId.value = taskId
        return
    }
    if (!resolver.openFile(fullPath)) {
        resolver.openFileLocation(fullPath)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(
    onDarkModeChanged: ((Boolean) -> Unit)? = null,
    /** The user's explicit choice: true / false, or null for System (the host must not force it). */
    onForcedDarkChanged: ((Boolean?) -> Unit)? = null
) {
    val viewModel: DashboardViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel { DashboardViewModel() }

    val state by viewModel.uiState.collectAsState()
    val pathResolver = remember { PathResolver() }

    // Clipboard watcher (Settings > General): a copied file / magnet link is offered for download.
    // Polled only while the app is on screen; the first read is skipped so what was already in
    // the clipboard when the app opened is not offered.
    val watchClipboard = state.monitorClipboard
    val clipboardVideoPages = state.clipboardVideoPages
    // At app start what is already in the clipboard is ignored; when the user turns the switch
    // on (or changes "video pages") the current clipboard is looked at right away.
    var clipboardFirstRun by remember { mutableStateOf(true) }
    LaunchedEffect(watchClipboard, clipboardVideoPages) {
        if (!watchClipboard) {
            clipboardFirstRun = false; return@LaunchedEffect
        }
        var last: String? = if (clipboardFirstRun) {
            try {
                getClipboardText()
            } catch (_: Throwable) {
                null
            }
        } else null
        clipboardFirstRun = false
        while (true) {
            kotlinx.coroutines.delay(800)
            val text = try {
                getClipboardText()
            } catch (_: Throwable) {
                null
            }
            if (text != null && text != last) {
                last = text
                com.michelle.raouf.fetch_jet.core.settings.ClipboardLinks.downloadableUrl(
                    text,
                    clipboardVideoPages
                )
                    ?.let { viewModel.onEvent(DashboardUiEvent.OnClipboardLink(it)) }
            }
        }
    }
    // The download whose details are open (side panel on wide screens, a sheet / page on
    // tablets and phones). Gone from the list (cancelled, removed) = closed.
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    // Wide layout: sidebar filter and the search box in the top bar.
    var sideFilter by remember { mutableStateOf(SideFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    // A copied link was accepted as an offer: come to the front so the user sees it.
    LaunchedEffect(state.clipboardOfferUrl) {
        if (state.clipboardOfferUrl != null) com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.showAndFocus()
    }
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

    // "system" follows the device's light / dark setting (and changes with it while open).
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkMode = when (state.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    LaunchedEffect(darkMode) {
        onDarkModeChanged?.invoke(darkMode)
    }
    LaunchedEffect(state.themeMode) {
        onForcedDarkChanged?.invoke(
            when (state.themeMode) {
                "light" -> false
                "dark" -> true
                else -> null
            }
        )
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is DashboardUiEffect.ShowToast -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    val language by com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.current.collectAsState()
    androidx.compose.runtime.key(language) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                    else androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
    FetchJetTheme(isDarkMode = darkMode) {
        Surface(
            modifier = Modifier.fillMaxSize().clearFocusOnTapOutside(),
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
                            isDarkMode = darkMode,
                            downloadLimitBytes = state.globalDownloadSpeedLimitBytes,
                            speedPresetLowBytes = state.speedPresetLowBytes,
                            speedPresetMediumBytes = state.speedPresetMediumBytes,
                            speedPresetFastBytes = state.speedPresetFastBytes,
                            onPickDownloadLimit = {
                                viewModel.onEvent(
                                    DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged(
                                        it
                                    )
                                )
                            },
                            onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnPauseAllClicked) },
                            onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnResumeAllClicked) },
                            onSort = { viewModel.onEvent(DashboardUiEvent.OnSortChanged(it)) },
                            onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeModeChanged(if (darkMode) "light" else "dark")) },
                            title = if (state.selectedTab == DashboardTab.SETTINGS) LocalizationManager.Strings.homeSettings else LocalizationManager.Strings.homeDownloads
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
                                    title = if (state.selectedTab == DashboardTab.SETTINGS) LocalizationManager.Strings.homeSettings else sideFilter.label,
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    totalDlSpeed = state.totalDownloadSpeedBytesPerSec,
                                    totalUlSpeed = state.totalUploadSpeedBytesPerSec,
                                    speedDisplayUnit = state.speedDisplayUnit,
                                    isDarkMode = darkMode,
                                    downloadLimitBytes = state.globalDownloadSpeedLimitBytes,
                                    speedPresetLowBytes = state.speedPresetLowBytes,
                                    speedPresetMediumBytes = state.speedPresetMediumBytes,
                                    speedPresetFastBytes = state.speedPresetFastBytes,
                                    onPickDownloadLimit = {
                                        viewModel.onEvent(
                                            DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged(
                                                it
                                            )
                                        )
                                    },
                                    onThemeToggled = { viewModel.onEvent(DashboardUiEvent.OnThemeModeChanged(if (darkMode) "light" else "dark")) },
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
                        // The sheet is as tall as its content; pull the handle up (or tap it) to
                        // expand, pull it down or tap outside to close.
                        var sheetExpanded by remember(selectedTaskId) { mutableStateOf(false) }
                        Dialog(
                            onDismissRequest = { selectedTaskId = null },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { selectedTaskId = null },
                                contentAlignment = if (phone) Alignment.TopCenter else Alignment.BottomCenter
                            ) {
                                val sheetModifier = when {
                                    phone -> Modifier.fillMaxSize()
                                    sheetExpanded -> Modifier.fillMaxWidth().widthIn(max = 820.dp)
                                        .fillMaxHeight(0.94f)

                                    else -> Modifier.fillMaxWidth().widthIn(max = 820.dp)
                                        .heightIn(max = this@BoxWithConstraints.maxHeight * 0.9f)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = if (phone) RoundedCornerShape(0.dp) else RoundedCornerShape(
                                        topStart = 22.dp,
                                        topEnd = 22.dp
                                    ),
                                    modifier = sheetModifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { /* swallow: a tap on the sheet must not close it */ }
                                ) {
                                    Column {
                                        if (!phone) {
                                            // sheet handle: tap toggles, drag up expands, drag down closes
                                            var dragTotal by remember { mutableStateOf(0f) }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp, bottom = 2.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) { sheetExpanded = !sheetExpanded }
                                                    .pointerInput(Unit) {
                                                        detectVerticalDragGestures(
                                                            onDragStart = { dragTotal = 0f },
                                                            onVerticalDrag = { _, dy -> dragTotal += dy },
                                                            onDragEnd = {
                                                                when {
                                                                    dragTotal < -40f -> sheetExpanded =
                                                                        true

                                                                    dragTotal > 40f && sheetExpanded -> sheetExpanded =
                                                                        false

                                                                    dragTotal > 40f -> selectedTaskId =
                                                                        null
                                                                }
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(vertical = 6.dp)
                                                        .size(width = 44.dp, height = 5.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.18f
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                        TaskDetailsPane(
                                            task = selectedTask,
                                            viewModel = viewModel,
                                            speedDisplayUnit = state.speedDisplayUnit,
                                            fullScreen = phone,
                                            onClose = { selectedTaskId = null },
                                            fillHeight = phone || sheetExpanded
                                        )
                                    }
                                }
                                // The details are their own window: messages shown while they are
                                // open (e.g. "Sent to <device>") would be hidden behind it.
                                androidx.compose.material3.SnackbarHost(
                                    snackbarHostState,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                )
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
                    courseReading = state.courseReading,
                    inspectedMediaQualities = state.inspectedMediaQualities,
                    inspectedSubtitles = state.inspectedSubtitles,
                    embedSubtitles = state.embedSubtitles,
                    onSubtitleToggled = { lang, selected ->
                        viewModel.onEvent(DashboardUiEvent.OnSubtitleToggled(lang, selected))
                    },
                    onToggleEmbedSubtitles = { embed ->
                        viewModel.onEvent(DashboardUiEvent.OnToggleEmbedSubtitles(embed))
                    },
                    inspectMediaError = state.inspectMediaError,
                    isYtDlpInstalled = state.isYtDlpInstalled,
                    ytDlpStatus = state.ytDlpStatus,
                    isDownloadingYtDlp = state.isDownloadingYtDlp,
                    onDownloadYtDlp = { viewModel.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked) },
                    onUrlChanged = { viewModel.onEvent(DashboardUiEvent.OnUrlChanged(it)) },
                    suggestedFileName = state.suggestedFileName,
                    fileNameOverride = state.fileNameOverride,
                    sortIntoFolders = state.sortIntoFolders,
                    onFileNameChanged = { viewModel.onEvent(DashboardUiEvent.OnFileNameChanged(it)) },
                    checksumInput = state.checksumInput,
                    onChecksumChanged = { viewModel.onEvent(DashboardUiEvent.OnChecksumChanged(it)) },
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
                    audioConvertFormat = state.audioConvertFormat,
                    onAudioConvertFormatSelected = {
                        viewModel.onEvent(DashboardUiEvent.OnAudioConvertFormatSelected(it))
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
                    onPlaylistFolderChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnPlaylistFolderChanged(
                                it
                            )
                        )
                    },
                    linkList = state.linkList,
                    onLinkListEntryToggled = { i, on ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListEntryToggled(
                                i,
                                on
                            )
                        )
                    },
                    onLinkListSelectAll = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListSelectAll(
                                it
                            )
                        )
                    },
                    linkListFolder = state.linkListFolder,
                    onLinkListFolderChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListFolderChanged(
                                it
                            )
                        )
                    },
                    onLinkListTorrentFileToggled = { link, file, on ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListTorrentFileToggled(link, file, on)
                        )
                    },
                    onLinkListTorrentFilePriority = { link, file, p ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListTorrentFilePriority(link, file, p)
                        )
                    },
                    onLinkListQualitySelected = { link, quality ->
                        viewModel.onEvent(DashboardUiEvent.OnLinkListQualitySelected(link, quality))
                    },
                    onLinkListRetryInspect = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListRetryInspect(
                                it
                            )
                        )
                    },
                    onLinkListTorrentSelectAll = { link, on ->
                        viewModel.onEvent(
                            DashboardUiEvent.OnLinkListTorrentSelectAll(
                                link,
                                on
                            )
                        )
                    },
                    scheduleStartMs = state.scheduleStartMs,
                    onScheduleChanged = { viewModel.onEvent(DashboardUiEvent.OnScheduleChanged(it)) },
                    selectedConnections = state.selectedConnections,
                    defaultConnections = state.defaultConnections,
                    onConnectionsChanged = {
                        viewModel.onEvent(
                            DashboardUiEvent.OnConnectionsChanged(
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
            if (state.removeConfirmationTaskId != null || state.removeConfirmationTaskIds.isNotEmpty()) {
                RemoveConfirmationDialog(
                    onConfirm = { deleteFile ->
                        viewModel.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(deleteFile))
                    },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnDismissRemoveDialog) },
                    count = if (state.removeConfirmationTaskId != null) 1 else state.removeConfirmationTaskIds.size
                )
            }

            missingFileTaskId.value?.let { missingId ->
                val missingTask = state.allTasks.firstOrNull { it.id == missingId }
                if (missingTask != null) com.michelle.raouf.fetch_jet.ui.MissingFileDialog(
                    fileName = missingTask.fileName,
                    onRemove = {
                        missingFileTaskId.value = null
                        viewModel.onEvent(DashboardUiEvent.OnRemoveClicked(missingId))
                        viewModel.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(false))
                    },
                    onDismiss = { missingFileTaskId.value = null }
                )
            }

            // New FetchJet version: automatic check on start + "what's new" dialog
            com.michelle.raouf.fetch_jet.ui.UpdateDialogHost()

            com.michelle.raouf.fetch_jet.ui.MainDownloaderPrompt(
                onOpenSettings = { viewModel.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.SETTINGS)) }
            )

            com.michelle.raouf.fetch_jet.ui.WhenDoneDialogHost()

            // A link just copied (clipboard watcher): download it?
            state.clipboardOfferUrl?.let { url ->
                ClipboardOfferDialog(
                    url = url,
                    onConfirm = { viewModel.onEvent(DashboardUiEvent.OnClipboardOfferAccepted) },
                    onDismiss = { viewModel.onEvent(DashboardUiEvent.OnClipboardOfferDismissed) }
                )
            }

            // A browser extension asks to use the local bridge (desktop / Android).
            val pairing by com.michelle.raouf.fetch_jet.core.network.BridgePairing.pending
                .collectAsState()
            pairing?.let { request ->
                BridgePairingDialog(
                    request,
                    onAllow = { com.michelle.raouf.fetch_jet.core.network.BridgePairing.answer(true) },
                    onBlock = { com.michelle.raouf.fetch_jet.core.network.BridgePairing.block() },
                    onDismiss = {
                        com.michelle.raouf.fetch_jet.core.network.BridgePairing.answer(
                            false
                        )
                    }
                )
            }

            // "Send to device" sheet + first-time pairing (ROADMAP-1.3.0 item 4).
            if (state.deviceShareTaskId != null) {
                com.michelle.raouf.fetch_jet.ui.DeviceSendSheet(state) { viewModel.onEvent(it) }
            }
            state.devicePairing?.let { pairState ->
                com.michelle.raouf.fetch_jet.ui.DevicePairingDialog(
                    pairing = pairState,
                    onConfirm = { viewModel.onEvent(DashboardUiEvent.OnConfirmPairingClicked) },
                    onCancel = { viewModel.onEvent(DashboardUiEvent.OnCancelPairingClicked) },
                    onBlock = { viewModel.onEvent(DashboardUiEvent.OnBlockPairingClicked) }
                )
            }
            val receiveState = state.deviceReceiving
            if (state.devicePairing == null && (receiveState is com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState.Receiving ||
                    receiveState is com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState.Failed)
            ) {
                com.michelle.raouf.fetch_jet.ui.DeviceReceiveDialog(
                    receiving = receiveState,
                    onClose = { viewModel.onEvent(DashboardUiEvent.OnDismissReceiveClicked) }
                )
            }

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

            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.material3.SnackbarHost(snackbarHostState)
            }
        }
    }
        }
    }
}
