package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.copyFile
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.writeBytes
import com.michelle.raouf.fetch_jet.platform.desktop.join
import kotlin.concurrent.AtomicInt

internal object DesktopBrowsers {
    private class Spec(
        val id: String,
        val name: String,
        val firefox: Boolean,
        val extensionsPage: String,
        val downloadUrl: String,
        val paths: BrowserPaths,
    )

    private val specs = listOf(
        Spec(
            "chrome", "Google Chrome", false, "chrome://extensions", "https://www.google.com/chrome/",
            BrowserPaths(
                windows = listOf("Google\\Chrome\\Application\\chrome.exe"),
                mac = listOf("Google Chrome/Google Chrome"),
                linux = listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"),
            ),
        ),
        Spec(
            "edge", "Microsoft Edge", false, "edge://extensions", "https://www.microsoft.com/edge/download",
            BrowserPaths(
                windows = listOf("Microsoft\\Edge\\Application\\msedge.exe"),
                mac = listOf("Microsoft Edge/Microsoft Edge"),
                linux = listOf("microsoft-edge", "microsoft-edge-stable"),
            ),
        ),
        Spec(
            "firefox", "Firefox", true, FIREFOX_ADDON_URL, "https://www.mozilla.org/firefox/new/",
            BrowserPaths(
                windows = listOf("Mozilla Firefox\\firefox.exe"),
                mac = listOf("Firefox/firefox"),
                linux = listOf("firefox"),
            ),
        ),
        Spec(
            "brave", "Brave", false, "brave://extensions", "https://brave.com/download/",
            BrowserPaths(
                windows = listOf("BraveSoftware\\Brave-Browser\\Application\\brave.exe"),
                mac = listOf("Brave Browser/Brave Browser"),
                linux = listOf("brave-browser", "brave"),
            ),
        ),
        Spec(
            "opera", "Opera", false, "opera://extensions", "https://www.opera.com/download",
            BrowserPaths(
                windows = listOf("Programs\\Opera\\opera.exe", "Opera\\opera.exe"),
                mac = listOf("Opera/Opera"),
                linux = listOf("opera"),
            ),
        ),
        Spec(
            "vivaldi", "Vivaldi", false, "vivaldi://extensions", "https://vivaldi.com/download/",
            BrowserPaths(
                windows = listOf("Vivaldi\\Application\\vivaldi.exe"),
                mac = listOf("Vivaldi/Vivaldi"),
                linux = listOf("vivaldi", "vivaldi-stable"),
            ),
        ),
        Spec(
            "arc", "Arc", false, "arc://extensions", "https://arc.net/",
            BrowserPaths(mac = listOf("Arc/Arc")),
        ),
    ).filter { BrowserLocator.hasCandidates(it.paths) }

    private val unpacking = AtomicInt(0)

    private fun executable(spec: Spec): String? = BrowserLocator.find(spec.paths)

    private val bundledFolder: String get() = DesktopOs.join(DesktopOs.join(DesktopApp.exeDir, "extension"), "chromium")

    private val bundled: Boolean get() = DesktopOs.exists(DesktopOs.join(bundledFolder, "manifest.json"))

    val chromiumFolder: String get() = DesktopOs.join(DesktopOs.join(DesktopPaths.settings(), "extension"), "chromium")

    fun browsers(): List<BrowserInfo> = specs.map {
        BrowserInfo(it.id, it.name, executable(it) != null, it.downloadUrl, hasLocalCopy = !it.firefox && bundled)
    }.sortedByDescending { it.installed }

    fun addExtension(id: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id } ?: return AddExtensionResult.Failed("unknown browser")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        if (spec.firefox) {
            return if (BrowserLocator.start(exe, listOf(FIREFOX_ADDON_URL))) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start Firefox")
        }
        val folder = unpackChromiumExtension()
            ?: return AddExtensionResult.Failed("the extension is not bundled in this build")
        BrowserLocator.start(exe, listOf(spec.extensionsPage))
        return AddExtensionResult.LoadUnpacked(folder, spec.extensionsPage)
    }

    fun openBrowser(id: String) {
        val spec = specs.firstOrNull { it.id == id } ?: return
        val exe = executable(spec) ?: return
        BrowserLocator.start(exe, emptyList())
    }

    fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id && it.firefox } ?: return AddExtensionResult.Failed("not Firefox")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        return try {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val folder = DesktopOs.join(DesktopOs.join(DesktopPaths.settings(), "extension"), "firefox")
            DesktopOs.createDirectories(folder)
            val file = DesktopOs.join(folder, safeName)
            if (!DesktopOs.writeBytes(file, bytes)) throw IllegalStateException("could not save the extension")
            if (BrowserLocator.start(exe, listOf(file))) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start Firefox")
        } catch (e: Throwable) {
            AddExtensionResult.Failed(e.message ?: "could not save the extension")
        }
    }

    fun openFolder(folder: String) {
        DesktopOs.openExternal(folder)
    }

    fun refreshUnpackedExtension() {
        if (DesktopOs.exists(DesktopOs.join(chromiumFolder, "manifest.json"))) unpackChromiumExtension()
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
        if (!DesktopOs.createDirectories(target)) return false
        for (entry in DesktopOs.listEntries(source) ?: return false) {
            val from = DesktopOs.join(source, entry.name)
            val to = DesktopOs.join(target, entry.name)
            if (entry.isDirectory) {
                if (!copyTree(from, to)) return false
            } else {
                if (!DesktopOs.copyFile(from, to)) return false
            }
        }
        return true
    }
}
