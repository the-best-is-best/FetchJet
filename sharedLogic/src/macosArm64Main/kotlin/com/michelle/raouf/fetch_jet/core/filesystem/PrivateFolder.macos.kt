@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.chmod
import platform.posix.getuid
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.stat

internal actual object PrivateFolder {
    private const val TYPE_MASK = 0xF000
    private const val DIRECTORY = 0x4000
    private const val REGULAR = 0x8000

    private fun modeAndOwner(path: String): Pair<Int, UInt>? = memScoped {
        val info = alloc<stat>()
        if (lstat(path, info.ptr) != 0) null else info.st_mode.toInt() to info.st_uid
    }

    actual fun ensure(path: String): Boolean {
        if (path.isEmpty()) return false
        val parent = path.trimEnd('/').substringBeforeLast('/', "")
        if (parent.isNotEmpty() && modeAndOwner(parent) == null && !ensure(parent)) return false
        if (modeAndOwner(path) == null) mkdir(path, 0x1C0u)
        val (mode, owner) = modeAndOwner(path) ?: return false
        if ((mode and TYPE_MASK) != DIRECTORY || owner != getuid()) return false
        if ((mode and 0x3F) != 0 && chmod(path, 0x1C0u) != 0) return false
        return ((modeAndOwner(path)?.first ?: return false) and 0x3F) == 0
    }

    actual fun isOwnFile(path: String): Boolean {
        val (mode, owner) = modeAndOwner(path) ?: return false
        return (mode and TYPE_MASK) == REGULAR && owner == getuid() && (mode and 0x12) == 0
    }
}
