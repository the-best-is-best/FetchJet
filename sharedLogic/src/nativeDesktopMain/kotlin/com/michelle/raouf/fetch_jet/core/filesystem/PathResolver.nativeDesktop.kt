package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.platform.desktop.OsUserFolder
import com.michelle.raouf.fetch_jet.platform.desktop.isDirectory
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.desktop.nativePath
import kotlin.concurrent.Volatile

internal object DesktopPaths {
    @Volatile
    private var settingsFor: String = ""

    @Volatile
    private var settingsReady: String = ""

    @Volatile
    private var tempReady: String = ""

    private val sep: Char get() = DesktopOs.separator

    fun downloads(): String {
        val override = DesktopOs.getEnv("FETCHJET_DOWNLOADS")?.takeIf { it.isNotBlank() }
        if (override != null) {
            val path = DesktopOs.nativePath(override)
            if (!DesktopOs.isDirectory(path)) DesktopOs.createDirectories(path)
            return path
        }
        return DesktopOs.userFolder(OsUserFolder.DOWNLOADS)
    }

    fun documents(): String = DesktopOs.userFolder(OsUserFolder.DOCUMENTS)

    fun desktop(): String = DesktopOs.userFolder(OsUserFolder.DESKTOP)

    fun settings(): String {
        val home = DesktopApp.appHome
        if (settingsFor == home && settingsReady.isNotEmpty()) return settingsReady
        if (!DesktopOs.isDirectory(home)) DesktopOs.createDirectories(home)
        settingsFor = home
        settingsReady = home
        return home
    }

    fun temp(): String {
        val isolated = DesktopApp.homeOverride != null || DesktopApp.isDevelopmentBuild
        val path = if (isolated) {
            DesktopOs.join(DesktopOs.join(settings(), "tmp"), "FetchJetDownloads")
        } else {
            DesktopOs.join(DesktopOs.tempDir(), "FetchJetDownloads")
        }
        if (tempReady != path) {
            if (!DesktopOs.isDirectory(path)) DesktopOs.createDirectories(path)
            tempReady = path
        }
        return path
    }

    fun parentOf(path: String): String? = if (sep == '\\') windowsParentOf(path) else posixParentOf(path)

    private fun windowsParentOf(path: String): String? {
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

    private fun posixParentOf(path: String): String? {
        val text = path.trimEnd('/')
        if (text.isEmpty()) return null
        val cut = text.lastIndexOf('/')
        if (cut < 0) return null
        return if (cut == 0) "/" else text.substring(0, cut)
    }

    fun nameOf(path: String): String = DesktopOs.nativePath(path).substringAfterLast(sep)
}

actual class PathResolver actual constructor() {
    actual fun getDownloadsDirectory(): String = DesktopPaths.downloads()

    actual fun getDocumentsDirectory(): String = DesktopPaths.documents()

    actual fun getDesktopDirectory(): String = DesktopPaths.desktop()

    actual fun getTempDirectory(): String = DesktopPaths.temp()

    actual fun getSettingsDirectory(): String = DesktopPaths.settings()

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
            val info = DesktopOs.fileInfo(path)
            val parent = DesktopPaths.parentOf(path)
            val target = when {
                info != null && info.isDirectory -> path
                info != null -> parent ?: getDownloadsDirectory()
                parent != null && DesktopOs.isDirectory(parent) -> parent
                else -> getDownloadsDirectory()
            }
            if (!DesktopOs.isDirectory(target)) DesktopOs.createDirectories(target)
            DesktopOs.openExternal(DesktopOs.fullPath(target))
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveExistingFile(filePath: String): String? {
        if (filePath.isBlank()) return null
        if (DesktopOs.fileInfo(filePath) != null) return filePath
        val dir = DesktopPaths.parentOf(filePath) ?: return null
        if (!DesktopOs.isDirectory(dir)) return null
        val name = DesktopPaths.nameOf(filePath)
        val stem = stemOf(name).ifBlank { name }
        val candidates = DesktopOs.listEntries(dir)?.filter { entry ->
            !entry.isDirectory && !entry.name.endsWith(".part") && !entry.name.endsWith(".ytdl") &&
                !entry.name.endsWith(".temp") && !entry.name.endsWith(".aria2") &&
                (stemOf(entry.name).equals(stem, ignoreCase = true) ||
                    entry.name.startsWith("$stem.", ignoreCase = true) ||
                    entry.name.startsWith("$stem (", ignoreCase = true))
        } ?: return null
        val newest = candidates.maxByOrNull { it.lastWriteEpochMs } ?: return null
        return DesktopOs.join(dir, newest.name)
    }

    private fun stemOf(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    actual fun openFileLocation(filePath: String): Boolean {
        return try {
            val file = resolveExistingFile(filePath) ?: filePath
            if (DesktopOs.fileInfo(file) == null) {
                return openDirectoryInExplorer(DesktopPaths.parentOf(file) ?: filePath)
            }
            if (DesktopOs.revealInFileManager(file)) true
            else openDirectoryInExplorer(DesktopPaths.parentOf(file) ?: file)
        } catch (_: Throwable) {
            false
        }
    }

    actual fun openFile(filePath: String): Boolean {
        return try {
            val file = resolveExistingFile(filePath) ?: return false
            if (DesktopOs.isDirectory(file)) return openDirectoryInExplorer(file)
            val full = DesktopOs.fullPath(file)
            val directory = DesktopPaths.parentOf(full)
            DesktopOs.openFile(full, directory)
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
            if (DesktopOs.isDirectory(path) || DesktopOs.createDirectories(path)) result.add(label to path)
        }
        add("Downloads", getDownloadsDirectory())
        add("Documents", getDocumentsDirectory())
        add("Desktop", getDesktopDirectory())
        return result
    }

    actual fun listSubdirectories(path: String): List<String> = listSubdirectories(path, false)

    actual fun listSubdirectories(path: String, includeHidden: Boolean): List<String> {
        return try {
            if (!DesktopOs.isDirectory(path)) return emptyList()
            DesktopOs.listEntries(path)
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
            val target = DesktopOs.fullPath(DesktopOs.join(DesktopOs.nativePath(parentPath), safeName))
            if (DesktopOs.isDirectory(target) || DesktopOs.createDirectories(target)) target else null
        } catch (_: Throwable) {
            null
        }
    }

    actual fun getParentDirectory(path: String): String? {
        return try {
            DesktopPaths.parentOf(DesktopOs.fullPath(path))?.takeIf { DesktopOs.isDirectory(it) }
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
