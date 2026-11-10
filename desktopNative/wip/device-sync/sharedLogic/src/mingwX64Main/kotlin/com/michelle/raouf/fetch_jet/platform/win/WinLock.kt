@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.windows.CRITICAL_SECTION
import platform.windows.DeleteCriticalSection
import platform.windows.EnterCriticalSection
import platform.windows.InitializeCriticalSection
import platform.windows.LeaveCriticalSection

class WinLock {
    private val section: CPointer<CRITICAL_SECTION> =
        nativeHeap.alloc<CRITICAL_SECTION>().ptr.also { InitializeCriticalSection(it) }

    @Suppress("unused")
    private val cleaner = createCleaner(section) {
        DeleteCriticalSection(it)
        nativeHeap.free(it)
    }

    fun lock() = EnterCriticalSection(section)

    fun unlock() = LeaveCriticalSection(section)

    inline fun <T> withLock(block: () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }
}
