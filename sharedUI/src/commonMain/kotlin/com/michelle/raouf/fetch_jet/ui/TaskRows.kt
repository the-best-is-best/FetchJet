package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.utils.decodeUnicodeText
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSpeed

/**
 * Compact list rows (the redesign): a download is one line - kind, name, state, progress,
 * speed, time left and one quick action. Everything else (all details and every action) is in
 * the details view the row opens.
 */

private val AudioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")

/** Short type tag shown in the row's square: VID, AUD, MAG, ZIP, ISO... */
fun taskKindTag(task: DownloadTask): String {
    val ext = task.fileName.substringAfterLast('.', "").lowercase()
    return when {
        task.engineType == EngineType.BITTORRENT -> "MAG"
        task.engineType == EngineType.MEDIA_STREAM -> if (ext in AudioExt) "AUD" else "VID"
        ext in AudioExt -> "AUD"
        ext in setOf("mp4", "mkv", "webm", "mov", "avi") -> "VID"
        ext.length in 2..4 && ext.all { it.isLetterOrDigit() } -> ext.uppercase()
        else -> "FILE"
    }
}

private fun kindColor(tag: String): Color = when (tag) {
    "VID" -> Color(0xFFC4B5FD)
    "AUD" -> Color(0xFF6EE7B7)
    "MAG" -> Color(0xFFF9A8D4)
    "ZIP", "RAR", "7Z" -> Color(0xFFFCD34D)
    "ISO", "IMG", "DMG" -> Color(0xFF93C5FD)
    "EXE", "MSI", "APK" -> Color(0xFFFDBA74)
    else -> Color(0xFFB7C0D8)
}

private fun etaText(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
    else -> "${seconds / 3600}h ${((seconds % 3600) / 60).toString().padStart(2, '0')}m"
}

/** The one-line state under the name. */
fun taskStateLine(task: DownloadTask, speedUnit: String): String {
    val pct = (task.progress.coerceIn(0f, 1f) * 100).toInt()
    if (task.waitingInQueue) {
        return if (task.scheduledAtMs > currentEpochMs()) "Starts ${formatLocalSchedule(task.scheduledAtMs)}"
        else if (task.queuePosition > 0) "Waiting in queue (#${task.queuePosition})" else "Waiting in queue"
    }
    // (A property of another module's class is not smart-cast: read it once.)
    val postLabel = task.postProcessLabel
    if (postLabel != null) return postLabel
    return when {
        task.status == DownloadStatus.CONNECTING -> "Connecting…"
        task.status == DownloadStatus.QUEUED -> "Starting…"
        task.status == DownloadStatus.DOWNLOADING -> buildString {
            append("$pct%")
            task.currentStepText?.takeIf { it.startsWith("Step") }
                ?.let { append(" · ").append(it.substringBefore(" (")) }
            if (task.downloadSpeedBytesPerSec > 0) append(" · ").append(
                formatSpeed(
                    task.downloadSpeedBytesPerSec,
                    speedUnit
                )
            )
            task.etaSeconds()?.let { append(" · ").append(etaText(it)).append(" left") }
        }

        task.status == DownloadStatus.PAUSED ->
            "Paused · $pct%" + if (task.canResume() == false) " · restarts on resume" else ""

        task.status == DownloadStatus.ERROR -> "Failed: " + (task.errorMessage ?: "error")
        task.status == DownloadStatus.COMPLETED ->
            "Completed" + if (task.totalBytes > 0) " · ${formatBytes(task.totalBytes)}" else ""

        else -> task.status.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun sizeText(task: DownloadTask): String = when {
    // "~" while yt-dlp only estimates the size (fragmented video)
    task.totalBytes > 0 && task.status == DownloadStatus.DOWNLOADING &&
            task.approximateTotalBytes == task.totalBytes -> "~" + formatBytes(task.totalBytes)

    task.totalBytes > 0 -> formatBytes(task.totalBytes)
    task.downloadedBytes > 0 -> formatBytes(task.downloadedBytes)
    else -> "—"
}

/** What the round button on a row does, and its label. */
enum class RowAction { PAUSE, RESUME, START_NOW, OPEN, NONE }

fun rowActionFor(task: DownloadTask): RowAction = when {
    task.waitingInQueue -> RowAction.START_NOW
    task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING ||
            task.status == DownloadStatus.QUEUED -> RowAction.PAUSE

    task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.ERROR -> RowAction.RESUME
    task.status == DownloadStatus.COMPLETED -> RowAction.OPEN
    else -> RowAction.NONE
}

@Composable
private fun barColor(task: DownloadTask): Color = when {
    task.status == DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
    task.status == DownloadStatus.COMPLETED -> Color(0xFF34D399)
    task.status == DownloadStatus.PAUSED || task.waitingInQueue -> Color(0xFFFBBF24)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun KindSquare(tag: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tag.take(4),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kindColor(tag)
        )
    }
}

@Composable
private fun ThinBar(fraction: Float, color: Color, height: Int = 5) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(height.dp))
                .background(color)
        )
    }
}

