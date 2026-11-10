package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.win.HostHooks
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinInstallKind
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winRevealInExplorer
import com.michelle.raouf.fetch_jet.platform.win.winShellExecute
import com.michelle.raouf.fetch_jet.platform.win.winTempDir
import com.michelle.raouf.fetch_jet.platform.win.winUserProfile
import kotlin.concurrent.Volatile

internal object WinPaths {
    @Volatile
    private var settingsFor: String = ""

    @Volatile
    private var settingsReady: String = ""

    @Volatile
    private var tempReady: String = ""

    private fun userFolder(name: String): String {
        val home = winUserProfile()
        val oneDrive = "$home\\OneDrive\\$name"
        if (winIsDirectory(oneDrive)) return oneDrive
        val plain = "$home\\$name"
        if (!winIsDirectory(plain)) winCreateDirectories(plain)
        return plain
    }

    fun downloads(): String {
        val override = winGetEnv("FETCHJET_DOWNLOADS")?.takeIf { it.isNotBlank() }
        if (override != null) {
            val path = override.replace('/', '\\').trimEnd('\\')
            if (!winIsDirectory(path)) winCreateDirectories(path)
            return path
        }
        return userFolder("Downloads")
    }

    fun documents(): String = userFolder("Documents")

    fun desktop(): String = userFolder("Desktop")

    fun settings(): String {
        val home = WinApp.appHome
        if (settingsFor == home && settingsReady.isNotEmpty()) return settingsReady
        if (!winIsDirectory(home)) winCreateDirectories(home)
        settingsFor = home
        settingsReady = home
        return home
    }

    fun temp(): String {
        val isolated = WinApp.homeOverride != null || WinApp.kind == WinInstallKind.DEVELOPMENT
        val path = if (isolated) settings() + "\\tmp\\FetchJetDownloads" else winTempDir() + "\\FetchJetDownloads"
        if (tempReady != path) {
            if (!winIsDirectory(path)) winCreateDirectories(path)
            tempReady = path
        }
        return path
    }

    fun parentOf(path: String): String? {
        var text = path.replace('/', '\\')
        while (text.length > 3 && text.endsWith('\\')) text = text.dropLast(1)
        if (text.length <= 3 && text.getOrNull(1) == ':') return null
        val cut = text.lastIndexOf('\\')
        if (cut < 0) return null
        if (text.startsWith("\\\\")) {
            val parts = text.substring(2).split('\\').filter { it.isNotEmpty() }
            if (parts.size <= 2) return null
        }
        val parent = text.substring(0, cut)
        return if (parent.length == 2 && parent[1] == ':') "$parent\\" else parent
    }

    fun nameOf(path: String): String = path.replace('/', '\\').trimEnd('\\').substringAfterLast('\\')
}

