@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.core.log

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.posix.O_APPEND
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.dup2
import platform.posix.open
import platform.posix.pipe
import platform.posix.read
import platform.posix.write

private class PosixLogAppender(private val fd: Int) : LogAppender {
    override fun append(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        var done = 0
        while (done < bytes.size) {
            val written = bytes.usePinned { write(fd, it.addressOf(done), (bytes.size - done).convert()) }
            if (written <= 0) return false
            done += written.toInt()
        }
        return true
    }
}

internal actual fun openLogAppender(path: String): LogAppender? {
    val fd = open(path, O_WRONLY or O_CREAT or O_APPEND or O_CLOEXEC, 0x1A4u)
    return if (fd < 0) null else PosixLogAppender(fd)
}

internal actual fun redirectStdStreamsToPipe(): Int? = memScoped {
    val ends = allocArray<IntVar>(2)
    if (pipe(ends) != 0) return null
    dup2(ends[1], 1)
    dup2(ends[1], 2)
    close(ends[1])
    ends[0]
}

internal actual fun readLogPipe(fd: Int, buffer: ByteArray): Int =
    buffer.usePinned { read(fd, it.addressOf(0), buffer.size.convert()).toInt() }
