@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.macos

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSBundle
import platform.Foundation.NSHomeDirectory
import platform.posix.dlsym
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.localtime_r
import platform.posix.realpath
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm

private typealias FnEnviron = CFunction<() -> CPointer<CPointerVar<CPointerVar<ByteVar>>>?>

internal fun <T : CFunction<*>> macSymbol(name: String): CPointer<T>? =
    dlsym((-2L).toCPointer<kotlinx.cinterop.CPointed>(), name)?.reinterpret()

fun macGetEnv(name: String): String? = getenv(name)?.toKString()

fun macCwd(): String = memScoped {
    val buffer = allocArray<ByteVar>(4096)
    getcwd(buffer, 4096u)?.toKString() ?: "/"
}

fun macHome(): String = macGetEnv("HOME")?.takeIf { it.isNotBlank() } ?: NSHomeDirectory()

fun macRealPath(path: String): String = memScoped {
    val buffer = allocArray<ByteVar>(4096)
    realpath(path, buffer)?.toKString() ?: path
}

fun macExecutablePath(): String {
    val raw = NSBundle.mainBundle.executablePath ?: return ""
    return macRealPath(raw)
}

fun macEnvironment(): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val getter = macSymbol<FnEnviron>("_NSGetEnviron") ?: return result
    val env = getter()?.pointed?.value ?: return result
    var i = 0
    while (true) {
        val entry = env[i++]?.toKString() ?: break
        val cut = entry.indexOf('=')
        if (cut > 0) result[entry.substring(0, cut)] = entry.substring(cut + 1)
    }
    return result
}

class MacLocalTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int, val utcOffsetSeconds: Long)

fun macLocalTime(): MacLocalTime = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr)
    val parts = alloc<tm>()
    localtime_r(now.ptr, parts.ptr)
    MacLocalTime(
        parts.tm_year + 1900, parts.tm_mon + 1, parts.tm_mday,
        parts.tm_hour, parts.tm_min, parts.tm_sec, parts.tm_gmtoff
    )
}

fun macSpawnDetached(command: List<String>): Boolean = try {
    macStartProcess(command, emptyMap(), null, pipeStdin = false, mergeStderr = false, captureOutput = false, detached = true).close()
    true
} catch (_: Throwable) {
    false
}