@Composable
private fun QuickActionButton(action: RowAction, sizeDp: Int, onClick: () -> Unit) {
    if (action == RowAction.NONE) {
        Spacer(modifier = Modifier.width(sizeDp.dp)); return
    }
    val label = when (action) {
        RowAction.PAUSE -> "Pause"
        RowAction.RESUME -> "Resume"
        RowAction.START_NOW -> "Start now"
        RowAction.OPEN -> "Open"
        RowAction.NONE -> ""
    }
    val glyph = when (action) {
        RowAction.PAUSE -> "⏸"
        RowAction.RESUME, RowAction.START_NOW -> "▶"
        RowAction.OPEN -> "↗"
        RowAction.NONE -> ""
    }
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * One download as a row. Wide screens get columns (size, progress, speed, time left); narrow
 * ones get a compact card with the state line and a bar underneath.
 */
@Composable
fun TaskRow(
    task: DownloadTask,
    selected: Boolean,
    speedDisplayUnit: String,
    onClick: () -> Unit,
    onQuickAction: (RowAction) -> Unit
) {
    val name = decodeUnicodeText(task.fileName.ifBlank {
        task.url.substringBefore('?').substringAfterLast('/')
    }).trim()
    val tag = taskKindTag(task)
    val action = rowActionFor(task)
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    val container = if (selected) accent.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface
    val border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val stateLine = taskStateLine(task, speedDisplayUnit)
    val stateColor = when (task.status) {
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 720.dp
        Surface(
            shape = shape,
            color = container,
            border = border,
            modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
        ) {
            if (wide) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KindSquare(tag, 34)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stateLine,
                            fontSize = 11.sp,
                            color = stateColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        sizeText(task),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp),
                        maxLines = 1
                    )
                    Column(modifier = Modifier.width(140.dp)) {
                        ThinBar(task.progress, barColor(task))
                        Text(
                            if (task.status == DownloadStatus.COMPLETED) "100%" else "${
                                (task.progress.coerceIn(
                                    0f,
                                    1f
                                ) * 100).toInt()
                            }%",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text(
                        if (task.status == DownloadStatus.DOWNLOADING && task.downloadSpeedBytesPerSec > 0)
                            formatSpeed(task.downloadSpeedBytesPerSec, speedDisplayUnit) else "—",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(84.dp), maxLines = 1
                    )
                    Text(
                        task.etaSeconds()?.let { etaText(it) } ?: "—",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp), maxLines = 1
                    )
                    QuickActionButton(action, 34) { onQuickAction(action) }
                }
            } else {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindSquare(tag, 40)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stateLine,
                                fontSize = 12.sp,
                                color = stateColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        QuickActionButton(action, 44) { onQuickAction(action) }
                    }
                    if (task.status != DownloadStatus.COMPLETED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ThinBar(task.progress, barColor(task))
                    }
                }
            }
        }
    }
}

/** Header of the wide list: the column names above the rows. */
@Composable
fun TaskRowsHeader() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 720.dp) return@BoxWithConstraints
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 58.dp, end = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "NAME",
                style = style,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "SIZE",
                style = style,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(76.dp)
            )
            Text(
                "PROGRESS",
                style = style,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(140.dp)
            )
            Text(
                "SPEED",
                style = style,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(84.dp)
            )
            Text(
                "LEFT",
                style = style,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp)
            )
            Spacer(modifier = Modifier.width(34.dp))
        }
    }
}

/** Sidebar filters of the wide layout: by state, then by type. */
enum class SideFilter(val label: String) {
    ALL("All downloads"),
    DOWNLOADING("Downloading"),
    WAITING("Waiting"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    MEDIA("Video & audio"),
    FILES("Files"),
    TORRENTS("Torrents");

    fun matches(t: DownloadTask): Boolean = when (this) {
        ALL -> true
        DOWNLOADING -> !t.waitingInQueue && (t.status == DownloadStatus.DOWNLOADING ||
                t.status == DownloadStatus.CONNECTING || t.status == DownloadStatus.QUEUED)

        WAITING -> t.waitingInQueue
        PAUSED -> !t.waitingInQueue && (t.status == DownloadStatus.PAUSED || t.status == DownloadStatus.IDLE)
        COMPLETED -> t.status == DownloadStatus.COMPLETED
        FAILED -> t.status == DownloadStatus.ERROR
        MEDIA -> t.engineType == EngineType.MEDIA_STREAM
        FILES -> t.engineType == EngineType.HTTP_DIRECT
        TORRENTS -> t.engineType == EngineType.BITTORRENT
    }
}
