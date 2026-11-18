@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.wcstr
import platform.windows.FOF_ALLOWUNDO
import platform.windows.FOF_NOCONFIRMATION
import platform.windows.FOF_NOERRORUI
import platform.windows.FOF_SILENT
import platform.windows.FO_DELETE
import platform.windows.SHFILEOPSTRUCTW
import platform.windows.SHFileOperationW

internal actual fun osMoveToTrash(path: String): Boolean = memScoped {
    val full = path.replace('/', '\\').removePrefix("\\\\?\\")
    val op = alloc<SHFILEOPSTRUCTW>()
    op.hwnd = null
    op.wFunc = FO_DELETE.convert()
    op.pFrom = (full + "\u0000").wcstr.ptr
    op.pTo = null
    op.fFlags = (FOF_ALLOWUNDO or FOF_NOCONFIRMATION or FOF_SILENT or FOF_NOERRORUI).convert()
    SHFileOperationW(op.ptr) == 0 && op.fAnyOperationsAborted == 0
}
