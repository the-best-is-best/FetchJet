@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import com.michelle.raouf.fetch_jet.platform.torrent.FjtException
import com.michelle.raouf.fetch_jet.platform.torrent.FjtHost
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLibrary
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLock
import com.michelle.raouf.fetch_jet.platform.torrent.FjtModule
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.windows.AcquireSRWLockExclusive
import platform.windows.GetLastError
import platform.windows.GetModuleFileNameW
import platform.windows.GetModuleHandleW
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.InitializeSRWLock
import platform.windows.LoadLibraryExW
import platform.windows.ReleaseSRWLockExclusive
import platform.windows.SRWLOCK
import platform.windows.SetErrorMode
import platform.windows.Sleep

private const val LOAD_WITH_ALTERED_SEARCH_PATH = 0x8u
private const val SEM_FAILCRITICALERRORS = 0x1u
private const val MODULE_FROM_ADDRESS = 0x4u
private const val MODULE_UNCHANGED_REFCOUNT = 0x2u

private fun torrentModuleAnchor(): Int = 0

private class WinFjtLock : FjtLock {
    private val lock: SRWLOCK = nativeHeap.alloc<SRWLOCK>().also { InitializeSRWLock(it.ptr) }
    override fun lock() = AcquireSRWLockExclusive(lock.ptr)
    override fun unlock() = ReleaseSRWLockExclusive(lock.ptr)
}

private class WinFjtModule(private val module: HMODULE) : FjtModule {
    override fun symbol(name: String): COpaquePointer? = GetProcAddress(module, name)?.reinterpret()
}

object WinTorrentHost : FjtHost {
    override val libraryName: String = "fjtorrent.dll"

    private fun moduleDir(): String? = memScoped {
        val kernel = GetModuleHandleW("kernel32.dll") ?: return null
        val handleEx = GetProcAddress(kernel, "GetModuleHandleExW")
            ?.reinterpret<CFunction<(UInt, COpaquePointer?, COpaquePointer?) -> Int>>() ?: return null
        val handle = alloc<COpaquePointerVar>()
        val anchor: COpaquePointer = staticCFunction(::torrentModuleAnchor).reinterpret<ByteVar>()
        if (handleEx(MODULE_FROM_ADDRESS or MODULE_UNCHANGED_REFCOUNT, anchor, handle.ptr) == 0) return null
        val module = handle.value ?: return null
        val buffer = allocArray<UShortVar>(4096)
        val written = GetModuleFileNameW(module.reinterpret(), buffer, 4096u).toInt()
        if (written <= 0 || written >= 4095) return null
        buffer.toKStringFromUtf16().substringBeforeLast('\\', "").ifEmpty { null }
    }

    override fun candidates(): List<String> {
        winGetEnv("FETCHJET_TORRENT_DLL")?.takeIf { it.isNotBlank() }?.let { return listOf(it.replace('/', '\\')) }
        val list = ArrayList<String>()
        moduleDir()?.let { list.add("$it\\$libraryName") }
        WinApp.exeDir.takeIf { it.isNotEmpty() }?.let { list.add("$it\\$libraryName") }
        return list.distinct()
    }

    override fun isFile(path: String): Boolean = winFileInfo(path)?.isDirectory == false

    override fun fullPath(path: String): String = winFullPath(path)

    override fun open(path: String): FjtModule {
        val previous = SetErrorMode(SEM_FAILCRITICALERRORS)
        val module = LoadLibraryExW(path, null, LOAD_WITH_ALTERED_SEARCH_PATH)
        val code = GetLastError()
        SetErrorMode(previous)
        if (module == null) {
            val hint = when (code) {
                126u -> "the file or one of its dependencies is missing"
                193u -> "not a 64-bit Windows library"
                5u, 225u -> "blocked (antivirus or policy)"
                else -> winErrorText(code)
            }
            throw FjtException("$libraryName could not be loaded: $hint ($code)")
        }
        return WinFjtModule(module)
    }

    override fun newLock(): FjtLock = WinFjtLock()

    override fun uptimeMs(): Long = winUptimeMs()

    override fun sleepMs(ms: Int) = Sleep(ms.toUInt())

    override fun env(name: String): String? = winGetEnv(name)

    override fun createDirectories(path: String) {
        winCreateDirectories(path)
    }
}

val winTorrentLibrary: FjtLibrary by lazy { FjtLibrary(WinTorrentHost) }
