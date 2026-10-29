package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.michelle.raouf.fetch_jet.getClipboardText
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSecondsToDisplayFriendly
import com.michelle.raouf.fetch_jet.utils.formatSecondsToHhMm
import com.michelle.raouf.fetch_jet.utils.formatSizeDisplay
import com.michelle.raouf.fetch_jet.utils.parseHhMmToSeconds
import kotlinx.coroutines.launch

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
    scheduleStartMs: Long = 0L,
    onScheduleChanged: (Long) -> Unit = {},
    onEngineSelected: (EngineType) -> Unit = {},
    onAddDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val defaultDownloads = remember { pathResolver.getDownloadsDirectory() }
    val defaultDocs = remember { pathResolver.getDocumentsDirectory() }
    val defaultDesktop = remember { pathResolver.getDesktopDirectory() }
    val coroutineScope = rememberCoroutineScope()

    var urlTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputUrl,
                selection = TextRange(inputUrl.length)
            )
        )
    }
    LaunchedEffect(inputUrl) {
        if (inputUrl != urlTextFieldValue.text) {
            urlTextFieldValue = TextFieldValue(
                text = inputUrl,
                selection = TextRange(inputUrl.length)
            )
        }
    }

    var pathTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = destinationPath,
                selection = TextRange(destinationPath.length)
            )
        )
    }
    LaunchedEffect(destinationPath) {
        if (destinationPath != pathTextFieldValue.text) {
            pathTextFieldValue = TextFieldValue(
                text = destinationPath,
                selection = TextRange(destinationPath.length)
            )
        }
    }

    var showDirectoryPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val fullVideoDurationHhMm = remember(mediaDurationSeconds) {
        if (mediaDurationSeconds > 0) formatSecondsToHhMm(mediaDurationSeconds) else "00:10"
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
        isStartAfterEnd -> "Start time (${clipStartTime.ifBlank { "00:00" }}) must be before end time (${clipEndTime.ifBlank { fullVideoDurationHhMm }})"
        isEndExceedingVideo -> "End time (${clipEndTime.ifBlank { fullVideoDurationHhMm }}) exceeds video duration ($fullVideoDurationHhMm)"
        isStartExceedingVideo -> "Start time exceeds video duration ($fullVideoDurationHhMm)"
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
            title = "⏱️ Set Start Time (From)",
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
            title = "⏱️ Set End Time (To)",
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
    val isInspectionComplete = when (detectedEngine) {
        EngineType.BITTORRENT -> inspectedTorrentFiles.isNotEmpty() && inspectedTorrentFiles.any { it.isSelected } && inspectError == null
        EngineType.MEDIA_STREAM -> inspectMediaError == null && (
                inspectedPlaylist?.entries?.any { it.isSelected } == true ||
                        (inspectedMediaQualities.isNotEmpty() && inspectedMediaQualities.any { it.isSelected }))
        EngineType.HTTP_DIRECT -> true
    }
    val isInspecting = isInspectingTorrent || isInspectingMedia
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
                .padding(vertical = 12.dp),
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
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Add New Download",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Enter link to start downloading",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL Input Field
                Text(
                    text = "Download URL / Magnet Link",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = urlTextFieldValue,
                    onValueChange = { newValue ->
                        urlTextFieldValue = newValue
                        if (newValue.text != inputUrl) {
                            onUrlChanged(newValue.text)
                        }
                    },
                    placeholder = {
                        Text(
                            "https://... or magnet:?xt=...",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    leadingIcon = {
                        Text("🔗", fontSize = 16.sp)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlTextFieldValue.text.isNotBlank()) {
                                IconButton(onClick = {
                                    urlTextFieldValue = TextFieldValue("", TextRange.Zero)
                                    onUrlChanged("")
                                }) {
                                    Text(
                                        "✕",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val clip = getClipboardText()?.trim()
                                            if (!clip.isNullOrBlank()) {
                                                urlTextFieldValue =
                                                    TextFieldValue(clip, TextRange(clip.length))
                                                onUrlChanged(clip)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    )
                                ) {
                                    Text(
                                        "📋 Paste",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.35f
                        ),
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Selector
                Text(
                    text = "Download Engine",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EngineChoiceChip(
                        label = "⚡ Direct",
                        isSelected = detectedEngine == EngineType.HTTP_DIRECT,
                        onClick = { onEngineSelected(EngineType.HTTP_DIRECT) },
                        modifier = Modifier.weight(1f)
                    )
                    if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor) {
                        EngineChoiceChip(
                            label = "🎬 Media",
                            isSelected = detectedEngine == EngineType.MEDIA_STREAM,
                            onClick = { onEngineSelected(EngineType.MEDIA_STREAM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent) {
                        EngineChoiceChip(
                            label = "🧲 Torrent",
                            isSelected = detectedEngine == EngineType.BITTORRENT,
                            onClick = { onEngineSelected(EngineType.BITTORRENT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Torrent File Inspection Section
                if (detectedEngine == EngineType.BITTORRENT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isInspectingTorrent) {
                        InspectionLoadingBanner(
                            title = "Inspecting Torrent Metadata...",
                            subtitle = "Fetching file manifest and peers"
                        )
                    } else if (inspectError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                                    alpha = 0.25f
                                )
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Torrent Inspection Failed",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = inspectError,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onInspectTorrent,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        "🔄 Retry Inspection",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    } else if (inspectedTorrentFiles.isEmpty()) {
                        OutlinedButton(
                            onClick = onInspectTorrent,
                            enabled = inputUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF38BDF8)
                            )
                        ) {
                            Text(
                                "🧲 Inspect Torrent Files",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        TorrentFilesSelectionCard(
                            torrentName = inspectedTorrentName,
                            files = inspectedTorrentFiles,
                            onFileSelectionToggled = onFileSelectionToggled,
                            onFilePriorityChanged = onFilePriorityChanged,
                            onSelectAll = onSelectAllFilesToggled
                        )
                    }
                }

                // Media Stream Inspection Section
                if (detectedEngine == EngineType.MEDIA_STREAM) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isInspectingMedia) {
                        InspectionLoadingBanner(
                            title = "Inspecting Video Qualities...",
                            subtitle = "Scanning available video resolutions and audio streams"
                        )
                    } else if (inspectMediaError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                                    alpha = 0.25f
                                )
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Video Inspection Failed",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = inspectMediaError,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onInspectMedia,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        "🔄 Retry Inspection",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    } else if (!isYtDlpInstalled) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF451A1A)),
                            border = BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val preparing = isDownloadingYtDlp ||
                                        ytDlpStatus.startsWith("Preparing", ignoreCase = true)
                                Text(
                                    text = if (preparing) "⏳ Media engine is starting..." else "⚠️ Media engine is not ready",
                                    color = if (preparing) Color(0xFFFDE68A) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ytDlpStatus.ifBlank { "Video stream extraction requires yt-dlp installed or embedded." },
                                    color = Color(0xFFFEE2E2),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onDownloadYtDlp,
                                    enabled = !preparing,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (preparing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Preparing engine...", fontSize = 12.sp)
                                    } else {
                                        Text("Download / Start media engine", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (inspectedPlaylist != null) {
                        PlaylistSelectionCard(
                            playlist = inspectedPlaylist,
                            quality = playlistQuality,
                            onEntryToggled = onPlaylistEntryToggled,
                            onSelectAll = onPlaylistSelectAll,
                            onQualityChanged = onPlaylistQualityChanged,
                            onUseSingleVideo = if (inputUrl.contains("v=") || inputUrl.contains("youtu.be/")) onPlaylistUseSingleVideo else null
                        )
                    } else if (inspectedMediaQualities.isEmpty()) {
                        val isInputAudio = inputUrl.substringBefore('?').substringAfterLast('.', "")
                            .lowercase() in listOf(
                            "mp3",
                            "m4a",
                            "flac",
                            "wav",
                            "aac",
                            "ogg",
                            "opus"
                        )
                        OutlinedButton(
                            onClick = onInspectMedia,
                            enabled = inputUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF38BDF8)
                            )
                        ) {
                            Text(
                                if (isInputAudio) "🎵 Inspect & Select Audio Formats" else "🎬 Inspect & Select Video Quality",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        MediaQualitiesSelectionCard(
                            qualities = inspectedMediaQualities,
                            onQualitySelectionToggled = onMediaQualitySelectionToggled,
                            onSelectAll = onSelectAllMediaQualitiesToggled
                        )

                        // Video/Audio Time Range Trimming Section (Only available when media duration is known!)
                        if (mediaDurationSeconds > 0L) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.35f
                                    )
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Audio only when the *selected* quality is audio (the list always
                                    // contains audio options, so checking "any" said "Audio" for videos).
                                    val selectedQuality =
                                        inspectedMediaQualities.firstOrNull { it.isSelected }
                                    val isAnyAudio =
                                        selectedQuality != null && selectedQuality.let {
                                        it.qualityLabel.contains("Audio", ignoreCase = true) ||
                                                it.format in listOf(
                                            "mp3",
                                            "m4a",
                                            "flac",
                                            "wav",
                                            "aac",
                                            "ogg",
                                            "opus"
                                        )
                                    }
                                    val mediaTypeLabel = if (isAnyAudio) "Audio" else "Video"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleClipRange(!isClipRangeEnabled) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("✂️", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Clip $mediaTypeLabel Section (Optional)",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Download a specific time range only",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = isClipRangeEnabled,
                                            onCheckedChange = onToggleClipRange,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }

                                    if (isClipRangeEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (mediaDurationSeconds > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.45f
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 6.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "⏱️ $mediaTypeLabel Duration: ${
                                                            clockText(
                                                                mediaDurationSeconds
                                                            )
                                                        } (${
                                                            formatSecondsToDisplayFriendly(
                                                                mediaDurationSeconds
                                                            )
                                                        }) - Full range selected by default",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        val isStartHasError =
                                            isStartAfterEnd || isStartExceedingVideo
                                        val isEndHasError = isStartAfterEnd || isEndExceedingVideo

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Start Time Card (Clickable)
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { showStartTimePicker = true },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    if (isStartHasError) 1.5.dp else 1.dp,
                                                    if (isStartHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.5f
                                                    )
                                                ),
                                                color = if (isStartHasError) MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.15f
                                                ) else MaterialTheme.colorScheme.surface
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Start Time (From)",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isStartHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text("🕒", fontSize = 12.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    val startDisplay =
                                                        clipStartTime.ifBlank { "00:00" }
                                                    Text(
                                                        text = startDisplay,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isStartHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = formatSecondsToDisplayFriendly(parseHhMmToSeconds(startDisplay)),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // End Time Card (Clickable)
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { showEndTimePicker = true },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    if (isEndHasError) 1.5.dp else 1.dp,
                                                    if (isEndHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.5f
                                                    )
                                                ),
                                                color = if (isEndHasError) MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.15f
                                                ) else MaterialTheme.colorScheme.surface
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "End Time (To)",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isEndHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text("🕒", fontSize = 12.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    val endDisplay =
                                                        clipEndTime.ifBlank { fullVideoDurationHhMm }
                                                    Text(
                                                        text = endDisplay,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isEndHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = formatSecondsToDisplayFriendly(parseHhMmToSeconds(endDisplay)),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (clipValidationErrorMessage != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.35f
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 6.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("⚠️", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = clipValidationErrorMessage,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (mediaDurationSeconds > 0) {
                                                QuickTimeChip(
                                                    label = "Full ${if (isAnyAudio) "Audio" else "Video"} (${
                                                        clockText(
                                                            mediaDurationSeconds
                                                        )
                                                    })",
                                                    onClick = {
                                                        onClipStartTimeChanged("00:00")
                                                        onClipEndTimeChanged(fullVideoDurationHhMm)
                                                    }
                                                )
                                            }
                                            if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 300L) {
                                                QuickTimeChip(
                                                    label = "First 5 min",
                                                    onClick = {
                                                        onClipStartTimeChanged("00:00"); onClipEndTimeChanged(
                                                        "00:05"
                                                    )
                                                    })
                                            }
                                            if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 900L) {
                                                QuickTimeChip(
                                                    label = "First 15 min",
                                                    onClick = {
                                                        onClipStartTimeChanged("00:00"); onClipEndTimeChanged(
                                                        "00:15"
                                                    )
                                                    })
                                            }
                                            if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 1800L) {
                                                QuickTimeChip(
                                                    label = "First 30 min",
                                                    onClick = {
                                                        onClipStartTimeChanged("00:00"); onClipEndTimeChanged(
                                                        "00:30"
                                                    )
                                                    })
                                            }
                                            if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 3600L) {
                                                QuickTimeChip(
                                                    label = "First 1 hour",
                                                    onClick = {
                                                        onClipStartTimeChanged("00:00"); onClipEndTimeChanged(
                                                        "01:00"
                                                    )
                                                    })
                                            }
                                        }

                                        // How the clip is fetched: two always-visible choices, the
                                        // chosen one highlighted (a single switch whose label
                                        // changed with its state was confusing).
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "How to download the clip",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val fullSizeText = selectedQuality?.size?.takeIf { it > 0L }
                                            ?.let { " (${formatBytes(it)})" } ?: ""
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ClipModeOption(
                                                selected = !clipFastMode,
                                                title = "✂️ Only the clip",
                                                lines = listOf(
                                                    "Downloads only the part you chose",
                                                    "Slower: YouTube limits the speed",
                                                    "Pause starts the clip over"
                                                ),
                                                onClick = { onToggleClipFastMode(false) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            ClipModeOption(
                                                selected = clipFastMode,
                                                title = "⚡ Full $mediaTypeLabel, then cut",
                                                lines = listOf(
                                                    "Downloads the whole $mediaTypeLabel$fullSizeText",
                                                    "Faster: full speed",
                                                    "Then keeps only your part",
                                                    "Pause / resume works"
                                                ),
                                                onClick = { onToggleClipFastMode(true) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // When to start: now, in a while, or at a time of day
                ScheduleStartSection(
                    scheduleStartMs = scheduleStartMs,
                    onScheduleChanged = onScheduleChanged
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Save Destination Section
                Text(
                    text = "Save Destination Directory",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = pathTextFieldValue,
                    onValueChange = { newValue ->
                        pathTextFieldValue = newValue
                        if (newValue.text != destinationPath) {
                            onDestinationPathChanged(newValue.text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    leadingIcon = {
                        Text("📁", fontSize = 16.sp)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showDirectoryPicker = true }) {
                            Text("📂", fontSize = 16.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.35f
                        ),
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Directory Buttons (Always fit cleanly on mobile!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickFolderButton(
                        icon = "📥",
                        label = "Downloads",
                        onClick = {
                            pathTextFieldValue =
                                TextFieldValue(defaultDownloads, TextRange(defaultDownloads.length))
                            onDestinationPathChanged(defaultDownloads)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickFolderButton(
                        icon = "📄",
                        label = "Documents",
                        onClick = {
                            pathTextFieldValue =
                                TextFieldValue(defaultDocs, TextRange(defaultDocs.length))
                            onDestinationPathChanged(defaultDocs)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    val isMobile = defaultDesktop.isBlank() || defaultDesktop.contains(
                        "storage",
                        ignoreCase = true
                    ) || defaultDesktop.contains("Documents", ignoreCase = true)
                    QuickFolderButton(
                        icon = if (isMobile) "🎬" else "💻",
                        label = if (isMobile) "Media" else "Desktop",
                        onClick = {
                            if (isMobile) {
                                val created =
                                    pathResolver.createSubdirectory(defaultDownloads, "Media")
                                val path = created ?: "$defaultDownloads/Media"
                                pathTextFieldValue = TextFieldValue(path, TextRange(path.length))
                                onDestinationPathChanged(path)
                            } else {
                                pathTextFieldValue =
                                    TextFieldValue(defaultDesktop, TextRange(defaultDesktop.length))
                                onDestinationPathChanged(defaultDesktop)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Torrent Leech Mode Toggle
                if (detectedEngine == EngineType.BITTORRENT) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = disableTorrentUpload,
                            onCheckedChange = onDisableTorrentUploadToggled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF0284C7),
                                uncheckedColor = Color(0xFF64748B)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disable Torrent Upload (Leech-Only Mode)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Inline Error Notice
                val activeError = errorMessage ?: inspectError ?: inspectMediaError
                if (activeError != null) {
                    InlineErrorMessage(
                        msg = activeError,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    if (hasActiveError && !isInspectionComplete) {
                        Button(
                            onClick = {
                                if (detectedEngine == EngineType.BITTORRENT) onInspectTorrent() else onInspectMedia()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                "🔄 Retry Inspect",
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = onAddDownload,
                        enabled = canStartDownload,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = when {
                                isLoading -> "Starting..."
                                isInspecting -> "Inspecting..."
                                hasActiveError -> "Inspection Failed"
                                !isUrlValid -> "Enter URL"
                                !isInspectionComplete && detectedEngine == EngineType.MEDIA_STREAM -> "Inspect Video First"
                                !isInspectionComplete && detectedEngine == EngineType.BITTORRENT -> "Inspect Torrent First"
                                !isClipRangeValid -> "Invalid Time Range"
                                inspectedPlaylist != null && detectedEngine == EngineType.MEDIA_STREAM -> {
                                    val n = inspectedPlaylist.entries.count { it.isSelected }
                                    if (scheduleStartMs > 0L) "Schedule $n videos" else "Download $n videos"
                                }

                                scheduleStartMs > 0L -> "Schedule Download"
                                else -> "Start Download"
                            },
                            // On the cyan primary button the readable text colour is onPrimary
                            // (black in the dark theme), not onSurface (white).
                            color = if (canStartDownload) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.6f
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectionLoadingBanner(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun InlineErrorMessage(msg: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠️", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuickFolderButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EngineChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.5f
        )
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = 0.6f
        )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = if (isSelected) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MediaQualitiesSelectionCard(
    qualities: List<MediaQualityEntry>,
    onQualitySelectionToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.35f
            )
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val videoTitle = qualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle
            val isAudio = qualities.any {
                it.qualityLabel.contains("Audio", ignoreCase = true) ||
                        it.format in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
            }
            val titleIcon = if (isAudio) "🎵" else "🎬"
            val defaultTitle = if (isAudio) "Available Audio Formats" else "Available Qualities"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!videoTitle.isNullOrBlank()) "$titleIcon $videoTitle" else "$titleIcon $defaultTitle",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onSelectAll(true) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Select All",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = { onSelectAll(false) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Clear",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "Selected: ${qualities.count { it.isSelected }} of ${qualities.size} available",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(qualities, key = { it.index }) { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (quality.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.4f
                                    ) else Color.Transparent
                                )
                                .clickable {
                                    onQualitySelectionToggled(
                                        quality.index,
                                        !quality.isSelected
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = quality.isSelected,
                                onCheckedChange = { checked ->
                                    onQualitySelectionToggled(
                                        quality.index,
                                        checked
                                    )
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF0284C7),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quality.qualityLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val sizeText =
                                    if (quality.size > 0) " • ${formatSizeDisplay(quality.size)}" else ""
                                val durText = if (quality.durationSeconds > 0) " • ⏱️ ${
                                    formatSecondsToDisplayFriendly(quality.durationSeconds)
                                }" else ""
                                Text(
                                    text = "Format: ${quality.format.uppercase()}$sizeText$durText",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentFilesSelectionCard(
    torrentName: String?,
    files: List<TorrentFileEntry>,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.35f
            )
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = torrentName ?: "Torrent Files",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = {
                        val allSelected = files.all { it.isSelected }
                        onSelectAll(!allSelected)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (files.all { it.isSelected }) "Deselect All" else "Select All",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(files, key = { it.index }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (file.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.4f
                                    ) else Color.Transparent
                                )
                                .clickable { onFileSelectionToggled(file.index, !file.isSelected) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = file.isSelected,
                                onCheckedChange = { checked ->
                                    onFileSelectionToggled(
                                        file.index,
                                        checked
                                    )
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF0284C7),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.path,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(file.size),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            val priorityLabel = when (file.priority) {
                                1 -> "High"
                                -1 -> "Low"
                                else -> "Normal"
                            }
                            val priorityColor = when (file.priority) {
                                1 -> Color(0xFFEF4444)
                                -1 -> Color(0xFF3B82F6)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                onClick = {
                                    val nextPriority = when (file.priority) {
                                        0 -> 1
                                        1 -> -1
                                        else -> 0
                                    }
                                    onFilePriorityChanged(file.index, nextPriority)
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = priorityColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = priorityLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = priorityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTimeChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** 42:44 / 1:05:09 - a clock reading, so 42 minutes is never shown as "00:42". */
private fun clockText(totalSeconds: Long): String {
    val t = totalSeconds.coerceAtLeast(0L)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/** One of the two clip download choices: a card, highlighted when chosen. */
@Composable
private fun ClipModeOption(
    selected: Boolean,
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.25f
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selected) "◉" else "○",
                    fontSize = 14.sp,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            lines.forEach { line ->
                Text(
                    text = "• $line",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
