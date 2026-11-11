@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.desktop

import com.michelle.raouf.fetch_jet.platform.macos.MacCrypto
import com.michelle.raouf.fetch_jet.platform.macos.MacDesktopTools
import com.michelle.raouf.fetch_jet.platform.macos.MacKeychain
import com.michelle.raouf.fetch_jet.platform.macos.macCwd
import com.michelle.raouf.fetch_jet.platform.macos.macExecutablePath
import com.michelle.raouf.fetch_jet.platform.macos.macGetEnv
import com.michelle.raouf.fetch_jet.platform.macos.macHome
import com.michelle.raouf.fetch_jet.platform.macos.macLocalTime
import com.michelle.raouf.fetch_jet.platform.macos.macStartProcess
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.engines
import io.ktor.utils.io.InternalAPI
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.pthread_threadid_np
import platform.posix.uint64_tVar
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.blackholeSink
import okio.buffer
import okio.use
import platform.posix._exit
import platform.posix.chmod
import platform.posix.setenv
import platform.posix.unsetenv

actual object DesktopOs {
    actual val name: String = "macOS"
    actual val separator: Char = '/'
    actual val nullDevice: String = "/dev/null"
    actual val curlCommand: String = "curl"

    private val fs = FileSystem.SYSTEM
    private val registered = AtomicInt(0)

    actual fun getEnv(name: String): String? = macGetEnv(name)

    actual fun setEnv(name: String, value: String?): Boolean =
        if (value == null) unsetenv(name) == 0 else setenv(name, value, 1) == 0

    actual fun threadId(): ULong = memScoped {
        val id = alloc<uint64_tVar>()
        pthread_threadid_np(null, id.ptr)
        id.value
    }

    actual fun memoryUse(): OsMemoryUse? = MacDesktopTools.memory()?.let {
        OsMemoryUse(privateBytes = it.footprint, workingSetBytes = it.resident)
    }

    actual fun terminateProcess(exitCode: Int) {
        _exit(exitCode)
    }

    actual fun localMinutesOfDay(): Int = macLocalTime().let { it.hour * 60 + it.minute }

    actual fun localUtcOffsetMs(): Long = macLocalTime().utcOffsetSeconds * 1000L

    actual fun localTimeStamp(): String {
        val now = macLocalTime()
        fun two(value: Int) = value.toString().padStart(2, '0')
        return "${now.year}-${two(now.month)}-${two(now.day)} ${two(now.hour)}:${two(now.minute)}:${two(now.second)}"
    }

    actual fun clipboardText(): String? = MacDesktopTools.clipboardText()

    actual fun setClipboardText(text: String): Boolean = MacDesktopTools.setClipboardText(text)

    actual fun clipboardSequence(): Long = MacDesktopTools.clipboardSequence()

    actual fun openExternal(target: String): Boolean = MacDesktopTools.open(target)

    actual fun openFile(path: String, directory: String?): Boolean = MacDesktopTools.open(path)

    actual fun revealInFileManager(path: String): Boolean = MacDesktopTools.reveal(path)

    actual fun userFolder(folder: OsUserFolder): String {
        val name = when (folder) {
            OsUserFolder.DOWNLOADS -> "Downloads"
            OsUserFolder.DOCUMENTS -> "Documents"
            OsUserFolder.DESKTOP -> "Desktop"
        }
        val path = "${macHome()}/$name"
        if (!isDirectory(path)) createDirectories(path)
        return path
    }

    actual fun tempDir(): String = macGetEnv("TMPDIR")?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "/tmp"

    actual fun fileInfo(path: String): OsFileInfo? = try {
        fs.metadataOrNull(path.toPath())?.let {
            OsFileInfo(it.size ?: 0L, it.isDirectory, it.lastModifiedAtMillis ?: 0L)
        }
    } catch (_: Throwable) {
        null
    }

    actual fun listEntries(dir: String): List<OsDirEntry>? = try {
        fs.listOrNull(dir.toPath())?.map { child ->
            val meta = fs.metadataOrNull(child)
            OsDirEntry(child.name, meta?.isDirectory == true, meta?.size ?: 0L, meta?.lastModifiedAtMillis ?: 0L)
        }
    } catch (_: Throwable) {
        null
    }

    actual fun createDirectories(dir: String): Boolean = try {
        fs.createDirectories(dir.toPath())
        true
    } catch (_: Throwable) {
        false
    }

    actual fun fullPath(path: String): String {
        val absolute = if (path.startsWith("/")) path else macCwd() + "/" + path
        return absolute.toPath(normalize = true).toString()
    }

    actual fun readText(path: String): String? = try {
        fs.read(path.toPath()) { readUtf8() }
    } catch (_: Throwable) {
        null
    }

    actual fun writeText(path: String, text: String): Boolean = try {
        fs.write(path.toPath()) { writeUtf8(text) }
        true
    } catch (_: Throwable) {
        false
    }

    actual fun appendBytes(path: String, bytes: ByteArray): Boolean = try {
        fs.appendingSink(path.toPath()).buffer().use { it.write(bytes) }
        true
    } catch (_: Throwable) {
        false
    }

    actual fun openFile(path: String, write: Boolean): OsFile? =
        openFile(path, write, if (write) OsOpen.TRUNCATE else OsOpen.MUST_EXIST)

    actual fun openFile(path: String, write: Boolean, mode: OsOpen): OsFile? = try {
        val file = path.toPath()
        val handle = if (!write) {
            if (mode == OsOpen.MUST_EXIST) fs.openReadOnly(file)
            else {
                fs.openReadWrite(file, mustCreate = mode == OsOpen.MUST_CREATE, mustExist = false).close()
                fs.openReadOnly(file)
            }
        } else {
            fs.openReadWrite(file, mustCreate = mode == OsOpen.MUST_CREATE, mustExist = mode == OsOpen.MUST_EXIST)
                .also { if (mode == OsOpen.TRUNCATE) it.resize(0) }
        }
        object : OsFile {
            override val size: Long get() = handle.size()
            override fun read(offset: Long, bytes: ByteArray, start: Int, count: Int): Int = try {
                handle.read(offset, bytes, start, count).let { if (it < 0) 0 else it }
            } catch (_: Throwable) {
                -1
            }
            override fun write(offset: Long, bytes: ByteArray, start: Int, count: Int): Boolean = try {
                handle.write(offset, bytes, start, count)
                true
            } catch (_: Throwable) {
                false
            }
            override fun resize(size: Long): Boolean = try {
                handle.resize(size)
                true
            } catch (_: Throwable) {
                false
            }
            override fun close() = handle.close()
        }
    } catch (_: Throwable) {
        null
    }

    actual fun deleteFile(path: String): Boolean = try {
        fs.delete(path.toPath(), mustExist = false)
        true
    } catch (_: Throwable) {
        false
    }

    actual fun move(source: String, target: String, replace: Boolean): Boolean = try {
        val to = target.toPath()
        if (!replace && fs.exists(to)) {
            false
        } else {
            moveOrCopy(source.toPath(), to)
            true
        }
    } catch (_: Throwable) {
        false
    }

    private fun moveOrCopy(from: Path, to: Path) {
        try {
            fs.atomicMove(from, to)
        } catch (_: Throwable) {
            fs.copy(from, to)
            fs.delete(from)
        }
    }

    actual fun restrictToOwner(path: String): Boolean = chmod(path, 0x180u) == 0

    actual fun randomBytes(count: Int): ByteArray = MacCrypto.randomBytes(count)

    actual fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray =
        MacCrypto.aesGcmSeal(key, plain)

    actual fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray = MacCrypto.aesGcmOpen(key, sealed)

    actual fun sha256OfFile(path: String): ByteArray? = try {
        val sink = HashingSink.sha256(blackholeSink())
        sink.buffer().use { out -> fs.read(path.toPath()) { out.writeAll(this) } }
        sink.hash.toByteArray()
    } catch (_: Throwable) {
        null
    }

    actual fun ecdsaP256Verify(publicSpki: ByteArray, sha256: ByteArray, signatureDer: ByteArray): Boolean =
        try {
            MacCrypto.ecdsaVerify(publicSpki, sha256, signatureDer)
        } catch (_: Throwable) {
            false
        }

    actual fun protectSecret(data: ByteArray): ByteArray = MacKeychain.protect(data)

    actual fun unprotectSecret(data: ByteArray): ByteArray = MacKeychain.unprotect(data)

    actual val httpEngine: HttpClientEngineFactory<HttpClientEngineConfig> = Darwin

    @OptIn(InternalAPI::class)
    actual fun registerHttpEngine() {
        if (registered.compareAndSet(0, 1) && engines.none { it === Darwin }) engines.append(Darwin)
    }

    actual fun deleteRecursively(path: String): Boolean = try {
        fs.deleteRecursively(path.toPath(), mustExist = false)
        true
    } catch (_: Throwable) {
        false
    }

    actual fun makeExecutable(path: String): Boolean = chmod(path, 0x1EDu) == 0

    actual fun startProcess(
        command: List<String>,
        environment: Map<String, String?>,
        workDir: String?,
        pipeStdin: Boolean,
        mergeStderr: Boolean,
        captureOutput: Boolean,
        detached: Boolean,
    ): OsChildProcess = macStartProcess(command, environment, workDir, pipeStdin, mergeStderr, captureOutput, detached)
}

