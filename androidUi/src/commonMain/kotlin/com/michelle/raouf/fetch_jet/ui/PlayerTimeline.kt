package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback

@Composable
internal fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1L)
    val shown = dragging ?: (positionMs.coerceIn(0L, total).toFloat() / total)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            MediaPlayback.formatTime((shown * total).toLong()),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * total).toLong()) }
                dragging = null
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            MediaPlayback.formatTime(durationMs),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
