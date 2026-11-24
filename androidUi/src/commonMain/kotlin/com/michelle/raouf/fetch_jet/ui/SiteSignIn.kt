package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun SiteSignInDialog(site: String, onFinished: (cookie: String?) -> Unit)
