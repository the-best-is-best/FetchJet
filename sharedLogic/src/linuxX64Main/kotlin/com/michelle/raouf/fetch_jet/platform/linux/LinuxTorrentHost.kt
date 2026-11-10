@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

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
import kotlinx.cinterop.toKString
import platform.posix.RTLD_LOCAL
import platform.posix.RTLD_NOW
import platform.posix.dlerror
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.usleep

private class LinuxFjtLock : FjtLock {
    private val inner = LinuxLock()
    override fun lock() = inner.lock()
    override fun unlock() = inner.unlock()
}

private class LinuxFjtModule(private val handle: COpaquePointer) : FjtModule {
    override fun symbol(name: String): COpaquePointer? = dlsym(handle, name)
}

object LinuxTorrentHost : FjtHost {
    override val libraryName: String = "libfjtorrent.so"

    private val start = TimeSource.Monotonic.markNow()

    private fun coreDir(): String? = DesktopOs.readText("/proc/self/maps")
        ?.lineSequence()
        ?.map { it.substringAfter('/', "") }
        ?.firstOrNull { it.endsWith("/libfjcore.so") }
        ?.let { "/" + it.substringBeforeLast('/') }

    override fun candidates(): List<String> {
        linuxGetEnv("FETCHJET_TORRENT_DLL")?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        val list = ArrayList<String>()
        coreDir()?.let { list.add("$it/$libraryName") }
        DesktopApp.exeDir.takeIf { it.isNotEmpty() }?.let { list.add("$it/$libraryName") }
        return list.distinct()
    }

    override fun isFile(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

    override fun fullPath(path: String): String = DesktopOs.fullPath(path)

    override fun open(path: String): FjtModule {
        val handle = dlopen(path, RTLD_NOW or RTLD_LOCAL)
            ?: throw FjtException("$libraryName could not be loaded: ${dlerror()?.toKString() ?: "unknown error"}")
        return LinuxFjtModule(handle)
    }

    override fun newLock(): FjtLock = LinuxFjtLock()

    override fun uptimeMs(): Long = start.elapsedNow().inWholeMilliseconds

    override fun sleepMs(ms: Int) {
        usleep((ms * 1000).toUInt())
    }

    override fun env(name: String): String? = linuxGetEnv(name)

    override fun createDirectories(path: String) {
        DesktopOs.createDirectories(path)
    }
}

val linuxTorrentLibrary: FjtLibrary by lazy { FjtLibrary(LinuxTorrentHost) }
