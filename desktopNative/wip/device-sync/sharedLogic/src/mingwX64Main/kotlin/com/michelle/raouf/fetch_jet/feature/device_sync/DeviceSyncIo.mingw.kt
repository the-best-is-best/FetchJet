@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_EXISTING
import com.michelle.raouf.fetch_jet.platform.win.winCloseFile
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winReadAt
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.HANDLE

internal fun syncFileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

internal fun syncFileSize(path: String): Long? = winFileInfo(path)?.takeIf { !it.isDirectory }?.size

internal class FileChunkReader(path: String, chunk: Int) {
    private var handle: HANDLE? = winOpenFile(path, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw IllegalStateException("cannot read ${syncFileName(path)}")
    private val buffer = ByteArray(chunk)
    private var offset = 0L

    fun next(): ByteArray? {
        val read = winReadAt(handle, offset, buffer)
        if (read < 0) throw IllegalStateException("read failed")
        if (read == 0) return null
        offset += read
        return buffer.copyOf(read)
    }

    fun close() {
        winCloseFile(handle)
        handle = null
    }
}

internal class FileChunkWriter(path: String) {
    private var handle: HANDLE? = winOpenFile(path, write = true, disposition = WIN_CREATE_ALWAYS)
        ?: throw IllegalStateException("cannot write ${syncFileName(path)}")
    private var offset = 0L

    fun write(bytes: ByteArray) {
        if (!winWriteAt(handle, offset, bytes)) throw IllegalStateException("write failed")
        offset += bytes.size
    }

    fun close() {
        winCloseFile(handle)
        handle = null
    }
}
