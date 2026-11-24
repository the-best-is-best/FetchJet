package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue

@Composable
internal expect fun MediaPlayerScreen(queue: PlaybackQueue, onClose: () -> Unit)
