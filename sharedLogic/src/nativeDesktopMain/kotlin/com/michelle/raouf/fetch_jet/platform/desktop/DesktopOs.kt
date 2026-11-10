package com.michelle.raouf.fetch_jet.platform.desktop

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory

class OsFileInfo(val size: Long, val isDirectory: Boolean, val lastWriteEpochMs: Long)

class OsDirEntry(val name: String, val isDirectory: Boolean, val size: Long, val lastWriteEpochMs: Long)

class OsMemoryUse(val privateBytes: Long, val workingSetBytes: Long)

enum class OsUserFolder { DOWNLOADS, DOCUMENTS, DESKTOP }

interface OsFile {
    val size: Long
    fun read(offset: Long, bytes: ByteArray, start: Int, count: Int): Int
    fun write(offset: Long, bytes: ByteArray, start: Int, count: Int): Boolean
    fun resize(size: Long): Boolean
    fun close()
}

enum class OsOpen { MUST_EXIST, MUST_CREATE, OPEN_OR_CREATE, TRUNCATE }

expect object DesktopOs {
    val name: String
    val separator: Char
    val nullDevice: String
    val curlCommand: String

    fun getEnv(name: String): String?
    fun setEnv(name: String, value: String?): Boolean
    fun threadId(): ULong
    fun memoryUse(): OsMemoryUse?
    fun terminateProcess(exitCode: Int)

    fun localMinutesOfDay(): Int
    fun localUtcOffsetMs(): Long
    fun localTimeStamp(): String

    fun clipboardText(): String?
    fun setClipboardText(text: String): Boolean
    fun clipboardSequence(): Long

    fun openExternal(target: String): Boolean
    fun openFile(path: String, directory: String?): Boolean
    fun revealInFileManager(path: String): Boolean

    fun userFolder(folder: OsUserFolder): String
    fun tempDir(): String
    fun fileInfo(path: String): OsFileInfo?
    fun listEntries(dir: String): List<OsDirEntry>?
    fun createDirectories(dir: String): Boolean
    fun fullPath(path: String): String
    fun readText(path: String): String?
    fun writeText(path: String, text: String): Boolean
    fun appendBytes(path: String, bytes: ByteArray): Boolean
    fun openFile(path: String, write: Boolean): OsFile?
    fun openFile(path: String, write: Boolean, mode: OsOpen): OsFile?
    fun deleteFile(path: String): Boolean
    fun move(source: String, target: String, replace: Boolean): Boolean
    fun restrictToOwner(path: String): Boolean

    fun randomBytes(count: Int): ByteArray
    fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray
    fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray
    fun sha256OfFile(path: String): ByteArray?
    fun ecdsaP256Verify(publicSpki: ByteArray, sha256: ByteArray, signatureDer: ByteArray): Boolean
    fun protectSecret(data: ByteArray): ByteArray
    fun unprotectSecret(data: ByteArray): ByteArray

    val httpEngine: HttpClientEngineFactory<HttpClientEngineConfig>
    fun registerHttpEngine()

    fun deleteRecursively(path: String): Boolean
    fun makeExecutable(path: String): Boolean

    fun startProcess(
        command: List<String>,
        environment: Map<String, String?>,
        workDir: String?,
        pipeStdin: Boolean,
        mergeStderr: Boolean,
        captureOutput: Boolean,
        detached: Boolean,
    ): OsChildProcess
}

fun DesktopOs.exists(path: String): Boolean = fileInfo(path) != null

fun DesktopOs.writeBytes(path: String, bytes: ByteArray): Boolean {
    val file = openFile(path, write = true) ?: return false
    try {
        return file.write(0L, bytes, 0, bytes.size)
    } finally {
        file.close()
    }
}

fun DesktopOs.copyFile(source: String, target: String): Boolean {
    val input = openFile(source, write = false) ?: return false
    try {
        val output = openFile(target, write = true) ?: return false
        try {
            val buffer = ByteArray(256 * 1024)
            var offset = 0L
            while (true) {
                val read = input.read(offset, buffer, 0, buffer.size)
                if (read < 0) return false
                if (read == 0) return true
                if (!output.write(offset, buffer, 0, read)) return false
                offset += read
            }
        } finally {
            output.close()
        }
    } finally {
        input.close()
    }
}

fun DesktopOs.isDirectory(path: String): Boolean = fileInfo(path)?.isDirectory == true

fun DesktopOs.join(parent: String, child: String): String = parent.trimEnd('/', separator) + separator + child

fun DesktopOs.nativePath(path: String): String =
    if (separator == '\\') path.replace('/', '\\').trimEnd('\\') else path.trimEnd('/').ifEmpty { "/" }
