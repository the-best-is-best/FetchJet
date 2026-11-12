package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressMode
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings
import com.michelle.raouf.fetch_jet.presentation.dashboard.AudioCompressUi
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.tbib.kmp_localization.LocalizationManager
import kotlin.math.roundToInt

@Composable
fun AudioCompressDialog(page: AudioCompressUi, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val muted = colors.onSurfaceVariant
    val settings = page.settings
    val change = { next: AudioCompressSettings ->
        onEvent(DashboardUiEvent.OnAudioCompressSettingsChanged(next))
    }
    Dialog(
        onDismissRequest = { onEvent(DashboardUiEvent.OnDismissAudioCompress) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 420.dp
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.audioCompressTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            page.fileName,
                            color = muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SizeCard(page)
                    if (!page.nothingToGain) ModeSwitch(settings.mode, !page.running) { change(settings.copy(mode = it)) }
                    if (page.nothingToGain) {
                        Text(s.audioCompressNothing, color = muted, fontSize = 13.sp)
                    } else if (settings.mode == AudioCompressMode.SIMPLE) {
                        val first = minOf(page.firstLevel, AudioCompress.LEVELS.size)
                        val level = settings.level.coerceIn(first, AudioCompress.LEVELS.size)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    s.audioCompressLevel,
                                    color = muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$level · ${AudioCompress.LEVELS[level - 1].bitrateKbps} kbps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            if (first < AudioCompress.LEVELS.size) Slider(
                                value = level.toFloat(),
                                onValueChange = { change(settings.copy(level = it.roundToInt())) },
                                valueRange = first.toFloat()..AudioCompress.LEVELS.size.toFloat(),
                                steps = (AudioCompress.LEVELS.size - first - 1).coerceAtLeast(0),
                                enabled = !page.running,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primary,
                                    activeTrackColor = colors.primary,
                                    inactiveTrackColor = colors.outlineVariant,
                                    activeTickColor = colors.onPrimary.copy(alpha = 0.6f),
                                    inactiveTickColor = colors.primary.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    s.audioCompressHigherQuality,
                                    color = muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    s.audioCompressSmallerFile,
                                    color = muted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        val rates = page.bitrateChoices.ifEmpty { AudioCompress.BITRATES }
                        if (page.formats.size > 1) PickField(
                            s.audioCompressFormat,
                            page.format,
                            page.formats,
                            !page.running,
                            Modifier.fillMaxWidth()
                        ) { change(settings.copy(format = page.formats[it])) }
                        val bitrate: @Composable (Modifier) -> Unit = { m ->
                            PickField(
                                s.audioCompressBitrate,
                                "${settings.bitrateKbps}k",
                                rates.map { "${it}k" },
                                !page.running,
                                m
                            ) { change(settings.copy(bitrateKbps = rates[it])) }
                        }
                        val sampleRate: @Composable (Modifier) -> Unit = { m ->
                            PickField(
                                s.audioCompressSampleRate,
                                sampleRateText(settings.sampleRate),
                                AudioCompress.SAMPLE_RATES.map { sampleRateText(it) },
                                !page.running,
                                m
                            ) { change(settings.copy(sampleRate = AudioCompress.SAMPLE_RATES[it])) }
                        }
                        val channels: @Composable (Modifier) -> Unit = { m ->
                            PickField(
                                s.audioCompressChannels,
                                channelsText(settings.channels),
                                AudioCompress.CHANNELS.map { channelsText(it) },
                                !page.running,
                                m
                            ) { change(settings.copy(channels = AudioCompress.CHANNELS[it])) }
                        }
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                bitrate(Modifier.weight(1f))
                                sampleRate(Modifier.weight(1f))
                                channels(Modifier.weight(1f))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                bitrate(Modifier.weight(1f))
                                sampleRate(Modifier.weight(1f))
                            }
                            channels(Modifier.fillMaxWidth())
                        }
                    }
                    if (page.running) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${s.audioCompressRunning} ${(page.progress * 100).roundToInt().coerceIn(0, 100)}%",
                                color = muted,
                                fontSize = 13.sp
                            )
                            LinearProgressIndicator(
                                progress = { page.progress },
                                color = colors.primary,
                                trackColor = colors.outlineVariant,
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    if (page.failed) {
                        Text(s.audioCompressFailed, color = colors.error, fontSize = 13.sp)
                    }
                    if (page.copies.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.audioCompressCopies, color = muted, fontSize = 13.sp)
                            page.copies.forEach { copy ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            copy.name,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(formatBytes(copy.bytes), color = muted, fontSize = 12.sp)
                                    }
                                    Text(
                                        s.audioCompressDelete,
                                        color = colors.error.copy(alpha = if (page.running) 0.4f else 1f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !page.running) {
                                                onEvent(DashboardUiEvent.OnAudioCompressDeleteCopy(copy.path))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onEvent(DashboardUiEvent.OnDismissAudioCompress) },
                            enabled = !page.running,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, colors.outline),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            modifier = Modifier.height(42.dp)
                        ) { Text(s.cancel, color = colors.onSurface) }
                        Button(
                            onClick = { onEvent(DashboardUiEvent.OnAudioCompressStartClicked) },
                            enabled = !page.running && !page.reading && !page.nothingToGain,
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp),
                            modifier = if (wide) Modifier.height(42.dp)
                            else Modifier.height(42.dp).weight(1f)
                        ) { Text(s.audioCompressStart, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeCard(page: AudioCompressUi) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF34D399) else Color(0xFF059669)
    val known = !page.reading && page.estimatedBytes > 0L && page.originalBytes > 0L
    val ratio = if (known) page.estimatedBytes.toFloat() / page.originalBytes.toFloat() else 0f
    val saving = if (known) (100 - page.estimatedBytes * 100 / page.originalBytes).toInt() else 0
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.35f), shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatBytes(page.originalBytes), color = colors.onSurfaceVariant, fontSize = 16.sp)
            Text(
                if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→",
                color = colors.onSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            when {
                page.reading -> Text(
                    s.audioCompressReading,
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                else -> Text(
                    if (page.estimatedBytes > 0L) formatBytes(page.estimatedBytes) else "—",
                    color = colors.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            if (known && saving > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "−$saving%",
                    color = green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(green.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.outlineVariant)
        ) {
            if (known) {
                Box(
                    modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                        .background(colors.primary, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(
    mode: AudioCompressMode,
    enabled: Boolean,
    onPick: (AudioCompressMode) -> Unit
) {
    val s = LocalizationManager.Strings
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(
            AudioCompressMode.SIMPLE to s.audioCompressSimple,
            AudioCompressMode.ADVANCED to s.audioCompressAdvanced
        ).forEach { (value, label) ->
            val selected = mode == value
            val half = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(half)
                    .then(
                        if (selected) Modifier
                            .background(colors.primary.copy(alpha = 0.18f), half)
                            .border(1.dp, colors.primary, half)
                        else Modifier
                    )
                    .clickable(enabled = enabled && !selected) { onPick(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PickField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    modifier: Modifier,
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(11.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clip(shape)
                    .background(colors.surfaceVariant.copy(alpha = 0.35f), shape)
                    .border(1.dp, colors.outlineVariant, shape)
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("▾", color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { open = false; onPick(index) })
                }
            }
        }
    }
}

@Composable
private fun sampleRateText(rate: Int): String =
    if (rate == 0) LocalizationManager.Strings.audioCompressAuto else "${rate}Hz"

@Composable
private fun channelsText(channels: Int): String = when (channels) {
    1 -> LocalizationManager.Strings.audioCompressMono
    2 -> LocalizationManager.Strings.audioCompressStereo
    else -> LocalizationManager.Strings.audioCompressAuto
}
