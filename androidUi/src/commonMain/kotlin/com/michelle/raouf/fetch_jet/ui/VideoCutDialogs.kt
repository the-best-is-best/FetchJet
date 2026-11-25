package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.feature.video_cut.CutClip
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutMode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoCutUi
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSecondsToHhMm
import com.michelle.raouf.fetch_jet.utils.parseHhMmToSeconds
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun VideoCutHost(ui: VideoCutUi, onEvent: (DashboardUiEvent) -> Unit) {
    when {
        ui.clipsOnly -> VideoClipsDialog(ui, onEvent)
        ui.running || ui.failed -> VideoCutProgressDialog(ui, onEvent)
        ui.mode == null -> VideoCutChooserDialog(ui, onEvent)
        ui.mode == VideoCutMode.TIME -> VideoCutTimeDialog(ui, onEvent)
        ui.mode == VideoCutMode.TIMELINE -> {
            val path = ui.sourcePath.ifBlank {
                QueueManagerRepository.getInstance().allTasksFlow.value
                    .firstOrNull { it.id == ui.taskId }?.let { VideoCut.sourcePath(it) }.orEmpty()
            }
            if (path.isBlank()) onEvent(DashboardUiEvent.OnDismissVideoCut)
            else VideoTrimScreen(ui, path, onEvent)
        }
    }
}

@Composable
private fun VideoClipsDialog(ui: VideoCutUi, onEvent: (DashboardUiEvent) -> Unit) {
    CutDialogFrame(LocalizationManager.Strings.videoCutClips, ui.fileName, { onEvent(DashboardUiEvent.OnDismissVideoCut) }) {
        VideoClipsSection(
            clips = ui.clips,
            onPlay = { openClip(it.path) },
            onDelete = { onEvent(DashboardUiEvent.OnVideoCutDeleteClip(it.path)) }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onEvent(DashboardUiEvent.OnDismissVideoCut) }) { Text(LocalizationManager.Strings.close) }
        }
    }
}

@Composable
private fun CutDialogFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun VideoCutChooserDialog(ui: VideoCutUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    CutDialogFrame(LocalizationManager.Strings.videoCutTitle, ui.fileName, { onEvent(DashboardUiEvent.OnDismissVideoCut) }) {
        CutChoiceCard(
            icon = "⏱",
            title = LocalizationManager.Strings.videoCutByTime,
            hint = LocalizationManager.Strings.videoCutByTimeHint,
            onClick = { onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME)) }
        )
        CutChoiceCard(
            icon = "🎞",
            title = LocalizationManager.Strings.videoCutOnTimeline,
            hint = if (ui.timelineAvailable) LocalizationManager.Strings.videoCutOnTimelineHint
            else LocalizationManager.Strings.videoCutTimelineNeedsMp4,
            enabled = ui.timelineAvailable,
            onClick = { onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIMELINE)) }
        )
        CutMessage(ui)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onEvent(DashboardUiEvent.OnDismissVideoCut) }) { Text(s.cancel) }
        }
    }
}

