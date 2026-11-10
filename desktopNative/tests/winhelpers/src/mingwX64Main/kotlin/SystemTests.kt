@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_EXISTING
import com.michelle.raouf.fetch_jet.platform.win.WIN_REG_VIEW_64
import com.michelle.raouf.fetch_jet.platform.win.WinKnownFolder
import com.michelle.raouf.fetch_jet.platform.win.WinRegRoot
import com.michelle.raouf.fetch_jet.platform.win.winAllocatedBytes
import com.michelle.raouf.fetch_jet.platform.win.winCanSuspend
import com.michelle.raouf.fetch_jet.platform.win.winClipboardSequence
import com.michelle.raouf.fetch_jet.platform.win.winClipboardText
import com.michelle.raouf.fetch_jet.platform.win.winCloseFile
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winCreateNamedMutex
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winErrorText
import com.michelle.raouf.fetch_jet.platform.win.winExecutablePath
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winFileSize
import com.michelle.raouf.fetch_jet.platform.win.winFreeBytes
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winKnownFolder
import com.michelle.raouf.fetch_jet.platform.win.winList
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winLocalMinutesOfDay
import com.michelle.raouf.fetch_jet.platform.win.winLocalUtcOffsetMs
import com.michelle.raouf.fetch_jet.platform.win.winMove
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winReadAt
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winRegDeleteTree
import com.michelle.raouf.fetch_jet.platform.win.winRegDeleteValue
import com.michelle.raouf.fetch_jet.platform.win.winRegKeyExists
import com.michelle.raouf.fetch_jet.platform.win.winRegReadString
import com.michelle.raouf.fetch_jet.platform.win.winRegSubKeys
import com.michelle.raouf.fetch_jet.platform.win.winRegWriteString
import com.michelle.raouf.fetch_jet.platform.win.winSetEnv
import com.michelle.raouf.fetch_jet.platform.win.winSetFileSize
import com.michelle.raouf.fetch_jet.platform.win.winSetSparse
import com.michelle.raouf.fetch_jet.platform.win.winTempDir
import com.michelle.raouf.fetch_jet.platform.win.winUserProfile
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import com.michelle.raouf.fetch_jet.platform.win.winWriteText

private const val PROBE_KEY = "Software\\FetchJetProbe\\helpers"
private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

fun systemTests(work: String, options: List<String>) {
    T.guarded("registry") {
        val value = "\"C:\\Program Files\\FetchJet\\FetchJet.exe\" --autostart=true مسار"
        T.check("registry write under HKCU", winRegWriteString(WinRegRoot.CURRENT_USER, PROBE_KEY, "com.michelle.raouf.fetch_jet.probe", value))
        T.check("registry read gives the same text", winRegReadString(WinRegRoot.CURRENT_USER, PROBE_KEY, "com.michelle.raouf.fetch_jet.probe") == value)
        T.check("registry key exists", winRegKeyExists(WinRegRoot.CURRENT_USER, PROBE_KEY))
        T.check("a missing value reads as null", winRegReadString(WinRegRoot.CURRENT_USER, PROBE_KEY, "missing") == null)
        T.check("registry delete", winRegDeleteValue(WinRegRoot.CURRENT_USER, PROBE_KEY, "com.michelle.raouf.fetch_jet.probe"))
        T.check("deleted value reads as null", winRegReadString(WinRegRoot.CURRENT_USER, PROBE_KEY, "com.michelle.raouf.fetch_jet.probe") == null)
        T.check(
            "the probe key is removed again",
            winRegDeleteTree(WinRegRoot.CURRENT_USER, "Software\\FetchJetProbe") && !winRegKeyExists(WinRegRoot.CURRENT_USER, "Software\\FetchJetProbe")
        )
        T.check("the Run key of the user can be opened", winRegKeyExists(WinRegRoot.CURRENT_USER, RUN_KEY))
        val browsers = winRegSubKeys(WinRegRoot.LOCAL_MACHINE, "SOFTWARE\\Clients\\StartMenuInternet", WIN_REG_VIEW_64)
        T.check("installed browsers are listed from HKLM", browsers.isNotEmpty(), browsers.joinToString())
        val version = winRegReadString(WinRegRoot.LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "ProductName")
        T.check("a REG_SZ under HKLM reads", !version.isNullOrEmpty(), version.orEmpty())
        val edge = winRegReadString(
            WinRegRoot.LOCAL_MACHINE,
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\msedge.exe", null
        )
        T.info("default value of App Paths\\msedge.exe: $edge")
    }

    T.guarded("folders") {
        val downloads = winKnownFolder(WinKnownFolder.DOWNLOADS)
        T.check("Downloads known folder", downloads != null && winExists(downloads), downloads.orEmpty())
        for (folder in WinKnownFolder.entries) {
            val path = winKnownFolder(folder)
            T.check("known folder $folder", !path.isNullOrEmpty(), path.orEmpty())
        }
        T.check("temp folder exists", winExists(winTempDir()), winTempDir())
        T.check("USERPROFILE exists", winExists(winUserProfile()), winUserProfile())
        T.check("executable path ends with the program name", winExecutablePath().endsWith("winhelpers.exe"), winExecutablePath())
        T.check("set and get an environment variable with Arabic text", winSetEnv("FJ_PROBE_VAR", "قيمة value") && winGetEnv("FJ_PROBE_VAR") == "قيمة value")
        T.check("a missing environment variable is null", winGetEnv("FJ_PROBE_NOT_SET") == null)
        T.check("error text for code 2", winErrorText(2u).isNotEmpty(), winErrorText(2u))
    }

    T.guarded("clipboard") {
        val sequence = winClipboardSequence()
        val text = winClipboardText()
        T.check("clipboard sequence number reads", sequence >= 0u, "sequence=$sequence")
        T.info("clipboard text read without changing it: ${if (text == null) "no text" else "${text.length} chars"}")
        T.check("the sequence number is stable while nothing changes", winClipboardSequence() == sequence)
    }

    T.guarded("time") {
        val offset = winLocalUtcOffsetMs()
        val expected = options.firstOrNull { it.startsWith("offset=") }?.substringAfter('=')?.toLongOrNull()
        T.check("local UTC offset equals the system's", expected == null || expected == offset, "native=$offset expected=$expected")
        val minutes = winLocalMinutesOfDay()
        val expectedMinutes = options.firstOrNull { it.startsWith("minutes=") }?.substringAfter('=')?.toIntOrNull()
        T.check(
            "local minutes of day equal the system's",
            expectedMinutes == null || kotlin.math.abs(expectedMinutes - minutes) <= 1, "native=$minutes expected=$expectedMinutes"
        )
    }

    T.guarded("mutex and power") {
        val first = winCreateNamedMutex("Local\\FetchJet.Probe.SingleInstance")
        val second = winCreateNamedMutex("Local\\FetchJet.Probe.SingleInstance")
        T.check("named mutex: the first owner is new, the second sees it", first.handle != null && !first.alreadyExisted && second.alreadyExisted)
        second.close()
        first.close()
        val third = winCreateNamedMutex("Local\\FetchJet.Probe.SingleInstance")
        T.check("after closing both the name is free again", !third.alreadyExisted)
        third.close()
        T.check("SetSuspendState is found in powrprof.dll (not called)", winCanSuspend())
    }
}

