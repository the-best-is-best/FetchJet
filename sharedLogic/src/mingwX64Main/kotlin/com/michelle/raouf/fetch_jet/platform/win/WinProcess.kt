@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.memset
import platform.windows.AssignProcessToJobObject
import platform.windows.CREATE_BREAKAWAY_FROM_JOB
import platform.windows.CREATE_NEW_PROCESS_GROUP
import platform.windows.CREATE_NO_WINDOW
import platform.windows.CREATE_SUSPENDED
import platform.windows.CREATE_UNICODE_ENVIRONMENT
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.CreateJobObjectW
import platform.windows.CreatePipe
import platform.windows.CreateProcessW
import platform.windows.DeleteProcThreadAttributeList
import platform.windows.EXTENDED_STARTUPINFO_PRESENT
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.FreeEnvironmentStringsW
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetEnvironmentStringsW
import platform.windows.GetExitCodeProcess
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.InitializeProcThreadAttributeList
import platform.windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION
import platform.windows.PROCESS_INFORMATION
import platform.windows.ReadFile
import platform.windows.ResumeThread
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.STARTUPINFOEXW
import platform.windows.SetHandleInformation
import platform.windows.SetInformationJobObject
import platform.windows.TerminateJobObject
import platform.windows.TerminateProcess
import platform.windows.UpdateProcThreadAttribute
import platform.windows.WaitForSingleObject
import platform.windows.WriteFile

private const val STARTF_USESTDHANDLES = 0x100u
private const val STARTF_USESHOWWINDOW = 0x1u
private const val HANDLE_FLAG_INHERIT = 1u
private const val JOB_KILL_ON_CLOSE = 0x2000u
private const val JOB_EXTENDED_LIMIT_INFORMATION = 9u
private const val ATTRIBUTE_HANDLE_LIST = 0x00020002uL
private const val WAIT_TIMEOUT_CODE = 258u
private const val STILL_ACTIVE_CODE = 259u
private const val INFINITE_WAIT = 0xFFFFFFFFu
private const val OPEN_EXISTING_FILE = 3u

fun winQuoteArgument(argument: String): String {
    if (argument.isNotEmpty() && argument.none { it == ' ' || it == '\t' || it == '"' || it == '\n' }) return argument
    val out = StringBuilder("\"")
    var slashes = 0
    for (c in argument) {
        when (c) {
            '\\' -> slashes++
            '"' -> {
                repeat(slashes * 2 + 1) { out.append('\\') }
                out.append('"')
                slashes = 0
            }

            else -> {
                repeat(slashes) { out.append('\\') }
                out.append(c)
                slashes = 0
            }
        }
    }
    repeat(slashes * 2) { out.append('\\') }
    out.append('"')
    return out.toString()
}

fun winCommandLine(command: List<String>): String = command.joinToString(" ") { winQuoteArgument(it) }

fun winEnvironment(): Map<String, String> {
    val block = GetEnvironmentStringsW() ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    var at = 0
    while (block[at] != 0.toUShort()) {
        val entry = (block + at)!!.toKStringFromUtf16()
        at += entry.length + 1
        val cut = entry.indexOf('=', 1)
        if (cut > 0) result[entry.substring(0, cut)] = entry.substring(cut + 1)
    }
    FreeEnvironmentStringsW(block)
    return result
}

private fun environmentBlock(overrides: Map<String, String?>): String {
    val merged = LinkedHashMap<String, Pair<String, String>>()
    for ((name, value) in winEnvironment()) merged[name.uppercase()] = name to value
    for ((name, value) in overrides) {
        if (value == null) merged.remove(name.uppercase()) else merged[name.uppercase()] = name to value
    }
    val out = StringBuilder()
    for (key in merged.keys.sorted()) {
        val (name, value) = merged.getValue(key)
        out.append(name).append('=').append(value).append('\u0000')
    }
    out.append('\u0000')
    return out.toString()
}

