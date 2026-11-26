package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class PdfOpen(
    val path: String,
    val title: String,
    val task: DownloadTask?,
    val hidden: Boolean
)

internal object PdfRequests {
    private val _current = MutableStateFlow<PdfOpen?>(null)
    val current: StateFlow<PdfOpen?> = _current

    fun open(request: PdfOpen) {
        _current.value = request
    }

    fun close() {
        _current.value = null
    }
}

@Composable
internal expect fun PdfReaderScreen(request: PdfOpen, onClose: () -> Unit, onOpenOutside: () -> Unit)
