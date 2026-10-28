package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrafficLimitSelector(
    title: String,
    speedBytesPerSec: Long,
    onSpeedChanged: (Long) -> Unit
) {
    val presets = listOf(
        "Unlimited (Max)" to 0L,
        "512 KB/s" to 512L * 1024L,
        "1 MB/s" to 1024L * 1024L,
        "5 MB/s" to 5L * 1024L * 1024L,
        "10 MB/s" to 10L * 1024L * 1024L
    )

    val matchedPreset = presets.firstOrNull { it.second == speedBytesPerSec }

    var isCustom by remember(speedBytesPerSec) {
        mutableStateOf(speedBytesPerSec > 0L && matchedPreset == null)
    }

    var customUnit by remember {
        mutableStateOf(
            when {
                speedBytesPerSec <= 0L -> "MB/s"
                speedBytesPerSec >= 1024L * 1024L -> "MB/s"
                else -> "KB/s"
            }
        )
    }

    fun calculateBytes(value: Double, unit: String): Long {
        return when (unit) {
            "KB/s" -> (value * 1024.0).toLong()
            "MB/s" -> (value * 1024.0 * 1024.0).toLong()
            "Kbps" -> (value * 1000.0 / 8.0).toLong()
            "Mbps" -> (value * 1000.0 * 1000.0 / 8.0).toLong()
            else -> (value * 1024.0 * 1024.0).toLong()
        }
    }

    var customText by remember {
        mutableStateOf(
            if (speedBytesPerSec <= 0L) ""
            else if (speedBytesPerSec >= 1024L * 1024L) {
                val mb = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
                if (mb == mb.toLong().toDouble()) mb.toLong()
                    .toString() else ((mb * 10).toLong() / 10.0).toString()
            } else {
                (speedBytesPerSec / 1024L).toString()
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val currentLabel = when {
                speedBytesPerSec <= 0L -> "No Limit (Max Speed)"
                speedBytesPerSec >= 1024L * 1024L -> {
                    val mb = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
                    val formatted = if (mb == mb.toLong().toDouble()) mb.toLong()
                        .toString() else ((mb * 10).toLong() / 10.0).toString()
                    "$formatted MB/s"
                }

                else -> "${speedBytesPerSec / 1024L} KB/s"
            }
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (speedBytesPerSec <= 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { (label, value) ->
                val selected = !isCustom && speedBytesPerSec == value
                FilterChip(
                    selected = selected,
                    onClick = {
                        isCustom = false
                        onSpeedChanged(value)
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }

            FilterChip(
                selected = isCustom,
                onClick = {
                    isCustom = true
                    if (customText.isBlank()) {
                        customText = "2"
                        onSpeedChanged(calculateBytes(2.0, customUnit))
                    }
                },
                label = { Text("Custom...", fontSize = 12.sp) }
            )
        }

        if (isCustom) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { newValue ->
                            val sanitized = newValue.filter { it.isDigit() || it == '.' }
                            customText = sanitized
                            val num = sanitized.toDoubleOrNull() ?: 0.0
                            onSpeedChanged(calculateBytes(num, customUnit))
                        },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        placeholder = { Text("Value", fontSize = 13.sp) },
                        leadingIcon = {
                            Text("⚡", fontSize = 14.sp)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.6f
                            ),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Modern Segmented Unit Switcher
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("KB/s", "MB/s", "Kbps", "Mbps").forEach { u ->
                                val isSelected = customUnit == u
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            customUnit = u
                                            val num = customText.toDoubleOrNull() ?: 0.0
                                            onSpeedChanged(calculateBytes(num, u))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = u,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }


                    // Helpful Hint / Live Total preview
                    val num = customText.toDoubleOrNull() ?: 0.0
                    val totalBytes = calculateBytes(num, customUnit)
                    if (totalBytes > 0L) {
                        val formattedHint = when (customUnit) {
                            "MB/s" -> "= ${num.toLong()} MB/s"
                            "KB/s" -> "= ${num.toLong()} KB/s"
                            "Mbps" -> "= ${num.toLong()} Mbps (${totalBytes / (1024L * 1024L)} MB/s)"
                            "Kbps" -> "= ${num.toLong()} Kbps (${totalBytes / 1024L} KB/s)"
                            else -> "= $num $customUnit"
                        }
                        Text(
                            text = formattedHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        }
    }
}