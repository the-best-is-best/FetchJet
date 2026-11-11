@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.macos

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString
import platform.darwin.TASK_VM_INFO
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.darwin.task_vm_info_data_t

object MacDesktopTools {
    fun clipboardText(): String? = try {
        NSPasteboard.generalPasteboard.stringForType(NSPasteboardTypeString)
    } catch (_: Throwable) {
        null
    }

    fun setClipboardText(text: String): Boolean = try {
        val board = NSPasteboard.generalPasteboard
        board.clearContents()
        board.setString(text, NSPasteboardTypeString)
    } catch (_: Throwable) {
        false
    }

    fun clipboardSequence(): Long = try {
        NSPasteboard.generalPasteboard.changeCount
    } catch (_: Throwable) {
        0L
    }

    fun open(target: String): Boolean = macSpawnDetached(listOf("/usr/bin/open", target))

    fun reveal(path: String): Boolean = macSpawnDetached(listOf("/usr/bin/open", "-R", path))

    class Memory(val footprint: Long, val resident: Long)

    fun memory(): Memory? = memScoped {
        val info = alloc<task_vm_info_data_t>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info_data_t>() / 4).convert()
        val status = task_info(mach_task_self_, TASK_VM_INFO.convert(), info.ptr.reinterpret(), count.ptr)
        if (status != 0) null else Memory(info.phys_footprint.toLong(), info.resident_size.toLong())
    }
}