actual class PathResolver actual constructor() {
    actual fun getDownloadsDirectory(): String = WinPaths.downloads()

    actual fun getDocumentsDirectory(): String = WinPaths.documents()

    actual fun getDesktopDirectory(): String = WinPaths.desktop()

    actual fun getTempDirectory(): String = WinPaths.temp()

    actual fun getSettingsDirectory(): String = WinPaths.settings()

    actual fun sanitizeFileName(fileName: String): String {
        val decoded = decodeUnicodeEscapes(fileName)
        var cleaned = decoded
            .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f]"), "_")
            .trim()
            .trimEnd('.', ' ')
        if (cleaned == "." || cleaned == "..") cleaned = ""
        val stemUpper = cleaned.substringBefore('.').uppercase()
        if (stemUpper.matches(Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$"))) cleaned = "_$cleaned"
        val ext = cleaned.substringAfterLast(".", "")
        val nameWithoutExt = if (ext.isNotEmpty()) cleaned.substringBeforeLast(".") else cleaned
        val truncatedName =
            if (nameWithoutExt.length > 80) nameWithoutExt.take(80).trimEnd() else nameWithoutExt
        val finalName = if (ext.isNotEmpty()) "$truncatedName.$ext" else truncatedName
        return if (finalName.isBlank()) "download_file" else finalName
    }

    actual fun isOpenFolderSupported(): Boolean = true

    actual fun openDirectoryInExplorer(path: String): Boolean {
        return try {
            val info = winFileInfo(path)
            val parent = WinPaths.parentOf(path)
            val target = when {
                info != null && info.isDirectory -> path
                info != null -> parent ?: getDownloadsDirectory()
                parent != null && winIsDirectory(parent) -> parent
                else -> getDownloadsDirectory()
            }
            if (!winIsDirectory(target)) winCreateDirectories(target)
            winShellExecute("open", winFullPath(target)) > 32
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveExistingFile(filePath: String): String? {
        if (filePath.isBlank()) return null
        if (winFileInfo(filePath) != null) return filePath
        val dir = WinPaths.parentOf(filePath) ?: return null
        if (!winIsDirectory(dir)) return null
        val name = WinPaths.nameOf(filePath)
        val stem = stemOf(name).ifBlank { name }
        val candidates = winListEntries(dir)?.filter { entry ->
            !entry.isDirectory && !entry.name.endsWith(".part") && !entry.name.endsWith(".ytdl") &&
                !entry.name.endsWith(".temp") && !entry.name.endsWith(".aria2") &&
                (stemOf(entry.name).equals(stem, ignoreCase = true) ||
                    entry.name.startsWith("$stem.", ignoreCase = true) ||
                    entry.name.startsWith("$stem (", ignoreCase = true))
        } ?: return null
        val newest = candidates.maxByOrNull { it.lastWriteEpochMs } ?: return null
        return "$dir\\${newest.name}"
    }

    private fun stemOf(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    actual fun openFileLocation(filePath: String): Boolean {
        return try {
            val file = resolveExistingFile(filePath) ?: filePath
            if (winFileInfo(file) == null) {
                return openDirectoryInExplorer(WinPaths.parentOf(file) ?: filePath)
            }
            if (winRevealInExplorer(file)) true
            else openDirectoryInExplorer(WinPaths.parentOf(file) ?: file)
        } catch (_: Throwable) {
            false
        }
    }

    actual fun openFile(filePath: String): Boolean {
        return try {
            val file = resolveExistingFile(filePath) ?: return false
            if (winIsDirectory(file)) return openDirectoryInExplorer(file)
            val full = winFullPath(file)
            val directory = WinPaths.parentOf(full)
            val code = winShellExecute(null, full, null, directory)
            if (code > 32) return true
            if (code == 31L || code == 27L) return winShellExecute("openas", full, null, directory) > 32
            false
        } catch (_: Throwable) {
            false
        }
    }

    actual fun fileExists(filePath: String): Boolean = resolveExistingFile(filePath) != null

    actual fun chooseDirectory(initialPath: String?): String? =
        try {
            HostHooks.pickFolder?.invoke("Select Download Directory", initialPath)
        } catch (_: Throwable) {
            null
        }

    actual fun getAvailableDirectories(): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        fun add(label: String, path: String) {
            if (winIsDirectory(path) || winCreateDirectories(path)) result.add(label to path)
        }
        add("Downloads", getDownloadsDirectory())
        add("Documents", getDocumentsDirectory())
        add("Desktop", getDesktopDirectory())
        return result
    }

    actual fun listSubdirectories(path: String): List<String> = listSubdirectories(path, false)

    actual fun listSubdirectories(path: String, includeHidden: Boolean): List<String> {
        return try {
            if (!winIsDirectory(path)) return emptyList()
            winListEntries(path)
                ?.filter { it.isDirectory && (includeHidden || !it.name.startsWith(".")) }
                ?.map { it.name }
                ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    actual fun createSubdirectory(parentPath: String, name: String): String? {
        return try {
            val safeName = sanitizeFileName(name)
            if (safeName.isBlank()) return null
            val target = winFullPath(parentPath.replace('/', '\\').trimEnd('\\') + "\\" + safeName)
            if (winIsDirectory(target) || winCreateDirectories(target)) target else null
        } catch (_: Throwable) {
            null
        }
    }

    actual fun getParentDirectory(path: String): String? {
        return try {
            WinPaths.parentOf(winFullPath(path))?.takeIf { winIsDirectory(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
        return regex.replace(input) { m ->
            try {
                m.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                m.value
            }
        }
    }
}
