package com.michelle.raouf.fetch_jet

import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge

/**
 * macOS hands magnet links, fetchjet:// links and opened .torrent files to a running app as
 * Apple Events, not as arguments.
 */
fun installMacOpenHandlers() {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return
    try {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.APP_OPEN_URI)) {
            desktop.setOpenURIHandler { event -> deliverOpened(event.uri.toString()) }
        }
        if (desktop.isSupported(java.awt.Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event -> event.files.forEach { deliverOpened(it.absolutePath) } }
        }
    } catch (_: Throwable) {
    }
}

private fun deliverOpened(raw: String) {
    val url = parseUrlFromArg(raw)
    if (url.isBlank()) return
    ExternalDownloadBridge.dispatch(url, "")
    ExternalDownloadBridge.onShowAndFocus?.invoke()
}
