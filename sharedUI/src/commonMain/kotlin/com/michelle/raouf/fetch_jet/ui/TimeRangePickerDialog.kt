package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.utils.formatSecondsToHhMm

@Composable
fun TimeRangePickerDialog(
    title: String,
    initialHhMm: String,
    maxDurationSeconds: Long = 0L,
    minTimeSeconds: Long = 0L,
    onTimeSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val initialParts = initialHhMm.split(":")
    val parsedHours = initialParts.getOrNull(0)?.toIntOrNull() ?: 0
    val parsedMinutes = initialParts.getOrNull(1)?.toIntOrNull() ?: 0
    val parsedSeconds = initialParts.getOrNull(2)?.toIntOrNull() ?: 0

    var hours by remember { mutableStateOf(parsedHours.coerceAtLeast(0)) }
    var minutes by remember { mutableStateOf(parsedMinutes.coerceIn(0, 59)) }
    // Seconds come only from the typed field (the spinners step by minutes).
    var seconds by remember { mutableStateOf(parsedSeconds.coerceIn(0, 59)) }

    // Always HH:MM:SS (two parts would be read as MM:SS by the download engine).
    val formatted = buildString {
        append(hours.toString().padStart(2, '0')).append(':')
        append(minutes.toString().padStart(2, '0')).append(':')
        append(seconds.toString().padStart(2, '0'))
    }
    val currentTotalSeconds = hours * 3600L + minutes * 60L + seconds

    val maxTypedSeconds = 359_999L // 99:59:59 - the boxes allow at most 99 hours

    fun applyTotalSeconds(total: Long) {
        val t = total.coerceIn(0L, maxTypedSeconds)
        hours = (t / 3600).toInt()
        minutes = ((t % 3600) / 60).toInt()
        seconds = (t % 60).toInt()
    }



    val isExceedingMax = maxDurationSeconds > 0L && currentTotalSeconds > maxDurationSeconds
    val isBelowMin = minTimeSeconds > 0L && currentTotalSeconds <= minTimeSeconds
    val isValid = !isExceedingMax && !isBelowMin

    val validationErrorMessage = when {
        isExceedingMax -> "Exceeds video duration (${formatSecondsToHhMm(maxDurationSeconds)})"
        isBelowMin -> "Must be after start time (${formatSecondsToHhMm(minTimeSeconds)})"
        else -> null
    }

    val durationSummary = buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (seconds > 0) add("${seconds}s")
    }.joinToString(" ").ifEmpty { "Start (00:00)" }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            "✕",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable HH : MM : SS boxes. Type straight into a box (digits only, each box
                // validated: hours 0-99, minutes/seconds 0-59) or use the arrows.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeFieldColumn(
                        label = "Hours",
                        value = hours,
                        maxValue = 99,
                        onValueChange = { hours = it },
                        onUp = {
                            val nextSec = (hours + 1) * 3600L + minutes * 60L + seconds
                            if (maxDurationSeconds in 1..nextSec) applyTotalSeconds(maxDurationSeconds) else hours++
                        },
                        onDown = { if (hours > 0) hours-- },
                        upEnabled = maxDurationSeconds <= 0L || currentTotalSeconds < maxDurationSeconds,
                        downEnabled = hours > 0
                    )
                    TimeColon()
                    TimeFieldColumn(
                        label = "Minutes",
                        value = minutes,
                        maxValue = 59,
                        onValueChange = { minutes = it },
                        onUp = {
                            val nextSec = currentTotalSeconds + 60L
                            if (maxDurationSeconds in 1..nextSec) applyTotalSeconds(maxDurationSeconds)
                            else if (minutes < 59) minutes++ else { minutes = 0; hours++ }
                        },
                        onDown = {
                            if (minutes > 0) minutes--
                            else if (hours > 0) { hours--; minutes = 59 }
                        },
                        upEnabled = maxDurationSeconds <= 0L || currentTotalSeconds < maxDurationSeconds,
                        downEnabled = minutes > 0 || hours > 0
                    )
                    TimeColon()
                    TimeFieldColumn(
                        label = "Seconds",
                        value = seconds,
                        maxValue = 59,
                        onValueChange = { seconds = it },
                        onUp = {
                            val nextSec = currentTotalSeconds + 1L
                            if (maxDurationSeconds in 1..nextSec) applyTotalSeconds(maxDurationSeconds)
                            else applyTotalSeconds(currentTotalSeconds + 1L)
                        },
                        onDown = { if (currentTotalSeconds > 0) applyTotalSeconds(currentTotalSeconds - 1L) },
                        upEnabled = maxDurationSeconds <= 0L || currentTotalSeconds < maxDurationSeconds,
                        downEnabled = currentTotalSeconds > 0
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Preview Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ Selected Time: $formatted ($durationSummary)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (validationErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ $validationErrorMessage",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Increment Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val targetMins = hours * 60 + minutes + 5
                            val targetSec = targetMins * 60L
                            if (maxDurationSeconds in 1..targetSec) {
                                applyTotalSeconds(maxDurationSeconds)
                            } else {
                                applyTotalSeconds(targetMins * 60L)
                            }
                        },
                        label = { Text("+5m", fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val targetMins = hours * 60 + minutes + 15
                            val targetSec = targetMins * 60L
                            if (maxDurationSeconds in 1..targetSec) {
                                applyTotalSeconds(maxDurationSeconds)
                            } else {
                                applyTotalSeconds(targetMins * 60L)
                            }
                        },
                        label = { Text("+15m", fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val targetMins = hours * 60 + minutes + 30
                            val targetSec = targetMins * 60L
                            if (maxDurationSeconds in 1..targetSec) {
                                applyTotalSeconds(maxDurationSeconds)
                            } else {
                                applyTotalSeconds(targetMins * 60L)
                            }
                        },
                        label = { Text("+30m", fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val targetMins = hours * 60 + minutes + 60
                            val targetSec = targetMins * 60L
                            if (maxDurationSeconds in 1..targetSec) {
                                applyTotalSeconds(maxDurationSeconds)
                            } else {
                                hours++
                            }
                        },
                        label = { Text("+1h", fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (maxDurationSeconds > 0) {
                        val maxH = (maxDurationSeconds / 3600).toInt()
                        val maxM = ((maxDurationSeconds % 3600) / 60).toInt()
                        val endFormatted = "${maxH.toString().padStart(2, '0')}:${
                            maxM.toString().padStart(2, '0')
                        }"
                        FilterChip(
                            selected = false,
                            onClick = {
                                applyTotalSeconds(maxDurationSeconds)
                            },
                            label = { Text("End of Video ($endFormatted)", fontSize = 11.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                    alpha = 0.5f
                                )
                            )
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (minTimeSeconds > 0L) {
                                // first whole minute after the start time
                                applyTotalSeconds((minTimeSeconds / 60 + 1) * 60)
                            } else {
                                applyTotalSeconds(0L)
                            }
                        },
                        label = {
                            Text(
                                if (minTimeSeconds > 0L) "Min (${
                                    formatSecondsToHhMm(
                                        minTimeSeconds + 60L
                                    )
                                })" else "00:00", fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (isValid) {
                                onTimeSelected(formatted)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            if (isValid) "Apply ($formatted)" else "Invalid Time",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeColon() {
    Text(
        text = ":",
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

/**
 * One time unit: ▲ / editable two-digit box / ▼ / label. Typing is validated per box: only
 * digits, at most two, values above [maxValue] snap to it; an empty box counts as 0 and is
 * shown as "00" again when it loses focus.
 */
@Composable
private fun TimeFieldColumn(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    upEnabled: Boolean,
    downEnabled: Boolean
) {
    // Text the user is typing; re-synced from [value] whenever the arrows/chips change it.
    var text by remember { mutableStateOf(value.toString().padStart(2, '0')) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        // Arrows / chips changed the value: reflect it unless the user is mid-typing the same number.
        if (!focused || text.toIntOrNull() != value) text = value.toString().padStart(2, '0')
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onUp, enabled = upEnabled, modifier = Modifier.size(40.dp)) {
            Text("▲", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (focused) 0.9f else 0.6f)
            ),
            modifier = Modifier.width(80.dp).height(64.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(2)
                        val parsed = digits.toIntOrNull() ?: 0
                        // Over the limit ("75" minutes) snaps to the limit at once, so the box
                        // never shows a value the dialog would not apply.
                        val n = parsed.coerceAtMost(maxValue)
                        text = if (parsed > maxValue) n.toString() else digits
                        onValueChange(n)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (!state.isFocused) {
                                // Normalise on blur: "" -> "00", "7" -> "07", "75" -> clamp to max.
                                val n = (text.toIntOrNull() ?: 0).coerceIn(0, maxValue)
                                onValueChange(n)
                                text = n.toString().padStart(2, '0')
                            }
                        }
                )
            }
        }
        IconButton(onClick = onDown, enabled = downEnabled, modifier = Modifier.size(40.dp)) {
            Text("▼", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
