package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoCutUi

@Composable
internal expect fun VideoTrimScreen(ui: VideoCutUi, path: String, onEvent: (DashboardUiEvent) -> Unit)
