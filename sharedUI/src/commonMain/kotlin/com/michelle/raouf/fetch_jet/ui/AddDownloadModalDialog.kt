package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.michelle.raouf.fetch_jet.utils.formatSecondsToHhMm
import com.michelle.raouf.fetch_jet.utils.parseHhMmToSeconds

@Composable
fun AddDownloadModalDialog(
    inputUrl: String,
    destinationPath: String,
    disableTorrentUpload: Boolean,
    detectedEngine: EngineType,
    isLoading: Boolean,
    errorMessage: String?,
    pathResolver: PathResolver,
    isInspectingTorrent: Boolean,
    inspectedTorrentName: String?,
    inspectedTorrentFiles: List<TorrentFileEntry>,
    inspectError: String?,
    isInspectingMedia: Boolean,
    inspectedMediaQualities: List<MediaQualityEntry>,
    inspectMediaError: String?,
    isYtDlpInstalled: Boolean,
    ytDlpStatus: String = "",
    isDownloadingYtDlp: Boolean = false,
    onDownloadYtDlp: () -> Unit = {},
    onUrlChanged: (String) -> Unit,
    onDestinationPathChanged: (String) -> Unit,
    suggestedFileName: String = "",
    fileNameOverride: String = "",
    sortIntoFolders: Boolean = false,
    onFileNameChanged: (String) -> Unit = {},
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    onInspectTorrent: () -> Unit,
    onInspectMedia: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onSelectAllFilesToggled: (Boolean) -> Unit,
    onMediaQualitySelectionToggled: (Int, Boolean) -> Unit,
    onSelectAllMediaQualitiesToggled: (Boolean) -> Unit,
    clipStartTime: String = "",
    clipEndTime: String = "",
    isClipRangeEnabled: Boolean = false,
    mediaDurationSeconds: Long = 0L,
    onClipStartTimeChanged: (String) -> Unit = {},
    onClipEndTimeChanged: (String) -> Unit = {},
    onToggleClipRange: (Boolean) -> Unit = {},
    clipFastMode: Boolean = false,
    onToggleClipFastMode: (Boolean) -> Unit = {},
    inspectedPlaylist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? = null,
    playlistQuality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality =
        com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality.BEST,
    onPlaylistEntryToggled: (Int, Boolean) -> Unit = { _, _ -> },
    onPlaylistSelectAll: (Boolean) -> Unit = {},
    onPlaylistQualityChanged: (com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality) -> Unit = {},
    onPlaylistUseSingleVideo: () -> Unit = {},
    linkList: List<com.michelle.raouf.fetch_jet.presentation.dashboard.LinkListEntry> = emptyList(),
    onLinkListEntryToggled: (Int, Boolean) -> Unit = { _, _ -> },
    onLinkListSelectAll: (Boolean) -> Unit = {},
    onLinkListTorrentFileToggled: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    onLinkListTorrentFilePriority: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onLinkListTorrentSelectAll: (Int, Boolean) -> Unit = { _, _ -> },
    scheduleStartMs: Long = 0L,
    onScheduleChanged: (Long) -> Unit = {},
    selectedConnections: Int = 0,
    defaultConnections: Int = 0,
    onConnectionsChanged: (Int) -> Unit = {},
    onEngineSelected: (EngineType) -> Unit = {},
    onAddDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val defaultDownloads = remember { pathResolver.getDownloadsDirectory() }
    val defaultDocs = remember { pathResolver.getDocumentsDirectory() }
    val defaultDesktop = remember { pathResolver.getDesktopDirectory() }
    val coroutineScope = rememberCoroutineScope()

    val urlFieldState = remember {
        mutableStateOf(
            TextFieldValue(
                text = inputUrl,
                selection = TextRange(inputUrl.length)
            )
        )
    }
    var urlTextFieldValue by urlFieldState
    LaunchedEffect(inputUrl) {
        if (inputUrl != urlTextFieldValue.text) {
            urlTextFieldValue = TextFieldValue(
                text = inputUrl,
                selection = TextRange(inputUrl.length)
            )
        }
    }

    val pathFieldState = remember {
        mutableStateOf(
            TextFieldValue(
                text = destinationPath,
                selection = TextRange(destinationPath.length)
            )
        )
    }
    var pathTextFieldValue by pathFieldState
    LaunchedEffect(destinationPath) {
        if (destinationPath != pathTextFieldValue.text) {
            pathTextFieldValue = TextFieldValue(
                text = destinationPath,
                selection = TextRange(destinationPath.length)
            )
        }
    }

    val showDirectoryPickerState = remember { mutableStateOf(false) }
    var showDirectoryPicker by showDirectoryPickerState
    val showStartTimePickerState = remember { mutableStateOf(false) }
    var showStartTimePicker by showStartTimePickerState
    val showEndTimePickerState = remember { mutableStateOf(false) }
    var showEndTimePicker by showEndTimePickerState

    val fullVideoDurationHhMm = remember(mediaDurationSeconds) {
        if (mediaDurationSeconds > 0) formatSecondsToHhMm(mediaDurationSeconds) else "00:10:00"
    }

    val currentStartSec =
        remember(clipStartTime) { parseHhMmToSeconds(clipStartTime.ifBlank { "00:00" }) }
    val currentEndSec = remember(
        clipEndTime,
        fullVideoDurationHhMm
    ) { parseHhMmToSeconds(clipEndTime.ifBlank { fullVideoDurationHhMm }) }

    val isStartAfterEnd = isClipRangeEnabled && currentStartSec >= currentEndSec
    val isEndExceedingVideo =
        isClipRangeEnabled && mediaDurationSeconds > 0L && currentEndSec > mediaDurationSeconds
    val isStartExceedingVideo =
        isClipRangeEnabled && mediaDurationSeconds > 0L && currentStartSec >= mediaDurationSeconds
    val isClipRangeValid =
        !isClipRangeEnabled || (!isStartAfterEnd && !isEndExceedingVideo && !isStartExceedingVideo)

    val clipValidationErrorMessage = when {
        !isClipRangeEnabled -> null
        isStartAfterEnd -> LocalizationManager.Strings.clipStartAfterEnd(
            clipStartTime.ifBlank { "00:00" },
            clipEndTime.ifBlank { fullVideoDurationHhMm })

        isEndExceedingVideo -> LocalizationManager.Strings.clipEndExceedsVideo(
            clipEndTime.ifBlank { fullVideoDurationHhMm },
            fullVideoDurationHhMm
        )

        isStartExceedingVideo -> LocalizationManager.Strings.clipStartExceedsVideo(
            fullVideoDurationHhMm
        )
        else -> null
    }

    if (showStartTimePicker) {
        val maxForStart = if (currentEndSec > 0L) {
            if (mediaDurationSeconds > 0L) minOf(
                mediaDurationSeconds,
                currentEndSec - 1L
            ) else currentEndSec - 1L
        } else {
            mediaDurationSeconds
        }
        TimeRangePickerDialog(
            title = "⏱️ ${LocalizationManager.Strings.setStartTimeTitle}",
            initialHhMm = clipStartTime.ifBlank { "00:00" },
            maxDurationSeconds = maxForStart.coerceAtLeast(0L),
            minTimeSeconds = 0L,
            onTimeSelected = { selected ->
                onClipStartTimeChanged(selected)
                showStartTimePicker = false
            },
            onDismissRequest = {
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimeRangePickerDialog(
            title = "⏱️ ${LocalizationManager.Strings.setEndTimeTitle}",
            initialHhMm = clipEndTime.ifBlank { fullVideoDurationHhMm },
            maxDurationSeconds = mediaDurationSeconds,
            minTimeSeconds = currentStartSec,
            onTimeSelected = { selected ->
                onClipEndTimeChanged(selected)
                showEndTimePicker = false
            },
            onDismissRequest = {
                showEndTimePicker = false
            }
        )
    }

    if (showDirectoryPicker) {
        DirectoryPickerDialog(
            initialPath = destinationPath,
            pathResolver = pathResolver,
            onDirectorySelected = { selected ->
                onDestinationPathChanged(selected)
                showDirectoryPicker = false
            },
            onDismissRequest = {
                showDirectoryPicker = false
            }
        )
    }

    // Auto-inspect: when URL changes and is valid, automatically trigger inspection
    LaunchedEffect(inputUrl, detectedEngine) {
        if (inputUrl.isNotBlank()) {
            when (detectedEngine) {
                EngineType.BITTORRENT -> {
                    if (inspectedTorrentFiles.isEmpty() && !isInspectingTorrent && inspectError == null) {
                        onInspectTorrent()
                    }
                }
                EngineType.MEDIA_STREAM -> {
                    if (inspectedMediaQualities.isEmpty() && inspectedPlaylist == null && !isInspectingMedia && inspectMediaError == null && isYtDlpInstalled) {
                        onInspectMedia()
                    }
                }
                else -> { /* HTTP_DIRECT: no inspection needed */
                }
            }
        }
    }

    val isUrlValid = inputUrl.isNotBlank()
    val showsLinkList = linkList.isNotEmpty()
    val listInspecting = showsLinkList && linkList.any { it.isSelected && it.isInspecting }
    val isInspectionComplete =
        if (showsLinkList) linkList.any { it.isSelected } && !listInspecting else when (detectedEngine) {
        EngineType.BITTORRENT -> inspectedTorrentFiles.isNotEmpty() && inspectedTorrentFiles.any { it.isSelected } && inspectError == null
        EngineType.MEDIA_STREAM -> inspectMediaError == null && (
                inspectedPlaylist?.entries?.any { it.isSelected } == true ||
                        (inspectedMediaQualities.isNotEmpty() && inspectedMediaQualities.any { it.isSelected }))
        EngineType.HTTP_DIRECT -> true
    }
    val isInspecting = isInspectingTorrent || isInspectingMedia || listInspecting
    val hasActiveError = errorMessage != null || inspectError != null || inspectMediaError != null
    val canStartDownload =
        isUrlValid && isInspectionComplete && !isLoading && !isInspecting && !hasActiveError && isClipRangeValid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 660.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .padding(vertical = 12.dp)
                .clearFocusOnTapOutside(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                AddDialogHeader(onDismiss = onDismiss)

                Spacer(modifier = Modifier.height(16.dp))

                UrlInputSection(
                    inputUrl = inputUrl,
                    urlField = urlFieldState,
                    onUrlChanged = onUrlChanged
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (showsLinkList) {
                    LinkListSection(
                        links = linkList,
                        onToggled = onLinkListEntryToggled,
                        onSelectAll = onLinkListSelectAll,
                        onTorrentFileToggled = onLinkListTorrentFileToggled,
                        onTorrentFilePriority = onLinkListTorrentFilePriority,
                        onTorrentSelectAll = onLinkListTorrentSelectAll
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                EngineSelector(detectedEngine = detectedEngine, onEngineSelected = onEngineSelected)

                TorrentInspectionSection(
                    detectedEngine = detectedEngine,
                    inputUrl = inputUrl,
                    isInspectingTorrent = isInspectingTorrent,
                    inspectError = inspectError,
                    inspectedTorrentName = inspectedTorrentName,
                    inspectedTorrentFiles = inspectedTorrentFiles,
                    onInspectTorrent = onInspectTorrent,
                    onFileSelectionToggled = onFileSelectionToggled,
                    onFilePriorityChanged = onFilePriorityChanged,
                    onSelectAllFilesToggled = onSelectAllFilesToggled
                )

                MediaInspectionSection(
                    inputUrl = inputUrl,
                    detectedEngine = detectedEngine,
                    isInspectingMedia = isInspectingMedia,
                    inspectedMediaQualities = inspectedMediaQualities,
                    inspectMediaError = inspectMediaError,
                    isYtDlpInstalled = isYtDlpInstalled,
                    ytDlpStatus = ytDlpStatus,
                    isDownloadingYtDlp = isDownloadingYtDlp,
                    onDownloadYtDlp = onDownloadYtDlp,
                    onInspectMedia = onInspectMedia,
                    onMediaQualitySelectionToggled = onMediaQualitySelectionToggled,
                    onSelectAllMediaQualitiesToggled = onSelectAllMediaQualitiesToggled,
                    clipStartTime = clipStartTime,
                    clipEndTime = clipEndTime,
                    isClipRangeEnabled = isClipRangeEnabled,
                    mediaDurationSeconds = mediaDurationSeconds,
                    onClipStartTimeChanged = onClipStartTimeChanged,
                    onClipEndTimeChanged = onClipEndTimeChanged,
                    onToggleClipRange = onToggleClipRange,
                    clipFastMode = clipFastMode,
                    onToggleClipFastMode = onToggleClipFastMode,
                    inspectedPlaylist = inspectedPlaylist,
                    playlistQuality = playlistQuality,
                    onPlaylistEntryToggled = onPlaylistEntryToggled,
                    onPlaylistSelectAll = onPlaylistSelectAll,
                    onPlaylistQualityChanged = onPlaylistQualityChanged,
                    onPlaylistUseSingleVideo = onPlaylistUseSingleVideo,
                    showStartTimePickerState = showStartTimePickerState,
                    showEndTimePickerState = showEndTimePickerState,
                    fullVideoDurationHhMm = fullVideoDurationHhMm,
                    isStartAfterEnd = isStartAfterEnd,
                    isEndExceedingVideo = isEndExceedingVideo,
                    isStartExceedingVideo = isStartExceedingVideo,
                    clipValidationErrorMessage = clipValidationErrorMessage
                )
                Spacer(modifier = Modifier.height(14.dp))
                }

                // When to start: now, in a while, or at a time of day
                ScheduleStartSection(
                    scheduleStartMs = scheduleStartMs,
                    onScheduleChanged = onScheduleChanged
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Direct files: how many connections split the file (IDM-style pieces).
                if (detectedEngine == EngineType.HTTP_DIRECT && !showsLinkList) {
                    ConnectionsSection(
                        selected = selectedConnections,
                        appDefault = defaultConnections,
                        onChanged = onConnectionsChanged
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (!showsLinkList) FileNameSection(
                    suggestedFileName = suggestedFileName,
                    fileNameOverride = fileNameOverride,
                    isInspectingMedia = isInspectingMedia,
                    isInspectingTorrent = isInspectingTorrent,
                    onFileNameChanged = onFileNameChanged
                )

                DestinationSection(
                    destinationPath = destinationPath,
                    pathField = pathFieldState,
                    showDirectoryPickerState = showDirectoryPickerState,
                    sortIntoFolders = sortIntoFolders,
                    fileNameOverride = fileNameOverride,
                    suggestedFileName = suggestedFileName,
                    detectedEngine = detectedEngine,
                    pathResolver = pathResolver,
                    defaultDownloads = defaultDownloads,
                    defaultDocs = defaultDocs,
                    defaultDesktop = defaultDesktop,
                    onDestinationPathChanged = onDestinationPathChanged
                )

                TorrentLeechToggle(
                    detectedEngine = detectedEngine,
                    disableTorrentUpload = disableTorrentUpload,
                    onDisableTorrentUploadToggled = onDisableTorrentUploadToggled
                )

                Spacer(modifier = Modifier.height(20.dp))

                AddDialogActions(
                    errorMessage = errorMessage,
                    inspectError = inspectError,
                    inspectMediaError = inspectMediaError,
                    hasActiveError = hasActiveError,
                    isInspectionComplete = isInspectionComplete,
                    isInspecting = isInspecting,
                    isUrlValid = isUrlValid,
                    isClipRangeValid = isClipRangeValid,
                    isLoading = isLoading,
                    canStartDownload = canStartDownload,
                    detectedEngine = detectedEngine,
                    inspectedPlaylist = inspectedPlaylist,
                    scheduleStartMs = scheduleStartMs,
                    linkCount = if (showsLinkList) linkList.count { it.isSelected } else 0,
                    onInspectTorrent = onInspectTorrent,
                    onInspectMedia = onInspectMedia,
                    onAddDownload = onAddDownload,
                    onDismiss = onDismiss
                )
            }
        }
    }
}
