package com.michelle.raouf.fetch_jet.core.browser

actual object BrowserIntegration {
    actual val canAddToEdgeAutomatically: Boolean get() = false
    actual fun isEdgeRegistered(): Boolean = false
    actual fun addToEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun removeFromEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun setUpOnFirstRun() {}

    actual fun browsers(): List<BrowserInfo> = emptyList()
    actual fun addExtension(id: String): AddExtensionResult = AddExtensionResult.NotInstalled
    actual fun openFolder(folder: String) {}
    actual fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult =
        AddExtensionResult.NotInstalled
    actual fun openBrowser(id: String) {}

    actual val canKeepReadyForBrowser: Boolean get() = false
    actual val canPopUpOverBrowser: Boolean get() = false
    actual fun isPopUpAllowed(): Boolean = true
    actual fun requestPopUpPermission() {}
    actual fun isKeptReadyForBrowser(): Boolean = false
    actual fun setKeepReadyForBrowser(enabled: Boolean) {}
    actual fun restoreKeepReady() {}
}
