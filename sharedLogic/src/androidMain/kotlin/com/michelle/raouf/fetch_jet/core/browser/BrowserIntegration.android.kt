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

    actual val canPopUpOverBrowser: Boolean get() = android.os.Build.VERSION.SDK_INT >= 29
    actual fun isPopUpAllowed(): Boolean {
        val ctx = PathResolver.appContext ?: return false
        return android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(
            ctx
        )
    }

    actual fun requestPopUpPermission() {
        val ctx = PathResolver.appContext ?: return
        try {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + ctx.packageName)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

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
