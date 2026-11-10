package com.michelle.raouf.fetch_jet.platform.desktop

import kotlin.concurrent.AtomicInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class OsProcessException(message: String) : Exception(message)

class OsProcessResult(
    val exitCode: Int,
    val timedOut: Boolean,
    val stdout: String,
    val stderr: String,
)

interface OsChildProcess {
    val pid: Long
    val hasStderr: Boolean
    val isAlive: Boolean
    fun writeStdin(bytes: ByteArray): Boolean
    fun closeStdin()
    fun readStdout(buffer: ByteArray): Int
    fun readStderr(buffer: ByteArray): Int
    fun waitFor(timeoutMs: Long): Boolean
    fun exitCode(): Int?
    fun kill()
    fun close()
}

class OsLineSplitter(private val onLine: (String) -> Unit) {
    private var pending = ByteArray(256)
    private var length = 0
    private var skipLineFeed = false

    fun feed(bytes: ByteArray, count: Int) {
        for (i in 0 until count) {
            val b = bytes[i]
            if (skipLineFeed) {
                skipLineFeed = false
                if (b == '\n'.code.toByte()) continue
            }
            if (b == '\n'.code.toByte() || b == '\r'.code.toByte()) {
                skipLineFeed = b == '\r'.code.toByte()
                flush()
            } else {
                if (length == pending.size) pending = pending.copyOf(pending.size * 2)
                pending[length++] = b
            }
        }
    }

    fun finish() {
        if (length > 0) flush()
    }

    private fun flush() {
        onLine(pending.decodeToString(0, length))
        length = 0
    }
}

suspend fun DesktopOs.runProcess(
    command: List<String>,
    environment: Map<String, String?> = emptyMap(),
    workDir: String? = null,
    stdinBytes: ByteArray? = null,
    mergeStderr: Boolean = false,
    timeoutMs: Long = -1,
    keepOutput: Boolean = true,
    onStarted: ((OsChildProcess) -> Unit)? = null,
    onStdoutLine: ((String) -> Unit)? = null,
    onStderrLine: ((String) -> Unit)? = null,
): OsProcessResult = withContext(Dispatchers.IO) {
    val process = startProcess(
        command, environment, workDir, pipeStdin = stdinBytes != null, mergeStderr = mergeStderr,
        captureOutput = true, detached = false
    )
    val finished = AtomicInt(0)
    val timedOut = AtomicInt(0)
    try {
        coroutineScope {
            val guard = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (finished.value == 0) process.kill()
                }
            }
            val timer = if (timeoutMs >= 0) launch {
                delay(timeoutMs)
                if (finished.value == 0) {
                    timedOut.value = 1
                    process.kill()
                }
            } else null
            onStarted?.invoke(process)
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val errors = if (process.hasStderr) async {
                pumpLines(process, false, stderr, keepOutput, onStderrLine)
            } else null
            val writer = if (stdinBytes != null) launch {
                process.writeStdin(stdinBytes)
                process.closeStdin()
            } else null
            pumpLines(process, true, stdout, keepOutput, onStdoutLine)
            errors?.await()
            writer?.join()
            process.waitFor(-1)
            val code = process.exitCode() ?: -1
            finished.value = 1
            timer?.cancel()
            guard.cancel()
            OsProcessResult(code, timedOut.value != 0, stdout.toString(), stderr.toString())
        }
    } finally {
        process.kill()
        process.close()
    }
}

private fun pumpLines(
    process: OsChildProcess,
    fromStdout: Boolean,
    keep: StringBuilder,
    keepOutput: Boolean,
    onLine: ((String) -> Unit)?,
) {
    val buffer = ByteArray(16 * 1024)
    val splitter = OsLineSplitter { line ->
        if (keepOutput) keep.append(line).append('\n')
        onLine?.invoke(line)
    }
    while (true) {
        val read = if (fromStdout) process.readStdout(buffer) else process.readStderr(buffer)
        if (read < 0) break
        splitter.feed(buffer, read)
    }
    splitter.finish()
}

fun DesktopOs.startDetached(command: List<String>, workDir: String? = null): Boolean = try {
    startProcess(command, emptyMap(), workDir, pipeStdin = false, mergeStderr = false, captureOutput = false, detached = true).close()
    true
} catch (_: OsProcessException) {
    false
}

fun DesktopOs.runForOutput(command: List<String>, timeoutMs: Long): String? = try {
    runBlocking { runProcess(command, timeoutMs = timeoutMs) }.stdout
} catch (_: OsProcessException) {
    null
}

fun DesktopOs.findOnPath(command: String): String? =
    getEnv("PATH").orEmpty().split(if (separator == '\\') ';' else ':').filter { it.isNotBlank() }
        .map { join(it, command) }
        .firstOrNull { fileInfo(it)?.isDirectory == false }
