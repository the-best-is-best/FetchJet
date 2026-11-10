package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.copyFile
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.writeBytes
import com.michelle.raouf.fetch_jet.platform.desktop.findOnPath
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached
import kotlin.concurrent.AtomicInt

internal object DesktopBrowsers {
    private class Spec(
        val id: String,
        val name: String,
        val firefox: Boolean,
        val extensionsPage: String,
        val downloadUrl: String,
        val windows: List<String>,
        val linux: List<String>,
    )

    private val specs = listOf(
        Spec(
            "chrome", "Google Chrome", false, "chrome://extensions", "https://www.google.com/chrome/",
            listOf("Google\\Chrome\\Application\\chrome.exe"),
            listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"),
        ),
        Spec(
            "edge", "Microsoft Edge", false, "edge://extensions", "https://www.microsoft.com/edge/download",
            listOf("Microsoft\\Edge\\Application\\msedge.exe"),
            listOf("microsoft-edge", "microsoft-edge-stable"),
        ),
        Spec(
            "firefox", "Firefox", true, FIREFOX_ADDON_URL, "https://www.mozilla.org/firefox/new/",
            listOf("Mozilla Firefox\\firefox.exe"),
            listOf("firefox"),
        ),
        Spec(
            "brave", "Brave", false, "brave://extensions", "https://brave.com/download/",
            listOf("BraveSoftware\\Brave-Browser\\Application\\brave.exe"),
            listOf("brave-browser", "brave"),
        ),
        Spec(
            "opera", "Opera", false, "opera://extensions", "https://www.opera.com/download",
            listOf("Programs\\Opera\\opera.exe", "Opera\\opera.exe"),
            listOf("opera"),
        ),
        Spec(
            "vivaldi", "Vivaldi", false, "vivaldi://extensions", "https://vivaldi.com/download/",
            listOf("Vivaldi\\Application\\vivaldi.exe"),
            listOf("vivaldi", "vivaldi-stable"),
        ),
    )

    private val onWindows: Boolean get() = DesktopOs.separator == '\\'

    private val unpacking = AtomicInt(0)

    private fun isFile(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

    private fun windowsRoots(): List<String> = listOf(
        DesktopOs.getEnv("ProgramFiles") ?: "C:\\Program Files",
        DesktopOs.getEnv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
        DesktopOs.getEnv("LOCALAPPDATA").orEmpty(),
    ).filter { it.isNotBlank() }

    private fun executable(spec: Spec): String? =
        if (onWindows) {
            spec.windows.firstNotNullOfOrNull { relative ->
                windowsRoots().map { DesktopOs.join(it, relative) }.firstOrNull { isFile(it) }
            }
        } else {
            spec.linux.firstNotNullOfOrNull { DesktopOs.findOnPath(it) }
        }

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
            return if (DesktopOs.startDetached(listOf(exe, FIREFOX_ADDON_URL))) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start Firefox")
        }
        val folder = unpackChromiumExtension()
            ?: return AddExtensionResult.Failed("the extension is not bundled in this build")
        DesktopOs.startDetached(listOf(exe, spec.extensionsPage))
        return AddExtensionResult.LoadUnpacked(folder, spec.extensionsPage)
    }

    fun openBrowser(id: String) {
        val spec = specs.firstOrNull { it.id == id } ?: return
        val exe = executable(spec) ?: return
        DesktopOs.startDetached(listOf(exe))
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
            if (DesktopOs.startDetached(listOf(exe, file))) AddExtensionResult.Opened
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
