package com.michelle.raouf.fetch_jet.feature.archive_extract

import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.nativePath
import com.michelle.raouf.fetch_jet.platform.desktop.runForOutput
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess

private val PERCENT = Regex("""^(\d{1,3})%""")
private val VERSION = Regex("""7-Zip\D*?(?:\[\d+]\s*)?(\d{1,2})\.(\d{2})""")
private const val NO_PASSWORD = "fetchjet-no-password-1f8a"
private const val SAFE_MAJOR = 25

actual object ExternalArchiver {
    private val found: String? by lazy { locate() }

    private val version: Pair<Int, Int>? by lazy {
        val tool = found ?: return@lazy null
        DesktopOs.runForOutput(listOf(tool), 10_000)?.let { VERSION.find(it) }
            ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .also { v ->
                if (v == null) AppLog.e("7-Zip: version not known ($tool)")
                else if (v.first < SAFE_MAJOR) AppLog.e("7-Zip ${v.first}.${v.second} is older than $SAFE_MAJOR.00: update it from 7-zip.org ($tool)")
            }
    }

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

    internal fun tool(): String = found ?: throw ArchiveException("this kind of archive needs 7-Zip: install it from 7-zip.org, then try again")

    private fun utf8Console(): List<String> = version?.takeIf { it.first >= 21 }?.let { listOf("-sccUTF-8") } ?: emptyList()

    internal fun passwordSwitch(password: String?): List<String> =
        if (password.isNullOrEmpty()) listOf("-p$NO_PASSWORD") else emptyList()

    internal fun passwordInput(password: String?): ByteArray? {
        if (password.isNullOrEmpty()) return null
        val line = password.replace("\r", "").replace("\n", "") + "\n"
        return line.repeat(4).encodeToByteArray()
    }

    internal fun failure(text: String, password: String?, archive: String, exitCode: Int, last: String?): ArchiveException {
        val all = text.lowercase()
        return when {
            "wrong password" in all || "can not open encrypted archive" in all || "cannot open encrypted archive" in all ->
                ArchivePasswordException(wrong = password != null)

            "missing volume" in all || "unexpected end of archive" in all && ArchiveExtractor.isVolume(archive.lowercase()) ->
                ArchiveException(ArchiveExtractor.MISSING_PART)

            "can not open the file as archive" in all || "cannot open the file as archive" in all ->
                ArchiveException("this is not an archive 7-Zip can open")

            else -> ArchiveException(last?.removePrefix("ERROR: ")?.take(200) ?: "7-Zip could not extract it (code $exitCode)")
        }
    }

    internal suspend fun list(archive: String, password: String?): List<ExternalEntry> {
        val tool = tool()
        val command = listOf(tool, "l", "-slt", "-ba", "-bse1") + utf8Console() + passwordSwitch(password) + DesktopOs.nativePath(archive)
        val result = DesktopOs.runProcess(command, mergeStderr = true, stdinBytes = passwordInput(password) ?: ByteArray(0), timeoutMs = 120_000)
        if (result.timedOut) throw ArchiveException("7-Zip took too long to read the archive")
        val entries = parseSlt(result.stdout)
        val usable = result.exitCode == 0 || (result.exitCode == 1 && entries.isNotEmpty())
        if (!usable || (entries.isEmpty() && "error" in result.stdout.lowercase())) {
            val last = result.stdout.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR", true) || it.contains("error", true) }
            throw failure(result.stdout, password, archive, result.exitCode, last)
        }
        return entries
    }

    actual suspend fun extract(archive: String, into: String, password: String?, onProgress: (Float) -> Unit) {
        val tool = tool()
        val command = listOf(tool, "x", "-y", "-bd", "-bsp1", "-bso1", "-bse1") + utf8Console() + passwordSwitch(password) +
            listOf("-o" + DesktopOs.nativePath(into), DesktopOs.nativePath(archive))
        val lines = ArrayList<String>()
        val result = DesktopOs.runProcess(command, mergeStderr = true, stdinBytes = passwordInput(password) ?: ByteArray(0), onStdoutLine = { line ->
            val text = line.trim()
            PERCENT.find(text)?.let { onProgress((it.groupValues[1].toInt() / 100f).coerceIn(0f, 1f)) }
            if (text.isNotEmpty() && PERCENT.find(text) == null && lines.size < 400) lines.add(text)
        })
        if (result.exitCode == 0 || result.exitCode == 1) return
        throw failure(lines.joinToString("\n") + "\n" + result.stdout, password, archive, result.exitCode,
            lines.lastOrNull { it.startsWith("ERROR", true) || it.contains("error", true) })
    }
}

internal fun parseSlt(output: String): List<ExternalEntry> {
    val entries = ArrayList<ExternalEntry>()
    var fields = HashMap<String, String>()
    fun flush() {
        val path = fields["Path"]
        if (path != null) {
            val attributes = fields["Attributes"].orEmpty()
            val unixMode = attributes.substringAfter(' ', "").trim()
            val winAttributes = attributes.substringBefore(' ')
            val isDirectory = fields["Folder"] == "+" || 'D' in winAttributes || unixMode.startsWith("d")
            val isLink = unixMode.startsWith("l") || 'L' in winAttributes ||
                listOf("Symbolic Link", "Hard Link", "Link").any { !fields[it].isNullOrBlank() }
            entries.add(ExternalEntry(path, fields["Size"]?.trim()?.toLongOrNull() ?: 0L, isDirectory, isLink))
        }
        fields = HashMap()
    }
    for (raw in output.lineSequence()) {
        val line = raw.trimEnd('\r')
        if (line.isBlank()) {
            flush()
            continue
        }
        val cut = line.indexOf(" = ")
        if (cut > 0) {
            val key = line.substring(0, cut)
            if (key == "Path" && fields.containsKey("Path")) flush()
            fields[key] = line.substring(cut + 3)
        } else if (line.endsWith(" =")) {
            fields[line.dropLast(2)] = ""
        }
    }
    flush()
    return entries
}

internal actual suspend fun listWithExternalArchiver(archive: String, password: String?): List<ExternalEntry> =
    ExternalArchiver.list(archive, password)
