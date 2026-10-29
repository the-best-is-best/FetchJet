package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository

/** Settings: how many downloads run at the same time (the rest wait in the queue). */
@Composable
fun QueueSettingsCard() {
    val repository = QueueManagerRepository.getInstance()
    val max by repository.maxConcurrentDownloads.collectAsState()

    SettingsGroup(
        title = "Queue",
        footer = "Extra downloads wait and start by themselves, oldest first, when one finishes. " +
                "\"Start now\" on a waiting download skips the queue."
    ) {
        SettingRow(
            title = "Downloads at the same time",
            subtitle = if (max <= 0) "No limit" else "$max at a time"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            SegmentedPicker(
                options = listOf(1, 2, 3, 4, 5, 8, 0),
                selected = max,
                label = { if (it == 0) "No limit" else it.toString() },
                onSelect = { repository.setMaxConcurrentDownloads(it) }
            )
        }
    }
}
