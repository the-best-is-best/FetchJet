@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGABRT
import platform.posix.signal

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
        DesktopOs.terminateProcess(exitCode)
    }

    private fun entry(kind: String, where: String, body: String): String =
        "[${DesktopOs.localTimeStamp()}] $kind in $where (thread ${DesktopOs.threadId()})\n$body\n"

    private fun append(text: String) {
        val path = logPath ?: return
        while (!writing.compareAndSet(0, 1)) {
        }
        try {
            DesktopOs.appendBytes(path, text.encodeToByteArray())
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
