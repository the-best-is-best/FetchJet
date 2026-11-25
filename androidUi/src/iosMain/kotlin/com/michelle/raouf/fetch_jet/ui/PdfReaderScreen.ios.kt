package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun PdfReaderScreen(request: PdfOpen, onClose: () -> Unit, onOpenOutside: () -> Unit) {
    LaunchedEffect(Unit) { onOpenOutside() }
}
