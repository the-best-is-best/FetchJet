package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinRegRoot
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winRegDeleteTree
import com.michelle.raouf.fetch_jet.platform.win.winRegReadString
import com.michelle.raouf.fetch_jet.platform.win.winRegWriteString
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

actual object BrowserIntegration {
    private const val EDGE_KEY = "Software\\Microsoft\\Edge\\Extensions\\$EDGE_EXTENSION_ID"
    private const val EDGE_UPDATE_URL = "https://edge.microsoft.com/extensionwebstorebase/v1/crx"
    private const val SCHEME_KEY = "Software\\Classes\\fetchjet"

    private val mayChangeSystem: Boolean get() = !WinApp.isStoreBuild && !WinApp.isDevelopmentBuild

    actual val canAddToEdgeAutomatically: Boolean get() = mayChangeSystem

    actual fun isEdgeRegistered(): Boolean =
        mayChangeSystem && winRegReadString(WinRegRoot.CURRENT_USER, EDGE_KEY, "update_url") != null

    actual fun addToEdge(): Result<Unit> {
        if (!mayChangeSystem) return Result.failure(UnsupportedOperationException("Windows only"))
        return if (winRegWriteString(WinRegRoot.CURRENT_USER, EDGE_KEY, "update_url", EDGE_UPDATE_URL)) Result.success(Unit)
        else Result.failure(Exception("reg add failed"))
    }

    actual fun removeFromEdge(): Result<Unit> {
        if (!mayChangeSystem) return Result.failure(UnsupportedOperationException("Windows only"))
        return if (winRegDeleteTree(WinRegRoot.CURRENT_USER, EDGE_KEY)) Result.success(Unit)
        else Result.failure(Exception("reg delete failed"))
    }

    fun registerUrlScheme() {
        try {
            if (!mayChangeSystem) return
            val exe = WinApp.launcherPath ?: return
            val current = winRegReadString(WinRegRoot.CURRENT_USER, "$SCHEME_KEY\\shell\\open\\command", null).orEmpty()
            if (current.contains(exe, ignoreCase = true)) return
            winRegWriteString(WinRegRoot.CURRENT_USER, SCHEME_KEY, null, "URL:FetchJet link")
            winRegWriteString(WinRegRoot.CURRENT_USER, SCHEME_KEY, "URL Protocol", "")
            winRegWriteString(WinRegRoot.CURRENT_USER, "$SCHEME_KEY\\DefaultIcon", null, "\"$exe\",0")
            winRegWriteString(WinRegRoot.CURRENT_USER, "$SCHEME_KEY\\shell\\open\\command", null, "\"$exe\" \"%1\"")
        } catch (_: Throwable) {
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    actual fun setUpOnFirstRun() {
        registerUrlScheme()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                DesktopBrowsers.refreshUnpackedExtension()
            } catch (_: Throwable) {
            }
        }
        if (!mayChangeSystem) return
        try {
            val marker = DesktopPaths.settings() + "\\browser-integration.done"
            if (winExists(marker)) return
            if (!isEdgeRegistered()) addToEdge()
            winWriteText(marker, "edge")
        } catch (_: Throwable) {
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
