package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.domain.model.canSendToDevice
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.theme.EngineBitTorrent
import com.michelle.raouf.fetch_jet.theme.EngineMediaStream
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.michelle.raouf.fetch_jet.utils.decodeUnicodeText
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun DownloadTaskItem(
    task: DownloadTask,
    speedDisplayUnit: String = "MB/s",
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onShare: (() -> Unit)? = null
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
                    EngineType.MEDIA_STREAM -> DetailChip(
                        LocalizationManager.Strings.detailsChipMedia,
                        EngineMediaStream
                    )

                    EngineType.BITTORRENT -> DetailChip(
                        LocalizationManager.Strings.detailsChipTorrent,
                        EngineBitTorrent
                    )

                    EngineType.HTTP_DIRECT -> DetailChip(
                        LocalizationManager.Strings.detailsChipFile,
                        MaterialTheme.colorScheme.primary
                    )
                }
                if (!isCompleted) {
                    when (task.canResume()) {
                        true -> DetailChip(
                            LocalizationManager.Strings.detailsChipResumable,
                            StatusCompleted
                        )

                        false -> DetailChip(
                            LocalizationManager.Strings.detailsChipNoResume,
                            StatusPaused
                        )
                        null -> {}
                    }
                }
                if (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()) {
                    // Fast clip mode downloads the whole video first, then cuts it: say so,
                    // otherwise the full video's size on a clip looks like a bug.
                    DetailChip(
                        LocalizationManager.Strings.detailsClipChip(
                            task.clipStart ?: "00:00",
                            task.clipEnd,
                            task.clipByCutting && !isCompleted
                        ),
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
                        text = "📁 ${
                            LocalizationManager.Strings.detailsTorrentFiles(
                                task.torrentFiles.count { it.isSelected },
                                task.torrentFiles.size
                            )
                        } ${if (showFiles) "▲" else "▼"}",
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
                                        1 -> LocalizationManager.Strings.detailsPriorityHigh to MaterialTheme.colorScheme.primary
                                        -1 -> LocalizationManager.Strings.detailsPriorityLow to MaterialTheme.colorScheme.error
                                        else -> LocalizationManager.Strings.detailsPriorityNormal to MaterialTheme.colorScheme.onSurfaceVariant
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
                                DownloadStatus.COMPLETED -> LocalizationManager.Strings.detailsStatusCompleted
                                DownloadStatus.PAUSED -> LocalizationManager.Strings.detailsStatusPaused
                                DownloadStatus.ERROR -> LocalizationManager.Strings.detailsStatusFailed
                                else -> if (task.waitingInQueue) LocalizationManager.Strings.detailsStatusWaiting else ""
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
                                text = "⚡ ${localizeStep(task.currentStepText.orEmpty())}",
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
                            ) else LocalizationManager.Strings.detailsUnknownSize
                        }"

                        val speedText = if (task.status == DownloadStatus.DOWNLOADING) {
                            if (task.postProcessLabel != null) {
                                "${task.postProcessLabel?.let { localizeStep(it) }}..."
                            } else if (task.currentStepText?.contains(
                                    "Merging",
                                    ignoreCase = true
                                ) == true
                            ) {
                                LocalizationManager.Strings.detailsMergingAudioVideo
                            } else if (task.engineType == EngineType.BITTORRENT && task.disableUpload) {
                                LocalizationManager.Strings.detailsTorrentSpeedUploadDisabled(
                                    formatSpeed(
                                        task.downloadSpeedBytesPerSec,
                                        speedDisplayUnit
                                    )
                                )
                            } else if (task.engineType == EngineType.BITTORRENT) {
                                LocalizationManager.Strings.detailsTorrentSpeed(
                                    formatSpeed(
                                        task.downloadSpeedBytesPerSec,
                                        speedDisplayUnit
                                    ),
                                    formatSpeed(
                                        task.uploadSpeedBytesPerSec,
                                        speedDisplayUnit
                                    )
                                )
                            } else {
                                formatSpeed(task.downloadSpeedBytesPerSec, speedDisplayUnit)
                            }
                        } else if (task.status == DownloadStatus.CONNECTING) {
                            LocalizationManager.Strings.detailsConnecting
                        } else if (task.waitingInQueue) {
                            if (task.scheduledAtMs > com.michelle.raouf.fetch_jet.core.util.currentEpochMs())
                                "🕑 ${
                                    LocalizationManager.Strings.detailsStartsAt(
                                        com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule(
                                            task.scheduledAtMs
                                        )
                                    )
                                }"
                            else if (task.queuePosition > 0) "⏳ ${
                                LocalizationManager.Strings.detailsWaitingInQueuePosition(
                                    task.queuePosition
                                )
                            }"
                            else "⏳ ${LocalizationManager.Strings.detailsWaitingInQueue}"
                        } else {
                            when (task.status) {
                                DownloadStatus.COMPLETED -> LocalizationManager.Strings.detailsStatusCompleted
                                DownloadStatus.PAUSED -> LocalizationManager.Strings.detailsStatusPaused
                                DownloadStatus.ERROR -> LocalizationManager.Strings.detailsStatusFailed
                                DownloadStatus.QUEUED -> LocalizationManager.Strings.detailsStatusQueued
                                else -> LocalizationManager.Strings.detailsStatusNotStarted
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
                                LocalizationManager.Strings.detailsDownloaded,
                                bytesText,
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                            DetailStat(
                                if (task.status == DownloadStatus.DOWNLOADING) LocalizationManager.Strings.detailsSpeed else LocalizationManager.Strings.detailsState,
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
                                LocalizationManager.Strings.detailsTimeLeft,
                                if (eta != null) formatEta(eta) else "—",
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                            DetailStat(
                                LocalizationManager.Strings.detailsAdded,
                                formatAddedTime(task.createdAtEpochMs),
                                MaterialTheme.colorScheme.onSurface,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Pieces (IDM-style): one segment per piece, filled as it downloads.
            if (task.chunks.size > 1 && task.status != DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                com.michelle.raouf.fetch_jet.ui.PiecesBar(
                    task = task,
                    speedDisplayUnit = speedDisplayUnit
                )
            }

            if (task.status == DownloadStatus.ERROR && !task.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ ${localizeStep(task.errorMessage.orEmpty())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = LocalizationManager.Strings.detailsSavedTo,
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

            val clipboard = LocalClipboardManager.current
            listOf(
                LocalizationManager.Strings.detailsSource to task.url,
                LocalizationManager.Strings.detailsPage to (task.requestHeaders["Referer"]
                    ?: task.originalUrl.orEmpty())
            ).filter { (_, link) -> link.isNotBlank() }
                .distinctBy { (_, link) -> link }
                .forEach { (label, link) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = LocalizationManager.Strings.copy,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    runCatching {
                                        clipboard.setText(
                                            androidx.compose.ui.text.AnnotatedString(
                                                link
                                            )
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = link,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }

            if (!task.expectedChecksum.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val verdictIcon: String
                val verdictText: String
                val verdictColor: Color
                when (task.checksumVerified) {
                    true -> {
                        verdictIcon = "✅"
                        verdictText = LocalizationManager.Strings.checksumVerified
                        verdictColor = Color(0xFF2E7D32)
                    }

                    false -> {
                        verdictIcon = "❌"
                        verdictText = LocalizationManager.Strings.checksumMismatch
                        verdictColor = MaterialTheme.colorScheme.error
                    }

                    null -> {
                        verdictIcon = "⏳"
                        verdictText = LocalizationManager.Strings.checksumChecking
                        verdictColor = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Text(
                    text = "$verdictIcon $verdictText",
                    style = MaterialTheme.typography.bodySmall,
                    color = verdictColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

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
                        DetailPrimaryButton(
                            LocalizationManager.Strings.detailsOpenFile,
                            m
                        ) { openDownloadedFile(fullPath) }
                        if (canFolder) DetailSecondaryButton(
                            LocalizationManager.Strings.detailsOpenFolder,
                            m,
                            openFolder
                        )
                    }

                    running -> {
                        DetailPrimaryButton(LocalizationManager.Strings.pause, m, pauseClicked)
                        if (canFolder) DetailSecondaryButton(
                            LocalizationManager.Strings.detailsOpenFolder,
                            m,
                            openFolder
                        )
                    }

                    task.waitingInQueue -> {
                        DetailPrimaryButton(
                            LocalizationManager.Strings.detailsStartNow,
                            m,
                            onResume
                        )
                        DetailSecondaryButton(LocalizationManager.Strings.pause, m, onPause)
                    }

                    else -> {
                        DetailPrimaryButton(
                            if (task.status == DownloadStatus.ERROR) LocalizationManager.Strings.retry else LocalizationManager.Strings.resume,
                            m,
                            onResume
                        )
                        if (canFolder) DetailSecondaryButton(
                            LocalizationManager.Strings.detailsOpenFolder,
                            m,
                            openFolder
                        )
                    }
                }
            }
            if (onShare != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text(
                        LocalizationManager.Strings.sysDeviceSendTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    if (isCompleted) LocalizationManager.Strings.detailsRemoveFromList else LocalizationManager.Strings.detailsCancelDownload,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Small soft label in the details: tinted text on a faint tint, no border. */
@Composable
internal fun DetailChip(text: String, color: Color) {
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
internal fun DetailPrimaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
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
internal fun DetailSecondaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
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

/** 45s · 12m 05s · 1h 07m */
internal fun formatEta(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60L -> "${s}s"
        s < 3600L -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        s < 100L * 3600L -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
        else -> "> 99h"
    }
}

/**
 * The post-processing progress line under a task's main bar: label, percentage (or a moving bar
 * when the length is unknown) and the time the step has been running.
 */
@Composable
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun PostProcessLine(label: String, progress: Float, startedAtMs: Long) {
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
                text = "$icon ${localizeStep(label)}",
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
internal fun TaskDetailsPane(
    task: DownloadTask,
    viewModel: DashboardViewModel,
    speedDisplayUnit: String,
    fullScreen: Boolean,
    onClose: () -> Unit,
    /** Take all the height offered (side pane, expanded sheet) instead of wrapping the content. */
    fillHeight: Boolean = true
) {
    val outer = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    Column(modifier = outer.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullScreen) {
                IconButton(onClick = onClose) { Text("←", fontSize = 20.sp) }
            }
            Text(
                text = if (fullScreen) LocalizationManager.Strings.detailsHeaderDownload else LocalizationManager.Strings.detailsHeaderDetails,
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
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
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
                },
                onShare = if (task.canSendToDevice()) {
                    { viewModel.onEvent(DashboardUiEvent.OnShareTaskClicked(task.id)) }
                } else null
            )
        }
    }
}

@Composable
internal fun DetailStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
internal fun formatAddedTime(epochMs: Long): String {
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
        0L -> LocalizationManager.Strings.detailsAddedToday(clock)
        1L -> LocalizationManager.Strings.detailsAddedYesterday(clock)
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
