@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.feature.device_sync.MacRecursiveLock
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.torrent.FjtException
import com.michelle.raouf.fetch_jet.platform.torrent.FjtHost
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLibrary
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLock
import com.michelle.raouf.fetch_jet.platform.torrent.FjtModule
import kotlin.time.TimeSource
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import platform.posix.Dl_info
import platform.posix.RTLD_LOCAL
import platform.posix.RTLD_NOW
import platform.posix.dladdr
import platform.posix.dlerror
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.usleep

private class MacFjtLock : FjtLock {
    private val inner = MacRecursiveLock()
    override fun lock() = inner.lock()
    override fun unlock() = inner.unlock()
}

private class MacFjtModule(private val handle: COpaquePointer) : FjtModule {
    override fun symbol(name: String): COpaquePointer? = dlsym(handle, name)
}

private fun coreMarker(): Int = 0

internal object MacTorrentHost : FjtHost {
    override val libraryName: String = "libfjtorrent.dylib"

    private val start = TimeSource.Monotonic.markNow()

    private fun coreDir(): String? = memScoped {
        val info = alloc<Dl_info>()
        if (dladdr(staticCFunction(::coreMarker), info.ptr) == 0) return@memScoped null
        val image = info.dli_fname?.toKString() ?: return@memScoped null
        if (!image.endsWith(".dylib")) return@memScoped null
        image.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
    }

    override fun candidates(): List<String> {
        DesktopOs.getEnv("FETCHJET_TORRENT_DLL")?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        val list = ArrayList<String>()
        coreDir()?.let { list.add("$it/$libraryName") }
        DesktopApp.exeDir.takeIf { it.isNotEmpty() }?.let { exe ->
            list.add("$exe/$libraryName")
            if (exe.endsWith("/Contents/MacOS")) list.add(exe.removeSuffix("/MacOS") + "/Frameworks/$libraryName")
        }
        return list.distinct()
    }

    override fun isFile(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

    override fun fullPath(path: String): String = DesktopOs.fullPath(path)

    override fun open(path: String): FjtModule {
        val handle = dlopen(path, RTLD_NOW or RTLD_LOCAL)
            ?: throw FjtException("$libraryName could not be loaded: ${dlerror()?.toKString() ?: "unknown error"}")
        return MacFjtModule(handle)
    }

    override fun newLock(): FjtLock = MacFjtLock()

    override fun uptimeMs(): Long = start.elapsedNow().inWholeMilliseconds

    override fun sleepMs(ms: Int) {
        usleep((ms * 1000).toUInt())
    }

    override fun env(name: String): String? = DesktopOs.getEnv(name)

    override fun createDirectories(path: String) {
        DesktopOs.createDirectories(path)
    }
}

internal val macTorrentLibrary: FjtLibrary by lazy { FjtLibrary(MacTorrentHost) }
