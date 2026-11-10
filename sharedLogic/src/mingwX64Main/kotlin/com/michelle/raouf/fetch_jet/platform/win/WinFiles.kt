@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memset
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.DeleteFileW
import platform.windows.DeviceIoControl
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.FindClose
import platform.windows.FindFirstFileW
import platform.windows.FindNextFileW
import platform.windows.FlushFileBuffers
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GET_FILEEX_INFO_LEVELS
import platform.windows.GetCompressedFileSizeW
import platform.windows.GetDiskFreeSpaceExW
import platform.windows.GetFileAttributesExW
import platform.windows.GetFileSizeEx
import platform.windows.GetFullPathNameW
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.MOVEFILE_COPY_ALLOWED
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MOVEFILE_WRITE_THROUGH
import platform.windows.MoveFileExW
import platform.windows.OVERLAPPED
import platform.windows.ReadFile
import platform.windows.RemoveDirectoryW
import platform.windows.SetEndOfFile
import platform.windows.SetFilePointerEx
import platform.windows.ULARGE_INTEGER
import platform.windows.WIN32_FILE_ATTRIBUTE_DATA
import platform.windows.WIN32_FIND_DATAW
import platform.windows.WriteFile

const val WIN_CREATE_NEW = 1u
const val WIN_CREATE_ALWAYS = 2u
const val WIN_OPEN_EXISTING = 3u
const val WIN_OPEN_ALWAYS = 4u
private const val FSCTL_SET_SPARSE = 0x000900C4u
private const val ERROR_HANDLE_EOF = 38u

class WinFileInfo(val size: Long, val isDirectory: Boolean, val attributes: UInt, val lastWriteEpochMs: Long)

class WinDirEntry(val name: String, val isDirectory: Boolean, val size: Long, val lastWriteEpochMs: Long)

private fun fileTimeToEpochMs(high: UInt, low: UInt): Long {
    val ticks = (high.toLong() shl 32) or low.toLong()
    return ticks / 10_000L - 11_644_473_600_000L
}

fun winFullPath(path: String): String = memScoped {
    val needed = GetFullPathNameW(path, 0u, null, null)
    if (needed == 0u) return path
    val buffer = allocArray<UShortVar>(needed.toInt() + 1)
    val written = GetFullPathNameW(path, needed + 1u, buffer, null)
    if (written == 0u) path else buffer.toKStringFromUtf16()
}

fun winLongPath(path: String): String {
    if (path.startsWith("\\\\?\\") || path.startsWith("\\\\.\\")) return path
    val full = winFullPath(path.replace('/', '\\'))
    if (full.startsWith("\\\\?\\")) return full
    if (full.startsWith("\\\\")) return "\\\\?\\UNC\\" + full.substring(2)
    if (full.length >= 2 && full[1] == ':') return "\\\\?\\" + full
    return full
}

