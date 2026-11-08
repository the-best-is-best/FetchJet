package com.michelle.raouf.fetch_jet.core.browser

import java.io.File
import java.net.JarURLConnection
import java.util.jar.JarFile

/**
 * The browsers on this computer and how FetchJet's extension gets into each one.
 *
 * Chromium browsers (Chrome, Edge, Brave, Opera, Vivaldi): the extension ships inside the app
 * (resources `fetchjet-extension/chromium`, copied by desktopApp's `bundleExtension` task) and is
 * unpacked to `~/.fetch_jet/extension/chromium`. A browser never lets another program install an
 * extension that is not in its store, so the user turns on Developer mode and loads that folder
 * once; the fixed `key` in the manifest keeps the same extension ID, and the folder is refreshed
 * at every app start, so the extension updates with the app.
 *
 * Firefox: opens Firefox Add-ons (the extension is published there) in Firefox itself.
 */
internal object DesktopBrowsers {

    private enum class Os { WINDOWS, MAC, LINUX }

    private val os = System.getProperty("os.name").orEmpty().lowercase().let {
        when {
            it.contains("win") -> Os.WINDOWS
            it.contains("mac") -> Os.MAC
            else -> Os.LINUX
        }
    }

    private class Spec(
        val id: String,
        val name: String,
        val firefox: Boolean,
        val extensionsPage: String,
        val downloadUrl: String,
        val windows: List<String>,
        val mac: List<String>,
        val linux: List<String>
    )

    private val home = System.getProperty("user.home").orEmpty()
    private val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
    private val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
    private val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"

    private fun win(vararg relative: String): List<String> = relative.flatMap { r ->
        listOf(programFiles, programFilesX86, localAppData).filter { it.isNotBlank() }
            .map { "$it\\$r" }
    }

    private fun mac(app: String, binary: String): List<String> =
        listOf("/Applications", "$home/Applications").map { "$it/$app.app/Contents/MacOS/$binary" }

    private val specs = listOf(
        Spec(
            "chrome", "Google Chrome", false, "chrome://extensions",
            "https://www.google.com/chrome/",
            win("Google\\Chrome\\Application\\chrome.exe"),
            mac("Google Chrome", "Google Chrome"),
            listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser")
        ),
        Spec(
            "edge", "Microsoft Edge", false, "edge://extensions",
            "https://www.microsoft.com/edge/download",
            win("Microsoft\\Edge\\Application\\msedge.exe"),
            mac("Microsoft Edge", "Microsoft Edge"),
            listOf("microsoft-edge", "microsoft-edge-stable")
        ),
        Spec(
            "firefox", "Firefox", true, FIREFOX_ADDON_URL,
            "https://www.mozilla.org/firefox/new/",
            win("Mozilla Firefox\\firefox.exe"),
            mac("Firefox", "firefox"),
            listOf("firefox")
        ),
        Spec(
            "brave", "Brave", false, "brave://extensions",
            "https://brave.com/download/",
            win("BraveSoftware\\Brave-Browser\\Application\\brave.exe"),
            mac("Brave Browser", "Brave Browser"),
            listOf("brave-browser", "brave")
        ),
        Spec(
            "opera", "Opera", false, "opera://extensions",
            "https://www.opera.com/download",
            win("Programs\\Opera\\opera.exe", "Opera\\opera.exe"),
            mac("Opera", "Opera"),
            listOf("opera")
        ),
        Spec(
            "vivaldi", "Vivaldi", false, "vivaldi://extensions",
            "https://vivaldi.com/download/",
            win("Vivaldi\\Application\\vivaldi.exe"),
            mac("Vivaldi", "Vivaldi"),
            listOf("vivaldi", "vivaldi-stable")
        )
    )

    private fun executable(spec: Spec): String? = when (os) {
        Os.WINDOWS -> spec.windows.firstOrNull { File(it).isFile }
        Os.MAC -> spec.mac.firstOrNull { File(it).isFile }
        Os.LINUX -> spec.linux.firstNotNullOfOrNull { onPath(it) }
    }

    private fun onPath(command: String): String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
            .map { File(it, command) }
            .firstOrNull { it.isFile && it.canExecute() }?.absolutePath

