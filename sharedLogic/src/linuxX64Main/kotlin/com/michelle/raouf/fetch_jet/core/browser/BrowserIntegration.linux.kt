package com.michelle.raouf.fetch_jet.core.browser

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

actual object BrowserIntegration {
    private const val NOT_ON_LINUX = "Edge policies are Windows only"

    actual val canAddToEdgeAutomatically: Boolean = false

    actual fun isEdgeRegistered(): Boolean = false

    actual fun addToEdge(): Result<Unit> = Result.failure(UnsupportedOperationException(NOT_ON_LINUX))

    actual fun removeFromEdge(): Result<Unit> = Result.failure(UnsupportedOperationException(NOT_ON_LINUX))

    @OptIn(DelicateCoroutinesApi::class)
    actual fun setUpOnFirstRun() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                DesktopBrowsers.refreshUnpackedExtension()
            } catch (_: Throwable) {
            }
        }
    }

    actual fun browsers(): List<BrowserInfo> = DesktopBrowsers.browsers()

    actual fun addExtension(id: String): AddExtensionResult = DesktopBrowsers.addExtension(id)

    actual fun openFolder(folder: String) = DesktopBrowsers.openFolder(folder)

    actual fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult =
        DesktopBrowsers.installXpi(id, bytes, fileName)

    actual fun openBrowser(id: String) = DesktopBrowsers.openBrowser(id)

    actual val canKeepReadyForBrowser: Boolean = false

    actual fun isKeptReadyForBrowser(): Boolean = false

    actual fun setKeepReadyForBrowser(enabled: Boolean) {}

    actual fun restoreKeepReady() {}

    actual val canPopUpOverBrowser: Boolean = false

    actual fun isPopUpAllowed(): Boolean = true

    actual fun requestPopUpPermission() {}
}
