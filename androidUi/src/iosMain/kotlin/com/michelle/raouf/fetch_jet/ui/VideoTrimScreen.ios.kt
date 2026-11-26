package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutMode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoCutUi

@Composable
internal actual fun VideoTrimScreen(ui: VideoCutUi, path: String, onEvent: (DashboardUiEvent) -> Unit) {
    LaunchedEffect(Unit) { onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME)) }
}
