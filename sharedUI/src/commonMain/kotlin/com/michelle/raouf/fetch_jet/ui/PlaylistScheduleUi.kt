package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule
import com.michelle.raouf.fetch_jet.core.util.nextLocalTimeEpochMs
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality

/** The videos of a playlist with checkboxes and one quality for all of them. */
@Composable
fun PlaylistSelectionCard(
    playlist: MediaPlaylist,
    quality: PlaylistQuality,
    onEntryToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onQualityChanged: (PlaylistQuality) -> Unit,
    onUseSingleVideo: (() -> Unit)?
) {
    val selectedCount = playlist.entries.count { it.isSelected }
    val allSelected = selectedCount == playlist.entries.size
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📃", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Playlist · ${playlist.entries.size} videos · $selectedCount selected",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(if (allSelected) "Select none" else "Select all", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Quality for all videos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlaylistQuality.entries.forEach { q ->
                    ChoiceChip(
                        label = q.label,
                        selected = q == quality,
                        onClick = { onQualityChanged(q) })
                }
            }
            Text(
                text = "If a video has no such quality, the closest lower one is taken.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(playlist.entries, key = { it.index }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryToggled(entry.index, !entry.isSelected) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entry.isSelected,
                                onCheckedChange = { onEntryToggled(entry.index, it) })
                            Text(
                                text = "${entry.index + 1}.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                text = entry.title,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (entry.durationSeconds > 0L) {
                                Text(
                                    text = clock(entry.durationSeconds),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (onUseSingleVideo != null) {
                TextButton(onClick = onUseSingleVideo, modifier = Modifier.align(Alignment.End)) {
                    Text("Download only this video instead", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * When the download(s) start: now (default), in 30 min / 1 h, or at a time of day.
 * The chosen moment is shown in words ("tomorrow 02:00").
 */
@Composable
fun ScheduleStartSection(scheduleStartMs: Long, onScheduleChanged: (Long) -> Unit) {
    var showTimeFields by remember { mutableStateOf(false) }
    var hourText by remember { mutableStateOf("02") }
    var minuteText by remember { mutableStateOf("00") }

    Text(
        "When to start",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChoiceChip("Now", selected = scheduleStartMs == 0L && !showTimeFields) {
            showTimeFields = false
            onScheduleChanged(0L)
        }
        ChoiceChip("In 30 min", selected = false) {
            showTimeFields = false
            onScheduleChanged(currentEpochMs() + 30 * 60_000L)
        }
        ChoiceChip("In 1 hour", selected = false) {
            showTimeFields = false
            onScheduleChanged(currentEpochMs() + 60 * 60_000L)
        }
        ChoiceChip("At a time…", selected = showTimeFields) { showTimeFields = true }
    }

    if (showTimeFields) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { v -> hourText = v.filter { it.isDigit() }.take(2) },
                label = { Text("Hour", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp)
            )
            Text(" : ", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = minuteText,
                onValueChange = { v -> minuteText = v.filter { it.isDigit() }.take(2) },
                label = { Text("Minute", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    hourText = h.toString().padStart(2, '0')
                    minuteText = m.toString().padStart(2, '0')
                    onScheduleChanged(nextLocalTimeEpochMs(h, m))
                },
                shape = RoundedCornerShape(10.dp)
            ) { Text("Set", fontSize = 12.sp) }
        }
        Text(
            text = "24-hour clock (e.g. 02:00 = 2 AM, 14:30 = 2:30 PM).",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (scheduleStartMs > 0L) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🕑 Starts ${formatLocalSchedule(scheduleStartMs)} (FetchJet must be running)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(
            alpha = 0.6f
        ),
        border = BorderStroke(
            1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun clock(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
