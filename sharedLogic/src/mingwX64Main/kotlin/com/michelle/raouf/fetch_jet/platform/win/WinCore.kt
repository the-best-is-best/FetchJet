@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKStringFromUtf16
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.GetCurrentProcess
import platform.windows.GetCurrentProcessId
import platform.windows.GetCurrentThreadId
import platform.windows.GetEnvironmentVariableW
import platform.windows.GetLastError
import platform.windows.GetModuleFileNameW
import platform.windows.GetModuleHandleW
import platform.windows.GetProcAddress
import platform.windows.GetTempPathW
import platform.windows.GetTickCount64
import platform.windows.SetEnvironmentVariableW

class WinException(val where: String, val code: UInt) :
    Exception("$where failed: ${winErrorText(code)} ($code)")

fun winErrorText(code: UInt): String = memScoped {
    val buffer = allocArray<UShortVar>(512)
    val written = FormatMessageW(
        FORMAT_MESSAGE_FROM_SYSTEM.toUInt() or FORMAT_MESSAGE_IGNORE_INSERTS.toUInt(),
        null, code, 0u, buffer, 512u, null
    )
    if (written == 0u) "error $code" else buffer.toKStringFromUtf16().trim()
}

fun winFail(where: String): Nothing = throw WinException(where, GetLastError())

fun winGetEnv(name: String): String? = memScoped {
    val needed = GetEnvironmentVariableW(name, null, 0u)
    if (needed == 0u) return null
    val buffer = allocArray<UShortVar>(needed.toInt() + 1)
    val written = GetEnvironmentVariableW(name, buffer, needed + 1u)
    if (written == 0u && needed > 1u) null else buffer.toKStringFromUtf16()
}

fun winSetEnv(name: String, value: String?): Boolean = SetEnvironmentVariableW(name, value) != 0

fun winUserProfile(): String = winGetEnv("USERPROFILE") ?: "C:\\"

fun winTempDir(): String = memScoped {
    val buffer = allocArray<UShortVar>(1024)
    val written = GetTempPathW(1024u, buffer)
    if (written == 0u) winUserProfile() else buffer.toKStringFromUtf16().trimEnd('\\')
}

fun winExecutablePath(): String {
    var size = 1024
    while (size <= 1 shl 16) {
        val path = memScoped {
            val buffer = allocArray<UShortVar>(size)
            val written = GetModuleFileNameW(null, buffer, size.toUInt()).toInt()
            when {
                written == 0 -> ""
                written < size - 1 -> buffer.toKStringFromUtf16()
                else -> null
            }
        }
        if (path != null) return path
        size *= 4
    }
    return ""
}

fun winProcessId(): UInt = GetCurrentProcessId()

class WinMemoryUse(val privateBytes: Long, val workingSetBytes: Long)

fun winMemoryUse(): WinMemoryUse? = memScoped {
    val kernel = GetModuleHandleW("kernel32.dll") ?: return null
    val query = GetProcAddress(kernel, "K32GetProcessMemoryInfo")
        ?.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?, UInt) -> Int>>() ?: return null
    val size = 80
    val counters = allocArray<ByteVar>(size)
    counters.reinterpret<UIntVar>()[0] = size.toUInt()
    if (query(GetCurrentProcess(), counters, size.toUInt()) == 0) return null
    val words = counters.reinterpret<LongVar>()
    WinMemoryUse(privateBytes = words[9], workingSetBytes = words[2])
}

fun winThreadId(): UInt = GetCurrentThreadId()

fun winUptimeMs(): Long = GetTickCount64().toLong()

private const val HEX = "0123456789abcdef"

fun ByteArray.toHexString(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4]).append(HEX[v and 15])
    }
    return out.toString()
}

fun String.hexToBytes(): ByteArray {
    val text = trim()
    require(text.length % 2 == 0) { "odd hex length" }
    return ByteArray(text.length / 2) { i ->
        val hi = text[i * 2].digitToInt(16)
        val lo = text[i * 2 + 1].digitToInt(16)
        ((hi shl 4) or lo).toByte()
    }
}
