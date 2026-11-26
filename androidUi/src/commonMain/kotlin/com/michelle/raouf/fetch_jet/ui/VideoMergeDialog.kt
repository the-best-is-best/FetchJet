package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.feature.video_merge.VideoMerge
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoMergeItem
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoMergeUi
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.ltr
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun VideoMergeDialog(ui: VideoMergeUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val dismiss = { onEvent(DashboardUiEvent.OnDismissMerge) }
    val saved = !ui.failed && ui.lastPath.isNotBlank()
    val totalBytes = ui.items.sumOf { it.bytes.coerceAtLeast(0L) }
    val totalMs = ui.items.sumOf { it.durationMs }
    val subtitle = buildList {
        add(s.homeSelectedCount(ui.items.size))
        if (totalBytes > 0L) add(formatBytes(totalBytes))
        if (totalMs > 0L) add("⏱ ${ltr(MediaPlayback.formatTime(totalMs))}")
    }.joinToString(" · ")
    CutDialogFrame(if (ui.running) s.mergeRunning else s.mergeTitle, subtitle, { if (!ui.running) dismiss() }) {
        if (ui.running) {
            MergeProgress(ui)
        } else {
            Text(s.mergeHint, fontSize = 12.sp, color = colors.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.items.forEachIndexed { index, item ->
                    MergeRow(
                        index = index,
                        item = item,
                        canUp = index > 0 && !saved,
                        canDown = index < ui.items.lastIndex && !saved,
                        onMove = { up -> onEvent(DashboardUiEvent.OnMergeMoveClicked(item.taskId, up)) }
                    )
                }
            }
            FetchJetTextField(
                value = ui.fileName,
                onValueChange = { onEvent(DashboardUiEvent.OnMergeNameChanged(it)) },
                label = s.mergeFileName,
                enabled = !saved,
                trailing = {
                    Text(
                        ".${ui.extension}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            )
            if (ui.checking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(s.mergeChecking, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                }
            }
            MergeMessage(ui)
        }
        if (!ui.running && ui.hasConflict) {
            FileOverwriteContent(
                fileName = ui.conflictName,
                folder = null,
                copyName = ui.copyName,
                onSaveAsCopy = { onEvent(DashboardUiEvent.OnMergeSaveAsCopyClicked) },
                onOverwrite = { onEvent(DashboardUiEvent.OnMergeOverwriteClicked) },
                onDismiss = { onEvent(DashboardUiEvent.OnMergeConflictDismissed) }
            )
        } else Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            when {
                ui.running -> OutlinedButton(
                    onClick = { onEvent(DashboardUiEvent.OnMergeCancelClicked) },
                    enabled = !ui.stopping,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
                ) {
                    Text(if (ui.stopping) s.videoCutStopping else s.cancel, color = colors.error, fontWeight = FontWeight.SemiBold)
                }
                saved -> TextButton(onClick = dismiss) { Text(s.close) }
                else -> {
                    TextButton(onClick = dismiss) { Text(s.cancel) }
                    Button(
                        enabled = ui.canStart,
                        shape = RoundedCornerShape(10.dp),
                        onClick = { onEvent(DashboardUiEvent.OnMergeStartClicked) }
                    ) { Text("⧉ ${s.mergeVideos}", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun MergeRow(
    index: Int,
    item: VideoMergeItem,
    canUp: Boolean,
    canDown: Boolean,
    onMove: (Boolean) -> Unit
) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val details = buildList {
        if (item.durationMs > 0L) add(ltr(MediaPlayback.formatTime(item.durationMs)))
        if (item.bytes > 0L) add(formatBytes(item.bytes))
    }.joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (item.notSame) colors.error.copy(alpha = 0.10f) else colors.surfaceVariant.copy(alpha = 0.35f),
        border = if (item.notSame) BorderStroke(1.dp, colors.error.copy(alpha = 0.7f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (item.notSame) colors.error.copy(alpha = 0.18f) else colors.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(28.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (item.notSame) "⚠" else "${index + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.notSame) colors.error else colors.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    item.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.notSame) colors.error else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (details.isNotEmpty()) {
                    Text(details, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            MoveButton("▲", s.mergeMoveUp, canUp) { onMove(true) }
            MoveButton("▼", s.mergeMoveDown, canDown) { onMove(false) }
        }
    }
}

@Composable
private fun MoveButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label }
    ) {
        Text(
            icon,
            fontSize = 14.sp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun MergeProgress(ui: VideoMergeUi) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${(ui.progress.coerceIn(0f, 1f) * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            VideoMerge.withExtension(ui.fileName, ui.extension),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp).widthIn(max = 260.dp)
        )
    }
    LinearProgressIndicator(
        progress = { ui.progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        com.michelle.raouf.fetch_jet.DetailStat(
            s.videoCutElapsed,
            com.michelle.raouf.fetch_jet.formatEta(ui.elapsedMs / 1000L),
            colors.onSurface,
            Modifier.weight(1f)
        )
        com.michelle.raouf.fetch_jet.DetailStat(
            s.videoCutLeft,
            if (ui.etaMs >= 0L) com.michelle.raouf.fetch_jet.formatEta((ui.etaMs + 999L) / 1000L) else "—",
            colors.onSurface,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun MergeMessage(ui: VideoMergeUi) {
    if (ui.message.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val saved = !ui.failed && ui.lastPath.isNotBlank()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (saved) "✓ " else "⚠️ ") + ui.message,
            color = if (saved) colors.primary else colors.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (saved) {
            TextButton(onClick = { openMerged(ui.lastPath) }) {
                Text("▶ ${LocalizationManager.Strings.playerPlayAll}", color = colors.primary)
            }
        }
    }
}

private fun openMerged(path: String) {
    val id = QueueManagerRepository.getInstance().allTasksFlow.value
        .firstOrNull { VideoMerge.sourcePath(it) == path }?.id.orEmpty()
    com.michelle.raouf.fetch_jet.openDownloadedFile(path, id)
}
