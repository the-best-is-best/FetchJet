package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberBackupFileSaver(text: () -> String?, onFinished: (failed: Boolean) -> Unit): (fileName: String) -> Unit
