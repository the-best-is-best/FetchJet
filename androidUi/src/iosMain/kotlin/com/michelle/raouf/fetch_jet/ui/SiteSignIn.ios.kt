package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun SiteSignInDialog(site: String, onFinished: (cookie: String?) -> Unit) {
    LaunchedEffect(Unit) { onFinished(null) }
}
