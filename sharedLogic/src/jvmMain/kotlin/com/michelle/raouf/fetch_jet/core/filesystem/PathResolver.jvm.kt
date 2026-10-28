package com.michelle.raouf.fetch_jet.core.filesystem

import java.io.File

actual class PathResolver actual constructor() {
    private fun isWindows(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("win") == true
    }

    actual fun getDownloadsDirectory(): String {
        val userHome = System.getProperty("user.home") ?: "."
        if (isWindows()) {
            val oneDriveDownloads = File(userHome, "OneDrive/Downloads")
            if (oneDriveDownloads.exists()) return oneDriveDownloads.absolutePath
        }
        val downloadsDir = File(userHome, "Downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        return downloadsDir.absolutePath
    }

    actual fun getDocumentsDirectory(): String {
        val userHome = System.getProperty("user.home") ?: "."
        if (isWindows()) {
            val oneDriveDocs = File(userHome, "OneDrive/Documents")
            if (oneDriveDocs.exists()) return oneDriveDocs.absolutePath
        }
        val docsDir = File(userHome, "Documents")
        if (!docsDir.exists()) docsDir.mkdirs()
        return docsDir.absolutePath
    }

    actual fun getDesktopDirectory(): String {
        val userHome = System.getProperty("user.home") ?: "."
        if (isWindows()) {
            val oneDriveDesktop = File(userHome, "OneDrive/Desktop")
            if (oneDriveDesktop.exists()) return oneDriveDesktop.absolutePath
        }
        val desktopDir = File(userHome, "Desktop")
        if (!desktopDir.exists()) desktopDir.mkdirs()
        return desktopDir.absolutePath
    }

    actual fun getSettingsDirectory(): String {
        val dir = File(System.getProperty("user.home") ?: ".", ".fetch_jet")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    actual fun getTempDirectory(): String {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "FetchJetDownloads")
        if (!tempDir.exists()) tempDir.mkdirs()
        return tempDir.absolutePath
    }

    actual fun sanitizeFileName(fileName: String): String {
        val decoded = decodeUnicodeEscapes(fileName)
        var cleaned = decoded
            .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f]"), "_")
            .trim()
            .trimEnd('.', ' ')          // "name." / "name " are invalid on Windows
        // "." and ".." would escape the destination folder (Content-Disposition is untrusted)
        if (cleaned == "." || cleaned == "..") cleaned = ""
        // Windows reserved device names (CON, NUL, COM1...) even with an extension
        val stemUpper = cleaned.substringBefore('.').uppercase()
        if (stemUpper.matches(Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$"))) cleaned = "_$cleaned"
        val ext = cleaned.substringAfterLast(".", "")
        val nameWithoutExt = if (ext.isNotEmpty()) cleaned.substringBeforeLast(".") else cleaned
        val truncatedName =
            if (nameWithoutExt.length > 80) nameWithoutExt.take(80).trimEnd() else nameWithoutExt
        val finalName = if (ext.isNotEmpty()) "$truncatedName.$ext" else truncatedName
        return if (finalName.isBlank()) "download_file" else finalName
    }

    actual fun chooseDirectory(initialPath: String?): String? {
        return try {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) {
            }
            val chooser = javax.swing.JFileChooser().apply {
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Download Directory"
                if (!initialPath.isNullOrBlank()) {
                    val initialFile = File(initialPath)
                    if (initialFile.exists()) currentDirectory = initialFile
                }
            }
            val result = chooser.showOpenDialog(null)
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    actual fun isOpenFolderSupported(): Boolean = true

    actual fun openDirectoryInExplorer(path: String): Boolean {
        return try {
            val target = File(path)
            val dirToOpen = when {
                target.exists() && target.isDirectory -> target
                target.exists() -> target.parentFile ?: target
                target.parentFile?.exists() == true -> target.parentFile!!
                else -> File(getDownloadsDirectory())
            }
            if (!dirToOpen.exists()) dirToOpen.mkdirs()
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dirToOpen)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds the file that actually exists on disk for a task path.
     * The engine may have renamed the file (yt-dlp merge -> .mkv, "name (1).ext" on
     * duplicates, a different extension from Content-Disposition), so when the exact
     * path is missing we look for a sibling with the same base name.
     */
    private fun resolveExistingFile(filePath: String): File? {
        val file = File(filePath)
        if (file.exists()) return file
        val dir = file.parentFile ?: return null
        if (!dir.isDirectory) return null
        val stem = file.nameWithoutExtension.ifBlank { file.name }
        val candidates = dir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") &&
                    !f.name.endsWith(".temp") && !f.name.endsWith(".aria2") &&
                    (f.nameWithoutExtension.equals(stem, ignoreCase = true) ||
                            f.name.startsWith("$stem.", ignoreCase = true) ||
                            f.name.startsWith("$stem (", ignoreCase = true))
        } ?: return null
        return candidates.maxByOrNull { it.lastModified() }
    }

    private fun isWindowsOs() = isWindows()
    private fun isMacOs() = System.getProperty("os.name")?.lowercase()?.contains("mac") == true

    /**
     * Runs a Windows shell command line verbatim. Java's ProcessBuilder re-quotes every
     * argument that contains a space, which breaks `explorer /select,"C:\a b\f.exe"`
     * and `start "" "C:\a b\f.exe"`, so for these two commands we hand the exact
     * command line to CreateProcess ourselves.
     */
    @Suppress("DEPRECATION")
    private fun runWindowsCommandLine(commandLine: String): Boolean {
        return try {
            Runtime.getRuntime().exec(commandLine)
            true
        } catch (_: Exception) {
            false
        }
    }

    actual fun openFileLocation(filePath: String): Boolean {
        return try {
            val file = resolveExistingFile(filePath) ?: File(filePath)
            when {
                !file.exists() -> openDirectoryInExplorer(file.parent ?: filePath)

                isWindowsOs() -> {
                    val desktop = runCatching { java.awt.Desktop.getDesktop() }.getOrNull()
                    val viaDesktop = desktop != null &&
                            desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR) &&
                            runCatching { desktop.browseFileDirectory(file) }.isSuccess
                    // explorer needs the path quoted as ONE token: /select,"<path>"
                    viaDesktop ||
                            runWindowsCommandLine("explorer.exe /select,\"${file.absolutePath}\"") ||
                        openDirectoryInExplorer(file.parent ?: filePath)
                }

                isMacOs() -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                    true
                }

                else -> {
                    val desktop = runCatching { java.awt.Desktop.getDesktop() }.getOrNull()
                    if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) {
                        runCatching { desktop.browseFileDirectory(file) }.isSuccess ||
                                openDirectoryInExplorer(file.parent ?: filePath)
                    } else {
                        try {
                            val proc = ProcessBuilder(
                                "dbus-send", "--session", "--dest=org.freedesktop.FileManager1",
                                "--type=method_call", "/org/freedesktop/FileManager1",
                                "org.freedesktop.FileManager1.ShowItems",
                                "array:string:file://${file.absolutePath}", "string:"
                            ).start()
                            proc.waitFor() == 0 || openDirectoryInExplorer(file.parent ?: filePath)
                        } catch (_: Exception) {
                            openDirectoryInExplorer(file.parent ?: filePath)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            openDirectoryInExplorer(filePath)
        }
    }

    actual fun openFile(filePath: String): Boolean {
        val file = resolveExistingFile(filePath) ?: return false
        if (file.isDirectory) return openDirectoryInExplorer(file.absolutePath)

        // 1. java.awt.Desktop (ShellExecute "open" / LSOpen / xdg-open).
        //    It throws for files with no associated app, for .exe/.msi that need UAC
        //    elevation, for .lnk/.url and some others, so a failure here must fall
        //    through to the OS shell instead of returning false.
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(file)
                    return true
                }
            }
        } catch (_: Exception) {
            // fall through
        }

        // 2. OS shell fallback.
        return try {
            when {
                isWindowsOs() -> {
                    // `start "" "<path>"` goes through the shell: handles .exe/.msi (with the
                    // UAC prompt), files whose association is a Store app, and unknown types
                    // (Windows shows its "How do you want to open this file?" dialog).
                    // Each pre-quoted argument is passed to CreateProcess verbatim.
                    ProcessBuilder(
                        "cmd.exe", "/c", "start", "\"\"", "\"${file.absolutePath}\""
                    ).start()
                    true
                }

                isMacOs() -> {
                    ProcessBuilder("open", file.absolutePath).start()
                    true
                }

                else -> {
                    ProcessBuilder("xdg-open", file.absolutePath).start()
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    actual fun getAvailableDirectories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val dl = File(getDownloadsDirectory())
        val doc = File(getDocumentsDirectory())
        val dt = File(getDesktopDirectory())
        if (dl.exists() || dl.mkdirs()) list.add("Downloads" to dl.absolutePath)
        if (doc.exists() || doc.mkdirs()) list.add("Documents" to doc.absolutePath)
        if (dt.exists() || dt.mkdirs()) list.add("Desktop" to dt.absolutePath)
        return list
    }

    actual fun listSubdirectories(path: String): List<String> {
        return listSubdirectories(path, false)
    }

    actual fun listSubdirectories(path: String, includeHidden: Boolean): List<String> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles { f -> f.isDirectory && (includeHidden || !f.name.startsWith(".")) }
                ?.map { it.name }
                ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    actual fun createSubdirectory(parentPath: String, name: String): String? {
        return try {
            val safeName = sanitizeFileName(name)
            if (safeName.isBlank()) return null
            val newDir = File(parentPath, safeName)
            if (newDir.exists() || newDir.mkdirs()) {
                newDir.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    actual fun getParentDirectory(path: String): String? {
        return try {
            val file = File(path)
            file.parentFile?.takeIf { it.exists() && it.canRead() }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
        return regex.replace(input) { m ->
            try {
                val codePoint = m.groupValues[1].toInt(16)
                codePoint.toChar().toString()
            } catch (_: Exception) {
                m.value
            }
        }
    }
}