@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.core.log

import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.winFileSize
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.posix.O_BINARY
import platform.posix._close
import platform.posix._dup2
import platform.posix._pipe
import platform.posix._read
import platform.windows.HANDLE

private class WinLogAppender(private val handle: HANDLE) : LogAppender {
    @Volatile
    private var offset = winFileSize(handle).coerceAtLeast(0L)

    override fun append(bytes: ByteArray): Boolean {
        if (!winWriteAt(handle, offset, bytes)) return false
        offset += bytes.size
        return true
    }
}

internal actual fun openLogAppender(path: String): LogAppender? =
    winOpenFile(path, write = true, disposition = WIN_OPEN_ALWAYS)?.let { WinLogAppender(it) }

internal actual fun redirectStdStreamsToPipe(): Int? = memScoped {
    val ends = allocArray<IntVar>(2)
    if (_pipe(ends, 65536u, O_BINARY) != 0) return null
    _dup2(ends[1], 1)
    _dup2(ends[1], 2)
    _close(ends[1])
    ends[0]
}

internal actual fun readLogPipe(fd: Int, buffer: ByteArray): Int =
    buffer.usePinned { _read(fd, it.addressOf(0), buffer.size.toUInt()) }
