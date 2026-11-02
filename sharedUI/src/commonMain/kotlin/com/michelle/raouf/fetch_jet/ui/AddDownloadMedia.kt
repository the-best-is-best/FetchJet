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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.isAllAudio
import com.michelle.raouf.fetch_jet.domain.model.isAudioOnly
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSecondsToDisplayFriendly
import com.michelle.raouf.fetch_jet.utils.formatSizeDisplay
import com.michelle.raouf.fetch_jet.utils.parseHhMmToSeconds
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun MediaQualitiesSelectionCard(
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
            val isAudio = qualities.isAllAudio()
            val titleIcon = if (isAudio) "🎵" else "🎬"
            val defaultTitle =
                if (isAudio) LocalizationManager.Strings.availableAudioFormats else LocalizationManager.Strings.availableQualities

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
                            LocalizationManager.Strings.addSelectAll,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = { onSelectAll(false) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            LocalizationManager.Strings.addClearSelection,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = LocalizationManager.Strings.selectedOfAvailable(
                    qualities.count { it.isSelected },
                    qualities.size
                ),
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
                                    text = LocalizationManager.Strings.qualityFormatLine(
                                        quality.format.uppercase(),
                                        "$sizeText$durText"
                                    ),
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
internal fun QuickTimeChip(
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
internal fun clockText(totalSeconds: Long): String {
    val t = totalSeconds.coerceAtLeast(0L)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/** One of the two clip download choices: a card, highlighted when chosen. */
@Composable
internal fun ClipModeOption(
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

@Composable
internal fun MediaInspectionSection(
    inputUrl: String,
    detectedEngine: EngineType,
    isInspectingMedia: Boolean,
    inspectedMediaQualities: List<MediaQualityEntry>,
    inspectMediaError: String?,
    isYtDlpInstalled: Boolean,
    ytDlpStatus: String,
    isDownloadingYtDlp: Boolean,
    onDownloadYtDlp: () -> Unit,
    onInspectMedia: () -> Unit,
    onMediaQualitySelectionToggled: (Int, Boolean) -> Unit,
    onSelectAllMediaQualitiesToggled: (Boolean) -> Unit,
    clipStartTime: String,
    clipEndTime: String,
    isClipRangeEnabled: Boolean,
    mediaDurationSeconds: Long,
    onClipStartTimeChanged: (String) -> Unit,
    onClipEndTimeChanged: (String) -> Unit,
    onToggleClipRange: (Boolean) -> Unit,
    clipFastMode: Boolean,
    onToggleClipFastMode: (Boolean) -> Unit,
    inspectedPlaylist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist?,
    playlistQuality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality,
    onPlaylistEntryToggled: (Int, Boolean) -> Unit,
    onPlaylistSelectAll: (Boolean) -> Unit,
    onPlaylistQualityChanged: (com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality) -> Unit,
    onPlaylistUseSingleVideo: () -> Unit,
    showStartTimePickerState: MutableState<Boolean>,
    showEndTimePickerState: MutableState<Boolean>,
    fullVideoDurationHhMm: String,
    isStartAfterEnd: Boolean,
    isEndExceedingVideo: Boolean,
    isStartExceedingVideo: Boolean,
    clipValidationErrorMessage: String?
) {
    var showStartTimePicker by showStartTimePickerState
    var showEndTimePicker by showEndTimePickerState
    // Media Stream Inspection Section
    if (detectedEngine == EngineType.MEDIA_STREAM) {
        Spacer(modifier = Modifier.height(12.dp))
        if (isInspectingMedia) {
            InspectionLoadingBanner(
                title = LocalizationManager.Strings.inspectingVideoQualities,
                subtitle = LocalizationManager.Strings.inspectingVideoQualitiesHint
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
                            text = LocalizationManager.Strings.videoInspectionFailed,
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
                            "🔄 ${LocalizationManager.Strings.addRetryInspection}",
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
                        text = if (preparing) "⏳ ${LocalizationManager.Strings.mediaEngineStarting}" else "⚠️ ${LocalizationManager.Strings.mediaEngineNotReady}",
                        color = if (preparing) Color(0xFFFDE68A) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = localizeStep(ytDlpStatus).ifBlank { LocalizationManager.Strings.mediaEngineRequiresYtDlp },
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
                            Text(LocalizationManager.Strings.preparingEngine, fontSize = 12.sp)
                        } else {
                            Text(LocalizationManager.Strings.startMediaEngine, fontSize = 12.sp)
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
                    if (isInputAudio) "🎵 ${LocalizationManager.Strings.inspectAudioFormats}" else "🎬 ${LocalizationManager.Strings.inspectVideoQuality}",
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
                        val isAnyAudio = selectedQuality?.isAudioOnly == true

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
                                        text = LocalizationManager.Strings.clipSectionTitle(
                                            isAnyAudio
                                        ),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = LocalizationManager.Strings.clipSectionHint,
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
                                            text = "⏱️ ${
                                                LocalizationManager.Strings.mediaDurationFullRange(
                                                    isAnyAudio,
                                                    clockText(mediaDurationSeconds),
                                                    formatSecondsToDisplayFriendly(
                                                        mediaDurationSeconds
                                                    )
                                                )
                                            }",
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
                                                text = LocalizationManager.Strings.clipStartTimeFrom,
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
                                            text = formatSecondsToDisplayFriendly(
                                                parseHhMmToSeconds(
                                                    startDisplay
                                                )
                                            ),
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
                                                text = LocalizationManager.Strings.clipEndTimeTo,
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
                                            text = formatSecondsToDisplayFriendly(
                                                parseHhMmToSeconds(
                                                    endDisplay
                                                )
                                            ),
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
                                        label = LocalizationManager.Strings.fullMediaChip(
                                            isAnyAudio,
                                            clockText(mediaDurationSeconds)
                                        ),
                                        onClick = {
                                            onClipStartTimeChanged("00:00")
                                            onClipEndTimeChanged(fullVideoDurationHhMm)
                                        }
                                    )
                                }
                                if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 300L) {
                                    QuickTimeChip(
                                        label = LocalizationManager.Strings.clipFirst5Min,
                                        onClick = {
                                            onClipStartTimeChanged("00:00:00"); onClipEndTimeChanged(
                                            "00:05:00"
                                        )
                                        })
                                }
                                if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 900L) {
                                    QuickTimeChip(
                                        label = LocalizationManager.Strings.clipFirst15Min,
                                        onClick = {
                                            onClipStartTimeChanged("00:00:00"); onClipEndTimeChanged(
                                            "00:15:00"
                                        )
                                        })
                                }
                                if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 1800L) {
                                    QuickTimeChip(
                                        label = LocalizationManager.Strings.clipFirst30Min,
                                        onClick = {
                                            onClipStartTimeChanged("00:00:00"); onClipEndTimeChanged(
                                            "00:30:00"
                                        )
                                        })
                                }
                                if (mediaDurationSeconds <= 0L || mediaDurationSeconds >= 3600L) {
                                    QuickTimeChip(
                                        label = LocalizationManager.Strings.clipFirst1Hour,
                                        onClick = {
                                            onClipStartTimeChanged("00:00:00"); onClipEndTimeChanged(
                                            "01:00:00"
                                        )
                                        })
                                }
                            }

                            // How the clip is fetched: two always-visible choices, the
                            // chosen one highlighted (a single switch whose label
                            // changed with its state was confusing).
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = LocalizationManager.Strings.clipModeTitle,
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
                                    title = "✂️ ${LocalizationManager.Strings.clipOnlyTitle}",
                                    lines = listOf(
                                        LocalizationManager.Strings.clipOnlyLine1,
                                        LocalizationManager.Strings.clipOnlyLine2,
                                        LocalizationManager.Strings.clipOnlyLine3
                                    ),
                                    onClick = { onToggleClipFastMode(false) },
                                    modifier = Modifier.weight(1f)
                                )
                                ClipModeOption(
                                    selected = clipFastMode,
                                    title = "⚡ ${
                                        LocalizationManager.Strings.fullThenCutTitle(
                                            isAnyAudio
                                        )
                                    }",
                                    lines = listOf(
                                        LocalizationManager.Strings.fullThenCutLine1(
                                            isAnyAudio,
                                            fullSizeText
                                        ),
                                        LocalizationManager.Strings.fullThenCutLine2,
                                        LocalizationManager.Strings.fullThenCutLine3,
                                        LocalizationManager.Strings.fullThenCutLine4
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

}