class WinProcess private constructor(
    private val process: HANDLE?,
    private val job: HANDLE?,
    private var stdinWrite: HANDLE?,
    private var stdoutRead: HANDLE?,
    private var stderrRead: HANDLE?,
    val pid: UInt,
) {
    private val closed = AtomicInt(0)
    private val gate = AtomicInt(0)

    private inline fun <T> gated(block: () -> T): T {
        while (!gate.compareAndSet(0, 1)) {
        }
        try {
            return block()
        } finally {
            gate.value = 0
        }
    }

    fun writeStdin(bytes: ByteArray): Boolean {
        val handle = stdinWrite ?: return false
        if (bytes.isEmpty()) return true
        return memScoped {
            val written = alloc<UIntVar>()
            var done = 0
            while (done < bytes.size) {
                val ok = bytes.usePinned {
                    WriteFile(handle, it.addressOf(done), (bytes.size - done).toUInt(), written.ptr, null) != 0
                }
                if (!ok || written.value == 0u) return@memScoped false
                done += written.value.toInt()
            }
            true
        }
    }

    fun closeStdin() {
        stdinWrite?.let { CloseHandle(it) }
        stdinWrite = null
    }

    fun readStdout(buffer: ByteArray): Int = read(stdoutRead, buffer)

    fun readStderr(buffer: ByteArray): Int = read(stderrRead, buffer)

    val hasStderr: Boolean get() = stderrRead != null

    private fun read(handle: HANDLE?, buffer: ByteArray): Int {
        if (handle == null || buffer.isEmpty()) return -1
        return memScoped {
            val count = alloc<UIntVar>()
            val ok = buffer.usePinned { ReadFile(handle, it.addressOf(0), buffer.size.toUInt(), count.ptr, null) != 0 }
            if (!ok || count.value == 0u) -1 else count.value.toInt()
        }
    }

    fun waitFor(timeoutMs: Long): Boolean {
        val wait = if (timeoutMs < 0 || timeoutMs >= INFINITE_WAIT.toLong()) INFINITE_WAIT else timeoutMs.toUInt()
        return WaitForSingleObject(process, wait) != WAIT_TIMEOUT_CODE
    }

    fun exitCode(): Int? = memScoped {
        val code = alloc<UIntVar>()
        if (GetExitCodeProcess(process, code.ptr) == 0) return null
        if (code.value == STILL_ACTIVE_CODE && WaitForSingleObject(process, 0u) == WAIT_TIMEOUT_CODE) null
        else code.value.toInt()
    }

    val isAlive: Boolean get() = WaitForSingleObject(process, 0u) == WAIT_TIMEOUT_CODE

    fun kill() {
        gated {
            if (closed.value != 0) return
            if (job != null) TerminateJobObject(job, 1u) else TerminateProcess(process, 1u)
        }
    }

    fun close() {
        gated {
            if (!closed.compareAndSet(0, 1)) return
            closeStdin()
            stdoutRead?.let { CloseHandle(it) }
            stderrRead?.let { CloseHandle(it) }
            stdoutRead = null
            stderrRead = null
            if (job != null) CloseHandle(job)
            if (process != null) CloseHandle(process)
        }
    }

    companion object {
        fun start(
            command: List<String>,
            environment: Map<String, String?> = emptyMap(),
            workDir: String? = null,
            pipeStdin: Boolean = false,
            mergeStderr: Boolean = false,
            captureOutput: Boolean = true,
            detached: Boolean = false,
            rawCommandLine: String? = null,
        ): WinProcess = memScoped {
            require(command.isNotEmpty() || rawCommandLine != null) { "empty command" }
            val security = alloc<SECURITY_ATTRIBUTES>()
            security.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
            security.lpSecurityDescriptor = null
            security.bInheritHandle = 1
            val toClose = ArrayList<HANDLE?>()
            val parentEnds = ArrayList<HANDLE?>()

            fun pipe(parentReads: Boolean): Pair<HANDLE?, HANDLE?> {
                val read = alloc<COpaquePointerVar>()
                val write = alloc<COpaquePointerVar>()
                if (CreatePipe(read.ptr, write.ptr, security.ptr, 64u * 1024u) == 0) winFail("CreatePipe")
                val parent = if (parentReads) read.value else write.value
                val child = if (parentReads) write.value else read.value
                SetHandleInformation(parent, HANDLE_FLAG_INHERIT, 0u)
                toClose.add(child)
                parentEnds.add(parent)
                return parent to child
            }

            fun nul(write: Boolean): HANDLE? {
                val handle = CreateFileW(
                    "NUL", if (write) GENERIC_WRITE.toUInt() else GENERIC_READ,
                    FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(), security.ptr, OPEN_EXISTING_FILE, 0u, null
                )
                if (handle == INVALID_HANDLE_VALUE) winFail("open NUL")
                toClose.add(handle)
                return handle
            }

            var job: HANDLE? = null
            var started = false
            try {
                val stdin = if (pipeStdin) pipe(parentReads = false) else null to nul(write = false)
                val stdout = if (captureOutput) pipe(parentReads = true) else null to nul(write = true)
                val stderr = when {
                    !captureOutput -> null to stdout.second
                    mergeStderr -> null to stdout.second
                    else -> pipe(parentReads = true)
                }
                val inherited = listOf(stdin.second, stdout.second, stderr.second).distinct()

                val size = alloc<ULongVar>()
                InitializeProcThreadAttributeList(null, 1u, 0u, size.ptr)
                val attributes = allocArray<ByteVar>(size.value.toInt())
                if (InitializeProcThreadAttributeList(attributes.reinterpret(), 1u, 0u, size.ptr) == 0) {
                    winFail("InitializeProcThreadAttributeList")
                }
                val handles = allocArray<COpaquePointerVar>(inherited.size)
                inherited.forEachIndexed { index, handle -> handles[index] = handle }
                if (UpdateProcThreadAttribute(
                        attributes.reinterpret(), 0u, ATTRIBUTE_HANDLE_LIST, handles,
                        (inherited.size * sizeOf<COpaquePointerVar>()).toULong(), null, null
                    ) == 0
                ) {
                    winFail("UpdateProcThreadAttribute")
                }

                val startup = alloc<STARTUPINFOEXW>()
                memset(startup.ptr, 0, sizeOf<STARTUPINFOEXW>().convert())
                startup.StartupInfo.cb = sizeOf<STARTUPINFOEXW>().toUInt()
                startup.StartupInfo.dwFlags =
                    if (detached) STARTF_USESTDHANDLES else STARTF_USESTDHANDLES or STARTF_USESHOWWINDOW
                startup.StartupInfo.wShowWindow = 0u
                startup.StartupInfo.hStdInput = stdin.second
                startup.StartupInfo.hStdOutput = stdout.second
                startup.StartupInfo.hStdError = stderr.second
                startup.lpAttributeList = attributes.reinterpret()

                val info = alloc<PROCESS_INFORMATION>()
                val line = rawCommandLine ?: winCommandLine(command)
                val block = environmentBlock(environment)
                val blockMemory = allocArray<UShortVar>(block.length + 1)
                for (i in block.indices) blockMemory[i] = block[i].code.toUShort()
                blockMemory[block.length] = 0u
                var flags = CREATE_UNICODE_ENVIRONMENT.toUInt() or EXTENDED_STARTUPINFO_PRESENT.toUInt() or
                    CREATE_NO_WINDOW.toUInt() or CREATE_SUSPENDED.toUInt()
                if (detached) flags = flags or CREATE_NEW_PROCESS_GROUP.toUInt()

                fun create(extraFlags: UInt): Boolean = CreateProcessW(
                    null, line.wcstr.ptr, null, null, 1, flags or extraFlags, blockMemory,
                    workDir?.let { winFullPath(it) }, startup.StartupInfo.ptr, info.ptr
                ) != 0

                val created = if (detached) create(CREATE_BREAKAWAY_FROM_JOB.toUInt()) || create(0u) else create(0u)
                val error = GetLastError()
                DeleteProcThreadAttributeList(attributes.reinterpret())
                if (!created) throw WinException("CreateProcess(${command.firstOrNull() ?: rawCommandLine})", error)

                if (!detached) {
                    job = CreateJobObjectW(null, null)
                    if (job != null) {
                        val limits = alloc<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>()
                        memset(limits.ptr, 0, sizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>().convert())
                        limits.BasicLimitInformation.LimitFlags = JOB_KILL_ON_CLOSE
                        SetInformationJobObject(
                            job, JOB_EXTENDED_LIMIT_INFORMATION, limits.ptr,
                            sizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>().toUInt()
                        )
                        if (AssignProcessToJobObject(job, info.hProcess) == 0) {
                            CloseHandle(job)
                            job = null
                        }
                    }
                }
                ResumeThread(info.hThread)
                CloseHandle(info.hThread)
                started = true
                WinProcess(info.hProcess, job, stdin.first, stdout.first, stderr.first, info.dwProcessId)
            } finally {
                toClose.distinct().forEach { CloseHandle(it) }
                if (!started) {
                    parentEnds.forEach { CloseHandle(it) }
                    job?.let { CloseHandle(it) }
                }
            }
        }
    }
}