actual object DesktopApp {
    private const val HOME_FOLDER = ".fetch_jet"
    private const val INSTALLED_MARKER = "FetchJet.installed"

    @Volatile
    private var homeValue: String? = null

    @Volatile
    private var kindOverride: String? = null

    actual var homeOverride: String?
        get() = homeValue
        set(value) {
            homeValue = value?.trimEnd('/')?.ifEmpty { "/" }
        }

    @Volatile
    actual var automaticDownloads: Boolean = macGetEnv("FETCHJET_NO_AUTO_DOWNLOADS") != "1"

    private val detectedKind: String by lazy {
        val exe = macExecutablePath()
        val dir = exe.substringBeforeLast('/', "")
        when {
            dir.isNotEmpty() && DesktopOs.exists("$dir/$INSTALLED_MARKER") -> "installed"
            dir.endsWith(".app/Contents/MacOS") && DesktopOs.exists("${dir.substringBeforeLast('/')}/Resources/$INSTALLED_MARKER") -> "installed"
            macGetEnv("FETCHJET_INSTALLED") == "1" -> "installed"
            else -> "development"
        }
    }

    actual val kindName: String get() = kindOverride ?: detectedKind

    actual val isDevelopmentBuild: Boolean get() = kindName == "development"

    actual val exeDir: String by lazy { macExecutablePath().substringBeforeLast('/', "") }

    actual val launcherPath: String? get() = if (isDevelopmentBuild) null else macExecutablePath().takeIf { it.isNotEmpty() }

    actual val appHome: String
        get() {
            homeValue?.let { return it }
            macGetEnv("FETCHJET_HOME")?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
            val home = macHome()
            if (!isDevelopmentBuild) return "$home/$HOME_FOLDER"
            return "$home/Library/Application Support/FetchJetDev/$HOME_FOLDER"
        }

    actual fun overrideKind(name: String) {
        if (name == "development" || name == "installed") kindOverride = name
    }

    actual fun looksLikeLoginStart(): Boolean = false
}