fun filesTests(work: String) {
    val root = "$work\\files"
    winDeleteRecursively(root)
    val names = listOf("ascii", "عربي ملف", "中文 文件", "emoji 😀🎵", "mixed عربي 中文 😀")
    T.guarded("files") {
        for (name in names) {
            val dir = "$root\\$name"
            val file = "$dir\\$name.txt"
            val ok = winCreateDirectories(dir) && winWriteText(file, "محتوى $name") && winReadText(file) == "محتوى $name" &&
                winList(dir) == listOf("$name.txt") && winMove(file, "$dir\\moved $name.txt") &&
                winFileInfo("$dir\\moved $name.txt")?.size == "محتوى $name".encodeToByteArray().size.toLong()
            T.check("create, write, read, list, move: $name", ok)
        }
        val long = root + "\\" + (1..14).joinToString("\\") { "مجلد-طويل-long-folder-$it" }
        T.check("path longer than 260 characters", long.length > 300 && winCreateDirectories(long) && winWriteText("$long\\ملف.bin", "x") && winList(long) == listOf("ملف.bin"), "${long.length} chars")
        val entries = winListEntries(root).orEmpty()
        T.check("list with details", entries.size == names.size + 1 && entries.all { it.isDirectory })
        T.check("free space on the drive", (winFreeBytes(root) ?: 0L) > 0L, "${winFreeBytes(root)}")
        T.check("missing folder lists as null", winList("$root\\nothing") == null)

        val sparse = "$root\\sparse.bin"
        val handle = winOpenFile(sparse, write = true, disposition = WIN_CREATE_ALWAYS)
        val sparseOk = winSetSparse(handle) && winSetFileSize(handle, 1L shl 31) &&
            winWriteAt(handle, (1L shl 31) - 5, "tail!".encodeToByteArray()) && winWriteAt(handle, 0, "head!".encodeToByteArray())
        val size = winFileSize(handle)
        winCloseFile(handle)
        T.check("2 GB sparse file with writes at both ends", sparseOk && size == 1L shl 31, "allocated=${winAllocatedBytes(sparse)}")
        val reader = winOpenFile(sparse, write = false, disposition = WIN_OPEN_EXISTING)
        val buffer = ByteArray(5)
        val read = winReadAt(reader, (1L shl 31) - 5, buffer)
        val atEnd = winReadAt(reader, 1L shl 31, buffer)
        winCloseFile(reader)
        T.check("read at an offset above 2 GB, and 0 at the end", read == 5 && buffer.decodeToString() == "tail!" && atEnd == 0, "read=$read end=$atEnd")
        T.check("delete everything again", winDeleteRecursively(root) && !winExists(root))
    }
}
