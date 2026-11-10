@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.set
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.localtime_r
import platform.posix.readlink
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm

fun linuxGetEnv(name: String): String? = getenv(name)?.toKString()

fun linuxCwd(): String = memScoped {
    val buffer = allocArray<ByteVar>(4096)
    getcwd(buffer, 4096u)?.toKString() ?: "/"
}

fun linuxHome(): String = linuxGetEnv("HOME")?.takeIf { it.isNotBlank() } ?: "/tmp"

fun linuxExecutablePath(): String = memScoped {
    val buffer = allocArray<ByteVar>(4096)
    val length = readlink("/proc/self/exe", buffer, 4095u)
    if (length <= 0) "" else {
        buffer[length] = 0
        buffer.toKString()
    }
}

class LinuxLocalTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int, val utcOffsetSeconds: Long)

fun linuxLocalTime(): LinuxLocalTime = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr)
    val parts = alloc<tm>()
    localtime_r(now.ptr, parts.ptr)
    LinuxLocalTime(
        parts.tm_year + 1900, parts.tm_mon + 1, parts.tm_mday,
        parts.tm_hour, parts.tm_min, parts.tm_sec, parts.tm_gmtoff
    )
}

fun linuxSpawnDetached(command: List<String>): Boolean = try {
    linuxStartProcess(command, emptyMap(), null, pipeStdin = false, mergeStderr = false, captureOutput = false, detached = true).close()
    true
} catch (_: Throwable) {
    false
}

internal fun CPointer<ByteVar>.text(): String = toKString()

internal fun Int.asSize(): ULong = convert()
