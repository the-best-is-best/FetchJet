@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.wcstr
import platform.posix.SIGABRT
import platform.posix._wfopen
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fwrite
import platform.posix.signal
import platform.windows.GetCurrentProcess
import platform.windows.GetCurrentThreadId
import platform.windows.GetLocalTime
import platform.windows.SYSTEMTIME
import platform.windows.TerminateProcess

typealias HostFatalFn = CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>>

object CrashLog {
    const val EXIT_CODE_UNHANDLED = 70
    const val EXIT_CODE_ABORT = 71

    private var logPath: String? = null
    private var hostFatal: HostFatalFn? = null
    private var terminateOnUnhandled = false
    private val writing = AtomicInt(0)
    val reported = AtomicInt(0)
    val unhandled = AtomicInt(0)

    fun install(path: String?, fatal: HostFatalFn?, terminateOnUnhandled: Boolean, quietAbort: Boolean) {
        logPath = path
        hostFatal = fatal
        this.terminateOnUnhandled = terminateOnUnhandled
        setUnhandledExceptionHook { e -> onUnhandled(e) }
        if (quietAbort) {
            signal(SIGABRT, abortHandler)
        }
    }

    fun report(where: String, e: Throwable) {
        reported.incrementAndGet()
        append(entry("ERROR", where, e.stackTraceToString()))
    }

    private fun onUnhandled(e: Throwable) {
        unhandled.incrementAndGet()
        val text = entry("UNHANDLED", "unhandled exception hook", e.stackTraceToString())
        append(text)
        if (terminateOnUnhandled) die(text, EXIT_CODE_UNHANDLED)
    }

    internal fun onAbort() {
        val text = entry("ABORT", "SIGABRT", "abort() was called inside the Kotlin DLL")
        append(text)
        die(text, EXIT_CODE_ABORT)
    }

    fun die(text: String, exitCode: Int) {
        val fatal = hostFatal
        if (fatal != null) memScoped { fatal.invoke(text.cstr.ptr) }
        TerminateProcess(GetCurrentProcess(), exitCode.toUInt())
    }

    private fun entry(kind: String, where: String, body: String): String = memScoped {
        val now = alloc<SYSTEMTIME>()
        GetLocalTime(now.ptr)
        val stamp = "${now.wYear}-${two(now.wMonth)}-${two(now.wDay)} ${two(now.wHour)}:${two(now.wMinute)}:${two(now.wSecond)}"
        "[$stamp] $kind in $where (thread ${GetCurrentThreadId()})\n$body\n"
    }

    private fun two(value: UShort): String = value.toString().padStart(2, '0')

    private fun append(text: String) {
        val path = logPath ?: return
        while (!writing.compareAndSet(0, 1)) {
        }
        try {
            val file = _wfopen(path.wcstr, "ab".wcstr) ?: return
            val bytes = text.encodeToByteArray()
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file) }
            }
            fflush(file)
            fclose(file)
        } finally {
            writing.value = 0
        }
    }
}

private val abortHandler = staticCFunction { _: Int -> CrashLog.onAbort() }

inline fun <T> guarded(where: String, fallback: T, block: () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        CrashLog.report(where, e)
        fallback
    }
