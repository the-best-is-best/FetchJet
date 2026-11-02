package com.michelle.raouf.fetch_jet.ui

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.michelle.raouf.fetch_jet.core.util.localTimeOnDayEpochMs
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
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
                        text = LocalizationManager.Strings.playlistSummary(
                            playlist.entries.size,
                            selectedCount
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(
                        if (allSelected) LocalizationManager.Strings.selectNone else LocalizationManager.Strings.selectAll,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                LocalizationManager.Strings.qualityForAllVideos,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
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
                text = LocalizationManager.Strings.qualityForAllVideosHint,
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
                    Text(LocalizationManager.Strings.downloadOnlyThisVideo, fontSize = 12.sp)
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
    var hourText by remember { mutableStateOf("09") }
    var minuteText by remember { mutableStateOf("00") }
    var isPm by remember { mutableStateOf(false) }
    var dayOffset by remember { mutableStateOf(0) }
    var timeError by remember { mutableStateOf<String?>(null) }

    Text(
        LocalizationManager.Strings.whenToStart,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChoiceChip(
            LocalizationManager.Strings.scheduleNow,
            selected = scheduleStartMs == 0L && !showTimeFields
        ) {
            showTimeFields = false
            onScheduleChanged(0L)
        }
        ChoiceChip(LocalizationManager.Strings.scheduleIn30Min, selected = false) {
            showTimeFields = false
            onScheduleChanged(currentEpochMs() + 30 * 60_000L)
        }
        ChoiceChip(LocalizationManager.Strings.scheduleIn1Hour, selected = false) {
            showTimeFields = false
            onScheduleChanged(currentEpochMs() + 60 * 60_000L)
        }
        ChoiceChip(LocalizationManager.Strings.scheduleAtTime, selected = showTimeFields) {
            if (!showTimeFields) {
                // Start from the current local time; the user moves it forward from there.
                val minuteOfDay =
                    ((currentEpochMs() + localUtcOffsetMs()).mod(24L * 60 * 60 * 1000) / 60_000L).toInt()
                val h24 = minuteOfDay / 60
                hourText = (if (h24 % 12 == 0) 12 else h24 % 12).toString().padStart(2, '0')
                minuteText = (minuteOfDay % 60).toString().padStart(2, '0')
                isPm = h24 >= 12
                dayOffset = 0
                timeError = null
            }
            showTimeFields = true
        }
    }

    if (showTimeFields) {
        // 12-hour clock with AM / PM and a day: "01:49 PM, today" is what people mean;
        // a 24-hour "01:49" silently became 1:49 AM tomorrow.
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            LocalizationManager.Strings.scheduleDayLabel,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                0 to LocalizationManager.Strings.scheduleToday,
                1 to LocalizationManager.Strings.scheduleTomorrow,
                2 to LocalizationManager.Strings.scheduleIn2Days,
                3 to LocalizationManager.Strings.scheduleIn3Days,
                7 to LocalizationManager.Strings.scheduleInAWeek
            )
                .forEach { (d, label) ->
                    ChoiceChip(label, selected = dayOffset == d) {
                        dayOffset = d
                    }
                }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Two rows on phones: [hour : minute  AM PM] / [Set]; one row when there is room.
        val setTime: () -> Unit = {
            val h12 = hourText.toIntOrNull()?.coerceIn(1, 12) ?: 12
            val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
            hourText = h12.toString().padStart(2, '0')
            minuteText = m.toString().padStart(2, '0')
            val h24 = (h12 % 12) + if (isPm) 12 else 0
            val at = localTimeOnDayEpochMs(dayOffset, h24, m)
            if (at <= currentEpochMs()) {
                timeError =
                    LocalizationManager.Strings.scheduleTimePassed
            } else {
                timeError = null
                onScheduleChanged(at)
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 420.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FetchJetTextField(
                        value = hourText,
                        onValueChange = { v -> hourText = v.filter { it.isDigit() }.take(2) },
                        label = LocalizationManager.Strings.scheduleHourLabel,
                        centered = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(if (compact) 72.dp else 80.dp)
                    )
                    Text(" : ", fontWeight = FontWeight.Bold)
                    FetchJetTextField(
                        value = minuteText,
                        onValueChange = { v -> minuteText = v.filter { it.isDigit() }.take(2) },
                        label = LocalizationManager.Strings.scheduleMinuteLabel,
                        centered = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(if (compact) 72.dp else 80.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ChoiceChip(LocalizationManager.Strings.scheduleAm, selected = !isPm) {
                        isPm = false
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    ChoiceChip(LocalizationManager.Strings.schedulePm, selected = isPm) {
                        isPm = true
                    }
                    if (!compact) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(onClick = setTime, shape = RoundedCornerShape(10.dp)) {
                            Text(LocalizationManager.Strings.scheduleSet, fontSize = 12.sp)
                        }
                    }
                }
                if (compact) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = setTime,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) { Text(LocalizationManager.Strings.scheduleSetTime, fontSize = 12.sp) }
                }
            }
        }
        timeError?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (scheduleStartMs > 0L) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🕑 ${
                    LocalizationManager.Strings.scheduleStartsAt(
                        formatLocalSchedule(
                            scheduleStartMs
                        )
                    )
                }",
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

/** Connections for a direct download: Auto (app setting) or a fixed count. */
@Composable
fun ConnectionsSection(selected: Int, appDefault: Int, onChanged: (Int) -> Unit) {
    Text(
        LocalizationManager.Strings.addConnectionsTitle,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val autoLabel =
            if (appDefault > 0) LocalizationManager.Strings.connectionsAutoWithDefault(appDefault) else LocalizationManager.Strings.connectionsAuto
        ChoiceChip(autoLabel, selected = selected == 0) { onChanged(0) }
        listOf(1, 4, 8, 16, 32).forEach { n ->
            ChoiceChip(n.toString(), selected = selected == n) { onChanged(n) }
        }
    }
}
