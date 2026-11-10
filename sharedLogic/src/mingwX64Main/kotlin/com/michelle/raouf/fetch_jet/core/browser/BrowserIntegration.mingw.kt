package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinRegRoot
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winRegDeleteTree
import com.michelle.raouf.fetch_jet.platform.win.winRegReadString
import com.michelle.raouf.fetch_jet.platform.win.winRegWriteString
import com.michelle.raouf.fetch_jet.platform.win.winShellOpen
import com.michelle.raouf.fetch_jet.platform.win.winStartDetached
import com.michelle.raouf.fetch_jet.platform.win.winWriteFile
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlin.concurrent.AtomicInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

internal object DesktopBrowsers {
    private class Spec(
        val id: String,
        val name: String,
        val firefox: Boolean,
        val extensionsPage: String,
        val downloadUrl: String,
        val paths: List<String>,
    )

    private val specs = listOf(
        Spec("chrome", "Google Chrome", false, "chrome://extensions", "https://www.google.com/chrome/", listOf("Google\\Chrome\\Application\\chrome.exe")),
        Spec("edge", "Microsoft Edge", false, "edge://extensions", "https://www.microsoft.com/edge/download", listOf("Microsoft\\Edge\\Application\\msedge.exe")),
        Spec("firefox", "Firefox", true, FIREFOX_ADDON_URL, "https://www.mozilla.org/firefox/new/", listOf("Mozilla Firefox\\firefox.exe")),
        Spec("brave", "Brave", false, "brave://extensions", "https://brave.com/download/", listOf("BraveSoftware\\Brave-Browser\\Application\\brave.exe")),
        Spec("opera", "Opera", false, "opera://extensions", "https://www.opera.com/download", listOf("Programs\\Opera\\opera.exe", "Opera\\opera.exe")),
        Spec("vivaldi", "Vivaldi", false, "vivaldi://extensions", "https://vivaldi.com/download/", listOf("Vivaldi\\Application\\vivaldi.exe")),
    )

    private val unpacking = AtomicInt(0)

    private fun roots(): List<String> = listOf(
        winGetEnv("ProgramFiles") ?: "C:\\Program Files",
        winGetEnv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
        winGetEnv("LOCALAPPDATA").orEmpty(),
    ).filter { it.isNotBlank() }

    private fun executable(spec: Spec): String? {
        for (relative in spec.paths) {
            for (root in roots()) {
                val candidate = "$root\\$relative"
                val info = winFileInfo(candidate)
                if (info != null && !info.isDirectory) return candidate
            }
        }
        return null
    }

    private val bundledFolder: String get() = WinApp.exeDir + "\\extension\\chromium"

    private val bundled: Boolean get() = winExists("$bundledFolder\\manifest.json")

    val chromiumFolder: String get() = WinPaths.settings() + "\\extension\\chromium"

    fun browsers(): List<BrowserInfo> = specs.map {
        BrowserInfo(it.id, it.name, executable(it) != null, it.downloadUrl, hasLocalCopy = !it.firefox && bundled)
    }.sortedByDescending { it.installed }

    fun addExtension(id: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id } ?: return AddExtensionResult.Failed("unknown browser")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        if (spec.firefox) {
            return if (winStartDetached(listOf(exe, FIREFOX_ADDON_URL))) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start Firefox")
        }
        val folder = unpackChromiumExtension()
            ?: return AddExtensionResult.Failed("the extension is not bundled in this build")
        winStartDetached(listOf(exe, spec.extensionsPage))
        return AddExtensionResult.LoadUnpacked(folder, spec.extensionsPage)
    }

    fun openBrowser(id: String) {
        val spec = specs.firstOrNull { it.id == id } ?: return
        val exe = executable(spec) ?: return
        winStartDetached(listOf(exe))
    }

    fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id && it.firefox } ?: return AddExtensionResult.Failed("not Firefox")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        return try {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val folder = WinPaths.settings() + "\\extension\\firefox"
            winCreateDirectories(folder)
            val file = "$folder\\$safeName"
            check(winWriteFile(file, bytes)) { "could not save the extension" }
            if (winStartDetached(listOf(exe, file))) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start Firefox")
        } catch (e: Throwable) {
            AddExtensionResult.Failed(e.message ?: "could not save the extension")
        }
    }

    fun openFolder(folder: String) {
        winShellOpen(folder)
    }

    fun refreshUnpackedExtension() {
        if (winExists("$chromiumFolder\\manifest.json")) unpackChromiumExtension()
    }

    fun unpackChromiumExtension(): String? {
        if (!bundled) return null
        while (!unpacking.compareAndSet(0, 1)) {
        }
        return try {
            if (copyTree(bundledFolder, chromiumFolder)) chromiumFolder else null
        } catch (_: Throwable) {
            null
        } finally {
            unpacking.value = 0
        }
    }

    private fun copyTree(source: String, target: String): Boolean {
        if (!winCreateDirectories(target)) return false
        for (entry in winListEntries(source) ?: return false) {
            val from = "$source\\${entry.name}"
            val to = "$target\\${entry.name}"
            if (entry.isDirectory) {
                if (!copyTree(from, to)) return false
            } else {
                val bytes = winReadFile(from) ?: return false
                if (!winWriteFile(to, bytes)) return false
            }
        }
        return true
    }
}

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
            val marker = WinPaths.settings() + "\\browser-integration.done"
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
