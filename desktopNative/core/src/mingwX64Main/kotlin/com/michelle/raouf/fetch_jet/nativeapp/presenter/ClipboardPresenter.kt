package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.settings.ClipboardLinks
import com.michelle.raouf.fetch_jet.getClipboardText
import com.michelle.raouf.fetch_jet.platform.win.winClipboardSequence
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val CLIPBOARD_PERIOD_MS = 800L

private suspend fun clipboardText(): String? = try {
    getClipboardText()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Throwable) {
    null
}

internal fun Presenter.startClipboardWatcher() {
    var firstRun = true
    scope.launch {
        vm.uiState.map { it.monitorClipboard to it.clipboardVideoPages }.distinctUntilChanged().collectLatest { (watch, videoPages) ->
            if (!watch) {
                firstRun = false
                return@collectLatest
            }
            var last: String? = if (firstRun) clipboardText() else null
            var seen: UInt? = if (firstRun) winClipboardSequence() else null
            firstRun = false
            while (true) {
                delay(CLIPBOARD_PERIOD_MS)
                val sequence = winClipboardSequence()
                if (sequence == seen) continue
                seen = sequence
                val text = clipboardText()
                if (text != null && text != last) {
                    last = text
                    ClipboardLinks.downloadableUrl(text, videoPages)?.let { vm.onEvent(DashboardUiEvent.OnClipboardLink(it)) }
                }
            }
        }
    }
    scope.launch {
        vm.uiState.map { it.clipboardOfferUrl }.distinctUntilChanged().collect {
            if (it != null) ExternalDownloadBridge.showAndFocus()
        }
    }
}