    private val bundled: Boolean by lazy {
        DesktopBrowsers::class.java.classLoader.getResource("$RESOURCE_ROOT/manifest.json") != null
    }

    fun browsers(): List<BrowserInfo> = specs
        .map {
            BrowserInfo(it.id, it.name, executable(it) != null, it.downloadUrl, !it.firefox && bundled)
        }
        .sortedByDescending { it.installed }

    fun addExtension(id: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id } ?: return AddExtensionResult.Failed("unknown browser")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        if (spec.firefox) {
            return if (launch(exe, spec.extensionsPage)) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start ${spec.name}")
        }
        val folder = unpackChromiumExtension()
            ?: return AddExtensionResult.Failed("the extension is not bundled in this build")
        launch(exe, spec.extensionsPage)
        return AddExtensionResult.LoadUnpacked(folder.absolutePath, spec.extensionsPage)
    }

    fun openBrowser(id: String) {
        val spec = specs.firstOrNull { it.id == id } ?: return
        val exe = executable(spec) ?: return
        try {
            if (os == Os.MAC) ProcessBuilder("open", "-a", exe.substringBefore(".app/") + ".app").start()
            else ProcessBuilder(exe).start()
        } catch (_: Exception) {
        }
    }

    /** Saves the signed .xpi under ~/.fetch_jet/extension/firefox and opens it in Firefox. */
    fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult {
        val spec = specs.firstOrNull { it.id == id && it.firefox }
            ?: return AddExtensionResult.Failed("not Firefox")
        val exe = executable(spec) ?: return AddExtensionResult.NotInstalled
        val safeName = fileName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val file = File(home, ".fetch_jet/extension/firefox/$safeName")
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            if (launch(exe, file.absolutePath)) AddExtensionResult.Opened
            else AddExtensionResult.Failed("could not start ${spec.name}")
        } catch (e: Exception) {
            AddExtensionResult.Failed(e.message ?: "could not save the extension")
        }
    }

    private fun launch(exe: String, url: String): Boolean = try {
        if (os == Os.MAC) {
            val app = exe.substringBefore(".app/") + ".app"
            ProcessBuilder("open", "-a", app, url).start()
        } else {
            ProcessBuilder(exe, url).start()
        }
        true
    } catch (_: Exception) {
        false
    }

    fun openFolder(folder: String) {
        try {
            when (os) {
                Os.WINDOWS -> ProcessBuilder("explorer.exe", folder).start()
                Os.MAC -> ProcessBuilder("open", folder).start()
                Os.LINUX -> ProcessBuilder("xdg-open", folder).start()
            }
        } catch (_: Exception) {
        }
    }

    private const val RESOURCE_ROOT = "fetchjet-extension/chromium"

    private val chromiumFolder: File get() = File(home, ".fetch_jet/extension/chromium")

    /** Keeps an already unpacked extension in step with the app (called at app start). */
    fun refreshUnpackedExtension() {
        if (File(chromiumFolder, "manifest.json").isFile) unpackChromiumExtension()
    }

    /** Copies the bundled extension to [chromiumFolder]; null when the build has none. */
    @Synchronized
    fun unpackChromiumExtension(): File? {
        val loader = DesktopBrowsers::class.java.classLoader
        val manifest = loader.getResource("$RESOURCE_ROOT/manifest.json") ?: return null
        val target = chromiumFolder
        return try {
            target.mkdirs()
            when (manifest.protocol) {
                "jar" -> {
                    val jar: JarFile = (manifest.openConnection() as JarURLConnection).jarFile
                    jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.startsWith("$RESOURCE_ROOT/") }
                        .forEach { entry ->
                            val out = File(target, entry.name.removePrefix("$RESOURCE_ROOT/"))
                            out.parentFile?.mkdirs()
                            jar.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        }
                }

                "file" -> {
                    val root = File(manifest.toURI()).parentFile
                    root.walkTopDown().filter { it.isFile }.forEach { f ->
                        val out = File(target, f.relativeTo(root).path)
                        out.parentFile?.mkdirs()
                        f.copyTo(out, overwrite = true)
                    }
                }

                else -> return null
            }
            target
        } catch (_: Exception) {
            null
        }
    }
}
