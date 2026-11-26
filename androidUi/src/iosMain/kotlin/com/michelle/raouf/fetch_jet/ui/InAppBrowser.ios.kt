package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun InAppBrowser(startUrl: String, onClose: () -> Unit) {
    LaunchedEffect(Unit) { onClose() }
}
