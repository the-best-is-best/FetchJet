package com.michelle.raouf.fetch_jet.core.browser

actual object BrowserIntegration {
    actual val canAddToEdgeAutomatically: Boolean get() = false
    actual fun isEdgeRegistered(): Boolean = false
    actual fun addToEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun removeFromEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun setUpOnFirstRun() {}

    actual val canKeepReadyForBrowser: Boolean get() = false
    actual fun isKeptReadyForBrowser(): Boolean = false
    actual fun setKeepReadyForBrowser(enabled: Boolean) {}
    actual fun restoreKeepReady() {}
}
