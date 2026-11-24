package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue

@Composable
internal actual fun MediaPlayerScreen(queue: PlaybackQueue, onClose: () -> Unit) {
    LaunchedEffect(Unit) { onClose() }
}
