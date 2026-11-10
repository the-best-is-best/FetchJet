@file:OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.core.log

import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winFileSize
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.posix.O_BINARY
import platform.posix._close
import platform.posix._dup2
import platform.posix._pipe
import platform.posix._read
import platform.windows.GetLocalTime
import platform.windows.HANDLE
import platform.windows.SYSTEMTIME

internal object NativeLog {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private val installed = AtomicInt(0)

    @Volatile
    private var file: HANDLE? = null

    @Volatile
    private var offset = 0L

    @Volatile
    var path: String = ""
        private set

    fun install(redirect: Boolean) {
        if (!installed.compareAndSet(0, 1)) return
        val wantVerbose = winGetEnv("FETCHJET_VERBOSE_LOGS").equals("true", ignoreCase = true)
        val keepConsole = winGetEnv("FETCHJET_LOG_CONSOLE") == "1"
        try {
            val dir = WinPaths.settings() + "\\logs"
            winCreateDirectories(dir)
            val target = "$dir\\app.log"
            if ((winFileInfo(target)?.size ?: 0L) > MAX_BYTES) winDeleteFile(target)
            val handle = winOpenFile(target, write = true, disposition = WIN_OPEN_ALWAYS)
            if (handle != null) {
                file = handle
                offset = winFileSize(handle).coerceAtLeast(0L)
                path = target
                if (redirect && !keepConsole) startRedirect()
            }
        } catch (_: Throwable) {
        }
        AppLog.verbose = wantVerbose
        println("---- FetchJet started ${localStamp()} (native $APP_VERSION) ----")
        println("Verbose logs: $wantVerbose")
    }

    private fun startRedirect() {
        val readEnd = memScoped {
            val ends = allocArray<IntVar>(2)
            if (_pipe(ends, 65536u, O_BINARY) != 0) return
            _dup2(ends[1], 1)
            _dup2(ends[1], 2)
            _close(ends[1])
            ends[0]
        }
        GlobalScope.launch(newSingleThreadContext("fetchjet-log")) {
            val buffer = ByteArray(8192)
            var pending = ByteArray(1024)
            var length = 0
            while (true) {
                val read = buffer.usePinned { _read(readEnd, it.addressOf(0), buffer.size.toUInt()) }
                if (read <= 0) break
                for (i in 0 until read) {
                    val b = buffer[i]
                    if (b == '\n'.code.toByte()) {
                        writeLine(pending, length)
                        length = 0
                    } else if (b != '\r'.code.toByte()) {
                        if (length == pending.size) pending = pending.copyOf(pending.size * 2)
                        pending[length++] = b
                    }
                }
            }
        }
    }

    private fun writeLine(bytes: ByteArray, length: Int) {
        val handle = file ?: return
        val line = try {
            LogRedact.redact(bytes.decodeToString(0, length))
        } catch (_: Throwable) {
            bytes.decodeToString(0, length)
        }
        val out = (line + "\n").encodeToByteArray()
        if (winWriteAt(handle, offset, out)) offset += out.size
    }

    fun writeDirect(line: String) {
        val handle = file ?: return
        val out = (line + "\n").encodeToByteArray()
        if (winWriteAt(handle, offset, out)) offset += out.size
    }

    private fun localStamp(): String = memScoped {
        val now = alloc<SYSTEMTIME>()
        GetLocalTime(now.ptr)
        fun two(value: UShort) = value.toString().padStart(2, '0')
        "${now.wYear}-${two(now.wMonth)}-${two(now.wDay)}T${two(now.wHour)}:${two(now.wMinute)}:${two(now.wSecond)}"
    }
}