@Composable
private fun CutChoiceCard(icon: String, title: String, hint: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 22.sp)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(hint, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Text(
                if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "‹" else "›",
                fontSize = 22.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoCutTimeDialog(ui: VideoCutUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val durationSec = ui.durationMs / 1000L
    var startSec by remember(ui.taskId, ui.durationMs) { mutableLongStateOf(ui.startMs / 1000L) }
    var endSec by remember(ui.taskId, ui.durationMs) {
        mutableLongStateOf(if (ui.endMs > 0L) (ui.endMs + 999L) / 1000L else durationSec)
    }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val startAfterEnd = endSec <= startSec
    val endTooLong = durationSec > 0L && endSec > durationSec
    val error = when {
        startAfterEnd -> s.clipStartAfterEnd(formatSecondsToHhMm(startSec), formatSecondsToHhMm(endSec))
        endTooLong -> s.clipEndExceedsVideo(formatSecondsToHhMm(endSec), formatSecondsToHhMm(durationSec))
        else -> null
    }
    CutDialogFrame(LocalizationManager.Strings.videoCutByTime, ui.fileName, { onEvent(DashboardUiEvent.OnDismissVideoCut) }) {
        if (durationSec > 0L) {
            Text(
                "⏱ ${clockText(durationSec)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ClipRangeCards(
            startText = formatSecondsToHhMm(startSec),
            endText = formatSecondsToHhMm(endSec),
            startError = startAfterEnd,
            endError = startAfterEnd || endTooLong,
            onStartClick = { pickStart = true },
            onEndClick = { pickEnd = true }
        )
        if (error != null) Text("⚠️ $error", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        CutMessage(ui)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            TextButton(onClick = { onEvent(DashboardUiEvent.OnDismissVideoCut) }) { Text(s.cancel) }
            Button(
                enabled = error == null && !ui.reading && ui.durationMs > 0L,
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    onEvent(DashboardUiEvent.OnVideoCutRangeChanged(startSec * 1000L, minOf(endSec * 1000L, ui.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)))
                    onEvent(DashboardUiEvent.OnVideoCutStartClicked)
                }
            ) { Text("✂ ${LocalizationManager.Strings.videoCutApply}", fontWeight = FontWeight.Bold) }
        }
    }
    if (pickStart) {
        TimeRangePickerDialog(
            title = "⏱️ ${s.setStartTimeTitle}",
            initialHhMm = formatSecondsToHhMm(startSec),
            maxDurationSeconds = (endSec - 1L).coerceAtLeast(0L),
            onTimeSelected = { startSec = parseHhMmToSeconds(it); pickStart = false },
            onDismissRequest = { pickStart = false }
        )
    }
    if (pickEnd) {
        TimeRangePickerDialog(
            title = "⏱️ ${s.setEndTimeTitle}",
            initialHhMm = formatSecondsToHhMm(endSec),
            maxDurationSeconds = durationSec,
            minTimeSeconds = startSec,
            onTimeSelected = { endSec = parseHhMmToSeconds(it); pickEnd = false },
            onDismissRequest = { pickEnd = false }
        )
    }
}

@Composable
private fun VideoCutProgressDialog(ui: VideoCutUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    CutDialogFrame(
        if (ui.failed) LocalizationManager.Strings.videoCutFailed else LocalizationManager.Strings.videoCutRunning,
        ui.fileName,
        { if (!ui.running) onEvent(DashboardUiEvent.OnDismissVideoCut) }
    ) {
        if (ui.running) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(ui.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    com.michelle.raouf.fetch_jet.utils.ltr("${formatTrimTime(ui.startMs).substringBeforeLast('.')} – ${formatTrimTime(ui.endMs).substringBeforeLast('.')}"),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
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
                    LocalizationManager.Strings.videoCutElapsed,
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
        } else if (ui.message.isNotBlank()) {
            Text(ui.message, color = colors.error, fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (ui.running) {
                OutlinedButton(
                    onClick = { onEvent(DashboardUiEvent.OnVideoCutCancelClicked) },
                    enabled = !ui.stopping,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
                ) {
                    Text(
                        if (ui.stopping) s.videoCutStopping else s.cancel,
                        color = colors.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                TextButton(onClick = { onEvent(DashboardUiEvent.OnDismissVideoCut) }) { Text(s.close) }
            }
        }
    }
}

internal fun openClip(path: String) {
    val id = QueueManagerRepository.getInstance().allTasksFlow.value
        .firstOrNull { VideoCut.isClip(it) && VideoCut.sourcePath(it) == path }?.id.orEmpty()
    com.michelle.raouf.fetch_jet.openDownloadedFile(path, id)
}

@Composable
internal fun CutMessage(ui: VideoCutUi, dark: Boolean = false) {
    if (ui.message.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val saved = !ui.failed && ui.message == LocalizationManager.Strings.videoCutSaved
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (saved) "✓ " else "⚠️ ") + ui.message,
            color = if (saved) colors.primary else colors.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (saved && ui.lastClipPath.isNotBlank()) TextButton(onClick = { openClip(ui.lastClipPath) }) { Text("▶ ${LocalizationManager.Strings.playerPlayAll}", color = if (dark) androidx.compose.ui.graphics.Color.White else colors.primary) }
    }
}

@Composable
internal fun VideoClipsSection(
    clips: List<CutClip>,
    onPlay: (CutClip) -> Unit,
    onDelete: (CutClip) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        clips.forEach { clip ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.clickable { onPlay(clip) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎞", fontSize = 16.sp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            clip.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${com.michelle.raouf.fetch_jet.utils.ltr("${formatTrimTime(clip.startMs)} – ${formatTrimTime(clip.endMs)}")} · ${formatBytes(clip.bytes)}",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = { onDelete(clip) }) {
                        Text(LocalizationManager.Strings.videoCutDeleteClip, color = colors.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
