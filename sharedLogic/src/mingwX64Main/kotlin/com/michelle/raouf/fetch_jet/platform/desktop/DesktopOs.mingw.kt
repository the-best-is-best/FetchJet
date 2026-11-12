@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.desktop

import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_NEW
import com.michelle.raouf.fetch_jet.platform.win.winSetFileSize
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_EXISTING
import com.michelle.raouf.fetch_jet.platform.win.winReadAt
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.WinInstallKind
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winClipboardSequence
import com.michelle.raouf.fetch_jet.platform.win.winClipboardText
import com.michelle.raouf.fetch_jet.platform.win.winCloseFile
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winDpapiProtect
import com.michelle.raouf.fetch_jet.platform.win.winDpapiUnprotect
import com.michelle.raouf.fetch_jet.platform.win.winEcdsaP256Verify
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winFileSize
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winLocalMinutesOfDay
import com.michelle.raouf.fetch_jet.platform.win.winLocalUtcOffsetMs
import com.michelle.raouf.fetch_jet.platform.win.winMemoryUse
import com.michelle.raouf.fetch_jet.platform.win.winMove
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winRevealInExplorer
import com.michelle.raouf.fetch_jet.platform.win.WinException
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winSetClipboardText
import com.michelle.raouf.fetch_jet.platform.win.winSetEnv
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winShellExecute
import com.michelle.raouf.fetch_jet.platform.win.winShellOpen
import com.michelle.raouf.fetch_jet.platform.win.winTempDir
import com.michelle.raouf.fetch_jet.platform.win.winThreadId
import com.michelle.raouf.fetch_jet.platform.win.winUserProfile
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.windows.GetCurrentProcess
import platform.windows.GetLastInputInfo
import platform.windows.GetLocalTime
import platform.windows.GetTickCount
import platform.windows.LASTINPUTINFO
import platform.windows.SYSTEMTIME
import platform.windows.TerminateProcess

actual object DesktopOs {
    actual val name: String = "Windows"
    actual val separator: Char = '\\'
    actual val nullDevice: String = "NUL"
    actual val curlCommand: String = "curl.exe"

    actual fun getEnv(name: String): String? = winGetEnv(name)
    actual fun setEnv(name: String, value: String?): Boolean = winSetEnv(name, value)
    actual fun threadId(): ULong = winThreadId().toULong()
    actual fun memoryUse(): OsMemoryUse? = winMemoryUse()?.let { OsMemoryUse(it.privateBytes, it.workingSetBytes) }

    actual fun terminateProcess(exitCode: Int) {
        TerminateProcess(GetCurrentProcess(), exitCode.toUInt())
    }

    actual fun localMinutesOfDay(): Int = winLocalMinutesOfDay()
    actual fun localUtcOffsetMs(): Long = winLocalUtcOffsetMs()

    actual fun localTimeStamp(): String = memScoped {
        val now = alloc<SYSTEMTIME>()
        GetLocalTime(now.ptr)
        fun two(value: UShort) = value.toString().padStart(2, '0')
        "${now.wYear}-${two(now.wMonth)}-${two(now.wDay)} ${two(now.wHour)}:${two(now.wMinute)}:${two(now.wSecond)}"
    }

    actual fun userIdleMs(): Long? = memScoped {
        val info = alloc<LASTINPUTINFO>()
        info.cbSize = sizeOf<LASTINPUTINFO>().toUInt()
        if (GetLastInputInfo(info.ptr) == 0) null else (GetTickCount() - info.dwTime).toLong()
    }

    actual fun clipboardText(): String? = winClipboardText()
    actual fun setClipboardText(text: String): Boolean = winSetClipboardText(text)
    actual fun clipboardSequence(): Long = winClipboardSequence().toLong()

    actual fun openExternal(target: String): Boolean = winShellOpen(target)

    actual fun openFile(path: String, directory: String?): Boolean {
        val code = winShellExecute(null, path, null, directory)
        if (code > 32) return true
        if (code == 31L || code == 27L) return winShellExecute("openas", path, null, directory) > 32
        return false
    }

    actual fun revealInFileManager(path: String): Boolean = winRevealInExplorer(path)

    actual fun userFolder(folder: OsUserFolder): String {
        val name = when (folder) {
            OsUserFolder.DOWNLOADS -> "Downloads"
            OsUserFolder.DOCUMENTS -> "Documents"
            OsUserFolder.DESKTOP -> "Desktop"
        }
        val home = winUserProfile()
        val oneDrive = "$home\\OneDrive\\$name"
        if (winIsDirectory(oneDrive)) return oneDrive
        val plain = "$home\\$name"
        if (!winIsDirectory(plain)) winCreateDirectories(plain)
        return plain
    }

    actual fun tempDir(): String = winTempDir()

    actual fun fileInfo(path: String): OsFileInfo? =
        winFileInfo(path)?.let { OsFileInfo(it.size, it.isDirectory, it.lastWriteEpochMs) }

    actual fun listEntries(dir: String): List<OsDirEntry>? =
        winListEntries(dir)?.map { OsDirEntry(it.name, it.isDirectory, it.size, it.lastWriteEpochMs) }

    actual fun createDirectories(dir: String): Boolean = winCreateDirectories(dir)
    actual fun fullPath(path: String): String = winFullPath(path)
    actual fun readText(path: String): String? = winReadText(path)
    actual fun writeText(path: String, text: String): Boolean = winWriteText(path, text)

    actual fun appendBytes(path: String, bytes: ByteArray): Boolean {
        val handle = winOpenFile(path, write = true, disposition = WIN_OPEN_ALWAYS) ?: return false
        try {
            val end = winFileSize(handle)
            return end >= 0 && winWriteAt(handle, end, bytes)
        } finally {
            winCloseFile(handle)
        }
    }

    actual fun openFile(path: String, write: Boolean): OsFile? =
        openFile(path, write, if (write) OsOpen.TRUNCATE else OsOpen.MUST_EXIST)

    actual fun openFile(path: String, write: Boolean, mode: OsOpen): OsFile? {
        val disposition = when (mode) {
            OsOpen.MUST_EXIST -> WIN_OPEN_EXISTING
            OsOpen.MUST_CREATE -> WIN_CREATE_NEW
            OsOpen.OPEN_OR_CREATE -> WIN_OPEN_ALWAYS
            OsOpen.TRUNCATE -> WIN_CREATE_ALWAYS
        }
        val handle = winOpenFile(path, write, disposition) ?: return null
        return object : OsFile {
            override val size: Long get() = winFileSize(handle)
            override fun read(offset: Long, bytes: ByteArray, start: Int, count: Int): Int = winReadAt(handle, offset, bytes, start, count)
            override fun write(offset: Long, bytes: ByteArray, start: Int, count: Int): Boolean = winWriteAt(handle, offset, bytes, start, count)
            override fun resize(size: Long): Boolean = winSetFileSize(handle, size)
            override fun close() = winCloseFile(handle)
        }
    }

    actual fun deleteFile(path: String): Boolean = winDeleteFile(path)
    actual fun move(source: String, target: String, replace: Boolean): Boolean = winMove(source, target, replace)
    actual fun restrictToOwner(path: String): Boolean = true

    actual fun randomBytes(count: Int): ByteArray = winRandomBytes(count)
    actual fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray = winAesGcmSeal(key, plain)
    actual fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray = winAesGcmOpen(key, sealed)
    actual fun sha256OfFile(path: String): ByteArray? = winSha256OfFile(path)

    actual fun ecdsaP256Verify(publicSpki: ByteArray, sha256: ByteArray, signatureDer: ByteArray): Boolean =
        winEcdsaP256Verify(publicSpki, sha256, signatureDer)

    actual fun protectSecret(data: ByteArray): ByteArray = winDpapiProtect(data)
    actual fun unprotectSecret(data: ByteArray): ByteArray = winDpapiUnprotect(data)

    actual val httpEngine: HttpClientEngineFactory<HttpClientEngineConfig> = WinHttpNative

    actual fun registerHttpEngine() {
        WinHttpNative.registerAsDefault()
    }

    actual fun deleteRecursively(path: String): Boolean = winDeleteRecursively(path)

    actual fun makeExecutable(path: String): Boolean = true

    actual fun startProcess(
        command: List<String>,
        environment: Map<String, String?>,
        workDir: String?,
        pipeStdin: Boolean,
        mergeStderr: Boolean,
        captureOutput: Boolean,
        detached: Boolean,
    ): OsChildProcess = try {
        WinChildProcess(WinProcess.start(command, environment, workDir, pipeStdin, mergeStderr, captureOutput, detached))
    } catch (e: WinException) {
        throw OsProcessException(e.message ?: "CreateProcess failed")
    }
}