fun winFileInfo(path: String): WinFileInfo? = memScoped {
    val data = alloc<WIN32_FILE_ATTRIBUTE_DATA>()
    if (GetFileAttributesExW(winLongPath(path), GET_FILEEX_INFO_LEVELS.GetFileExInfoStandard, data.ptr) == 0) {
        return null
    }
    WinFileInfo(
        size = (data.nFileSizeHigh.toLong() shl 32) or data.nFileSizeLow.toLong(),
        isDirectory = (data.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u,
        attributes = data.dwFileAttributes,
        lastWriteEpochMs = fileTimeToEpochMs(data.ftLastWriteTime.dwHighDateTime, data.ftLastWriteTime.dwLowDateTime)
    )
}

fun winExists(path: String): Boolean = winFileInfo(path) != null

fun winIsDirectory(path: String): Boolean = winFileInfo(path)?.isDirectory == true

fun winListEntries(dir: String): List<WinDirEntry>? = memScoped {
    val data = alloc<WIN32_FIND_DATAW>()
    val handle = FindFirstFileW(winLongPath(dir) + "\\*", data.ptr)
    if (handle == INVALID_HANDLE_VALUE) return null
    val entries = ArrayList<WinDirEntry>()
    do {
        val name = data.cFileName.toKStringFromUtf16()
        if (name != "." && name != "..") {
            entries.add(
                WinDirEntry(
                    name = name,
                    isDirectory = (data.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u,
                    size = (data.nFileSizeHigh.toLong() shl 32) or data.nFileSizeLow.toLong(),
                    lastWriteEpochMs = fileTimeToEpochMs(
                        data.ftLastWriteTime.dwHighDateTime, data.ftLastWriteTime.dwLowDateTime
                    )
                )
            )
        }
    } while (FindNextFileW(handle, data.ptr) != 0)
    FindClose(handle)
    entries
}

fun winList(dir: String): List<String>? = winListEntries(dir)?.map { it.name }

fun winCreateDirectories(dir: String): Boolean {
    val full = winLongPath(dir)
    if (winFileInfo(full)?.isDirectory == true) return true
    val cut = full.lastIndexOf('\\')
    if (cut > 6) {
        val parent = full.substring(0, cut)
        if (!parent.endsWith(":") && !winCreateDirectories(parent)) return false
    }
    return CreateDirectoryW(full, null) != 0 || GetLastError() == ERROR_ALREADY_EXISTS.toUInt()
}

fun winDeleteFile(path: String): Boolean = DeleteFileW(winLongPath(path)) != 0

fun winRemoveDirectory(path: String): Boolean = RemoveDirectoryW(winLongPath(path)) != 0

fun winDeleteRecursively(path: String): Boolean {
    val info = winFileInfo(path) ?: return true
    if (!info.isDirectory) return winDeleteFile(path)
    var ok = true
    for (name in winList(path) ?: emptyList()) ok = winDeleteRecursively("$path\\$name") && ok
    return winRemoveDirectory(path) && ok
}

fun winMove(source: String, target: String, replace: Boolean = true): Boolean {
    var flags = MOVEFILE_COPY_ALLOWED.toUInt() or MOVEFILE_WRITE_THROUGH.toUInt()
    if (replace) flags = flags or MOVEFILE_REPLACE_EXISTING.toUInt()
    return MoveFileExW(winLongPath(source), winLongPath(target), flags) != 0
}

fun winFreeBytes(dir: String): Long? = memScoped {
    val available = alloc<ULARGE_INTEGER>()
    if (GetDiskFreeSpaceExW(winLongPath(dir), available.ptr, null, null) == 0) return null
    available.QuadPart.toLong()
}

fun winOpenFile(
    path: String,
    write: Boolean,
    disposition: UInt,
    flags: UInt = FILE_ATTRIBUTE_NORMAL.toUInt(),
): HANDLE? {
    val access = if (write) GENERIC_READ or GENERIC_WRITE.toUInt() else GENERIC_READ
    val share = FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt() or FILE_SHARE_DELETE.toUInt()
    val handle = CreateFileW(winLongPath(path), access, share, null, disposition, flags, null)
    return if (handle == INVALID_HANDLE_VALUE) null else handle
}

fun winCloseFile(handle: HANDLE?) {
    if (handle != null) CloseHandle(handle)
}

fun winFlushFile(handle: HANDLE?): Boolean = FlushFileBuffers(handle) != 0

fun winFileSize(handle: HANDLE?): Long = memScoped {
    val size = alloc<LARGE_INTEGER>()
    if (GetFileSizeEx(handle, size.ptr) == 0) -1L else size.QuadPart
}

fun winSetFileSize(handle: HANDLE?, size: Long): Boolean {
    val distance = cValue<LARGE_INTEGER> { QuadPart = size }
    if (SetFilePointerEx(handle, distance, null, 0u) == 0) return false
    return SetEndOfFile(handle) != 0
}

fun winSetSparse(handle: HANDLE?): Boolean = memScoped {
    val returned = alloc<UIntVar>()
    DeviceIoControl(handle, FSCTL_SET_SPARSE, null, 0u, null, 0u, returned.ptr, null) != 0
}

fun winAllocatedBytes(path: String): Long = memScoped {
    val high = alloc<UIntVar>()
    val low = GetCompressedFileSizeW(winLongPath(path), high.ptr)
    if (low == UInt.MAX_VALUE && GetLastError() != 0u) -1L else (high.value.toLong() shl 32) or low.toLong()
}

fun winWriteAt(handle: HANDLE?, offset: Long, bytes: ByteArray, start: Int = 0, count: Int = bytes.size - start): Boolean {
    if (count <= 0) return true
    return memScoped {
        val overlapped = alloc<OVERLAPPED>()
        memset(overlapped.ptr, 0, sizeOf<OVERLAPPED>().convert())
        overlapped.Offset = offset.toUInt()
        overlapped.OffsetHigh = (offset ushr 32).toUInt()
        val written = alloc<UIntVar>()
        val ok = bytes.usePinned {
            WriteFile(handle, it.addressOf(start), count.toUInt(), written.ptr, overlapped.ptr) != 0
        }
        ok && written.value == count.toUInt()
    }
}

fun winReadAt(handle: HANDLE?, offset: Long, bytes: ByteArray, start: Int = 0, count: Int = bytes.size - start): Int {
    if (count <= 0) return 0
    return memScoped {
        val overlapped = alloc<OVERLAPPED>()
        memset(overlapped.ptr, 0, sizeOf<OVERLAPPED>().convert())
        overlapped.Offset = offset.toUInt()
        overlapped.OffsetHigh = (offset ushr 32).toUInt()
        val read = alloc<UIntVar>()
        val ok = bytes.usePinned {
            ReadFile(handle, it.addressOf(start), count.toUInt(), read.ptr, overlapped.ptr) != 0
        }
        when {
            ok -> read.value.toInt()
            GetLastError() == ERROR_HANDLE_EOF -> 0
            else -> -1
        }
    }
}

fun winWriteFile(path: String, bytes: ByteArray): Boolean {
    val handle = winOpenFile(path, write = true, disposition = WIN_CREATE_ALWAYS) ?: return false
    val ok = winWriteAt(handle, 0L, bytes)
    winCloseFile(handle)
    return ok
}

fun winReadFile(path: String): ByteArray? {
    val handle = winOpenFile(path, write = false, disposition = WIN_OPEN_EXISTING) ?: return null
    try {
        val size = winFileSize(handle)
        if (size < 0 || size > Int.MAX_VALUE) return null
        val bytes = ByteArray(size.toInt())
        var done = 0
        while (done < bytes.size) {
            val read = winReadAt(handle, done.toLong(), bytes, done, bytes.size - done)
            if (read <= 0) break
            done += read
        }
        return if (done == bytes.size) bytes else null
    } finally {
        winCloseFile(handle)
    }
}

fun winReadText(path: String): String? = winReadFile(path)?.decodeToString()

fun winWriteText(path: String, text: String): Boolean = winWriteFile(path, text.encodeToByteArray())
