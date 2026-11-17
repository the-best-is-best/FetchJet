package com.michelle.raouf.fetch_jet.feature.archive_extract

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.nativePath
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess

private val PERCENT = Regex("""^(\d{1,3})%""")

actual object ExternalArchiver {
    private val found: String? by lazy { locate() }

    private fun locate(): String? {
        DesktopOs.getEnv("FETCHJET_7Z")?.takeIf { it.isNotBlank() }?.let { return it.takeIf { p -> DesktopOs.exists(p) } }
        val windows = DesktopOs.separator == '\\'
        val candidates = ArrayList<String>()
        if (windows) {
            for (root in listOf("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")) {
                DesktopOs.getEnv(root)?.let { candidates.add(it.trimEnd('\\') + "\\7-Zip\\7z.exe") }
            }
        } else {
            candidates.addAll(listOf("/opt/homebrew/bin/7zz", "/usr/local/bin/7zz", "/opt/homebrew/bin/7z", "/usr/local/bin/7z"))
        }
        val names = if (windows) listOf("7z.exe", "7za.exe", "7zz.exe") else listOf("7zz", "7z", "7za")
        val split = if (windows) ';' else ':'
        DesktopOs.getEnv("PATH").orEmpty().split(split).filter { it.isNotBlank() }.forEach { dir ->
            names.forEach { candidates.add(dir.trimEnd('/', '\\') + DesktopOs.separator + it) }
        }
        return candidates.firstOrNull { DesktopOs.exists(it) }
    }

    actual fun available(): Boolean = found != null

    actual suspend fun extract(archive: String, into: String, password: String?, onProgress: (Float) -> Unit) {
        val tool = found ?: throw ArchiveException("this kind of archive needs 7-Zip: install it from 7-zip.org, then try again")
        val secret = password?.takeIf { it.isNotEmpty() } ?: "fetchjet-no-password-1f8a"
        val command = listOf(tool, "x", "-y", "-bd", "-bsp1", "-bso1", "-bse1", "-p$secret", "-o" + DesktopOs.nativePath(into), DesktopOs.nativePath(archive))
        val lines = ArrayList<String>()
        val result = DesktopOs.runProcess(command, mergeStderr = true, onStdoutLine = { line ->
            val text = line.trim()
            PERCENT.find(text)?.let { onProgress((it.groupValues[1].toInt() / 100f).coerceIn(0f, 1f)) }
            if (text.isNotEmpty() && PERCENT.find(text) == null && lines.size < 400) lines.add(text)
        })
        if (result.exitCode == 0 || result.exitCode == 1) return
        val all = (lines.joinToString("\n") + "\n" + result.stdout).lowercase()
        when {
            "wrong password" in all || "can not open encrypted archive" in all || "cannot open encrypted archive" in all ->
                throw ArchivePasswordException(wrong = password != null)

            "missing volume" in all || "unexpected end of archive" in all && ArchiveExtractor.isVolume(archive.lowercase()) ->
                throw ArchiveException(ArchiveExtractor.MISSING_PART)

            "can not open the file as archive" in all || "cannot open the file as archive" in all ->
                throw ArchiveException("this is not an archive 7-Zip can open")

            else -> throw ArchiveException(lines.lastOrNull { it.startsWith("ERROR", true) || it.contains("error", true) }
                ?.removePrefix("ERROR: ")?.take(200) ?: "7-Zip could not extract it (code ${result.exitCode})")
        }
    }
}