private class WinChildProcess(private val process: WinProcess) : OsChildProcess {
    override val pid: Long get() = process.pid.toLong()
    override val hasStderr: Boolean get() = process.hasStderr
    override val isAlive: Boolean get() = process.isAlive
    override fun writeStdin(bytes: ByteArray): Boolean = process.writeStdin(bytes)
    override fun closeStdin() = process.closeStdin()
    override fun readStdout(buffer: ByteArray): Int = process.readStdout(buffer)
    override fun readStderr(buffer: ByteArray): Int = process.readStderr(buffer)
    override fun waitFor(timeoutMs: Long): Boolean = process.waitFor(timeoutMs)
    override fun exitCode(): Int? = process.exitCode()
    override fun kill() = process.kill()
    override fun close() = process.close()
}

actual object DesktopApp {
    actual var homeOverride: String?
        get() = WinApp.homeOverride
        set(value) {
            WinApp.homeOverride = value?.replace('/', '\\')?.trimEnd('\\')
        }

    actual var automaticDownloads: Boolean
        get() = WinApp.automaticDownloads
        set(value) {
            WinApp.automaticDownloads = value
        }

    actual val appHome: String get() = WinApp.appHome
    actual val kindName: String get() = WinApp.kind.name.lowercase()
    actual val isDevelopmentBuild: Boolean get() = WinApp.isDevelopmentBuild
    actual val exeDir: String get() = WinApp.exeDir
    actual val launcherPath: String? get() = WinApp.launcherPath

    actual fun overrideKind(name: String) {
        when (name) {
            "development" -> WinApp.kindOverride = WinInstallKind.DEVELOPMENT
            "installed" -> WinApp.kindOverride = WinInstallKind.INSTALLED
            "store" -> WinApp.kindOverride = WinInstallKind.STORE
        }
    }

    actual fun looksLikeLoginStart(): Boolean = WinApp.looksLikeLoginStart()
}
