package com.michelle.raouf.fetch_jet.core.browser

import android.content.Context
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

actual object BrowserIntegration {
    actual val canAddToEdgeAutomatically: Boolean get() = false
    actual fun isEdgeRegistered(): Boolean = false
    actual fun addToEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun removeFromEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun setUpOnFirstRun() {}

    private const val PREFS = "fetchjet_browser"
    private const val KEY_KEEP_READY = "keepReady"

    private fun prefs() =
        PathResolver.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    actual val canKeepReadyForBrowser: Boolean get() = true

    actual fun isKeptReadyForBrowser(): Boolean =
        prefs()?.getBoolean(KEY_KEEP_READY, false) ?: false

    actual fun setKeepReadyForBrowser(enabled: Boolean) {
        val ctx = PathResolver.appContext ?: return
        prefs()?.edit()?.putBoolean(KEY_KEEP_READY, enabled)?.apply()
        if (enabled) BrowserReadyService.start(ctx) else BrowserReadyService.stop(ctx)
    }

    actual fun restoreKeepReady() {
        val ctx = PathResolver.appContext ?: return
        if (isKeptReadyForBrowser()) BrowserReadyService.start(ctx)
    }
}
