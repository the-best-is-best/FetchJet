package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher

data class MainDownloaderState(
    val torrentSupported: Boolean = false,
    val torrentDefault: Boolean? = null,
    val clipboardOn: Boolean = false,
    val startupSupported: Boolean = false,
    val startupOn: Boolean = false,
    val keepReadySupported: Boolean = false,
    val keepReadyOn: Boolean = false,
    val extensionSeen: Boolean = false,
) {
    val torrentToDo: Boolean get() = torrentSupported && torrentDefault != true
    val clipboardToDo: Boolean get() = !clipboardOn
    val startupToDo: Boolean get() = startupSupported && !startupOn
    val keepReadyToDo: Boolean get() = keepReadySupported && !keepReadyOn
    val extensionToDo: Boolean get() = !extensionSeen
    val anythingToDo: Boolean get() = torrentToDo || clipboardToDo || startupToDo || keepReadyToDo || extensionToDo
}

data class MainDownloaderResult(
    /** No browser has the extension yet: the caller opens Settings > Browser extension. */
    val openExtensionPage: Boolean,
    val systemPageOpened: Boolean,
)

/**
 * "Make FetchJet your main downloader": one question on the first run, one tap does every
 * step this platform allows (default for torrents, clipboard watch, start at login / keep
 * ready, browser extension). Nothing happens without that tap.
 */
object MainDownloaderSetup {
    val available: Boolean get() = DefaultTorrentHandler.supported

    suspend fun state(): MainDownloaderState {
        val startupSupported = StartupLauncher.isSupported && StartupLauncher.managedBySystemUri == null
        val keepReady = runCatching { BrowserIntegration.canKeepReadyForBrowser }.getOrDefault(false)
        return MainDownloaderState(
            torrentSupported = DefaultTorrentHandler.supported,
            torrentDefault = if (DefaultTorrentHandler.supported) runCatching { DefaultTorrentHandler.isDefault() }.getOrNull() else null,
            clipboardOn = BehaviorSettings.monitorClipboard.value,
            startupSupported = startupSupported,
            startupOn = startupSupported && runCatching { StartupLauncher.isEnabled() }.getOrDefault(false),
            keepReadySupported = keepReady,
            keepReadyOn = keepReady && runCatching { BrowserIntegration.isKeptReadyForBrowser() }.getOrDefault(false),
            extensionSeen = BridgePairing.seenBrowsers.value.isNotEmpty(),
        )
    }

    /** The state when the first-run question should be shown, else null. */
    suspend fun stateToAsk(): MainDownloaderState? {
        if (!available || BehaviorSettings.askedMainDownloader.value) return null
        val state = state()
        if (!state.anythingToDo) {
            markAsked()
            return null
        }
        return state
    }

    fun markAsked() = BehaviorSettings.setAskedMainDownloader(true)

    suspend fun applyAll(state: MainDownloaderState): MainDownloaderResult {
        markAsked()
        if (state.clipboardToDo) BehaviorSettings.setMonitorClipboard(true)
        if (state.startupToDo) runCatching { StartupLauncher.setEnabled(true) }
        if (state.keepReadyToDo) runCatching { BrowserIntegration.setKeepReadyForBrowser(true) }
        var systemPage = false
        if (state.torrentToDo) {
            val done = runCatching { DefaultTorrentHandler.makeDefault() }.getOrDefault(false)
            systemPage = done && DefaultTorrentHandler.opensSystemPage
        }
        return MainDownloaderResult(openExtensionPage = state.extensionToDo, systemPageOpened = systemPage)
    }
}
