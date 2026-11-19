package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.SubtitlesUi
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitleKey
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesListText
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesNoteText
import com.michelle.raouf.fetch_jet.presentation.dashboard.subtitlesProgressText
import com.michelle.raouf.fetch_jet.presentation.dashboard.trackLabel
import com.tbib.kmp_localization.LocalizationManager

@Composable
fun SubtitlesDialog(page: SubtitlesUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val muted = colors.onSurfaceVariant
    Dialog(
        onDismissRequest = { onEvent(DashboardUiEvent.OnDismissSubtitles) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().clearFocusOnTapOutside(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.subtitlesAddButton, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(page.fileName, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    page.reading -> {
                        Text(s.subtitlesReading, color = muted, fontSize = 13.sp)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    page.readFailed -> Text(s.subtitlesReadFailed, color = colors.error, fontSize = 13.sp)
                    page.tracks.isEmpty() -> Text(s.subtitlesNone, color = muted, fontSize = 13.sp)
                    else -> {
                        Text(subtitlesNoteText(page), color = muted, fontSize = 13.sp)
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                        ) {
                            page.tracks.forEach { track ->
                                val key = subtitleKey(track)
                                val there = key in page.present
                                val enabled = !there && !page.running
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(enabled = enabled) {
                                            onEvent(DashboardUiEvent.OnSubtitleTrackToggled(key, !track.isSelected))
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = track.isSelected || there,
                                        onCheckedChange = { onEvent(DashboardUiEvent.OnSubtitleTrackToggled(key, it)) },
                                        enabled = enabled
                                    )
                                    Text(
                                        trackLabel(track),
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (there) Text(s.subtitlesAlreadyThere, color = muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                if (page.running) {
                    Text(subtitlesProgressText(page.done, page.total), color = muted, fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = { if (page.total > 0) page.done.toFloat() / page.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (page.added.isNotEmpty()) Text(subtitlesListText(s.subtitlesAddedList, page.added), fontSize = 13.sp, color = colors.primary)
                if (page.refused.isNotEmpty()) Text(subtitlesListText(s.subtitlesRefusedList, page.refused), fontSize = 13.sp, color = colors.error)
                if (page.failed.isNotEmpty()) Text(subtitlesListText(s.subtitlesFailedList, page.failed), fontSize = 13.sp, color = colors.error)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = { onEvent(DashboardUiEvent.OnDismissSubtitles) },
                        enabled = !page.running
                    ) { Text(s.close) }
                    Button(
                        onClick = { onEvent(DashboardUiEvent.OnSubtitlesAddClicked) },
                        enabled = page.canAdd
                    ) { Text(s.subtitlesAddSelected) }
                }
            }
        }
    }
}
