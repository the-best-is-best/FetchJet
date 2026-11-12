@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.CancelWaitableTimer
import platform.windows.CloseHandle
import platform.windows.CreateWaitableTimerW
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.LARGE_INTEGER
import platform.windows.SetLastError
import platform.windows.SetThreadExecutionState
import platform.windows.SetWaitableTimer

private const val EPOCH_TO_FILETIME_MS = 11_644_473_600_000L
private const val WIN_ERROR_NOT_SUPPORTED = 50u
private const val WIN_ES_SYSTEM_REQUIRED = 0x00000001u

@Volatile
private var wakeTimer: HANDLE? = null

fun winArmWakeTimer(epochMs: Long): Boolean {
    val timer = wakeTimer ?: CreateWaitableTimerW(null, 1, null as String?)?.also { wakeTimer = it }
    if (timer == null) {
        println("WakeTimer: CreateWaitableTimer failed (${GetLastError()})")
        return false
    }
    return memScoped {
        val due = alloc<LARGE_INTEGER>()
        due.QuadPart = (epochMs + EPOCH_TO_FILETIME_MS) * 10_000L
        SetLastError(0u)
        val set = SetWaitableTimer(timer, due.ptr, 0, null, null, 1) != 0
        val code = GetLastError()
        if (!set) println("WakeTimer: SetWaitableTimer failed ($code)")
        else if (code == WIN_ERROR_NOT_SUPPORTED) println("WakeTimer: this computer does not support wake timers")
        set
    }
}

fun winCancelWakeTimer() {
    val timer = wakeTimer ?: return
    wakeTimer = null
    CancelWaitableTimer(timer)
    CloseHandle(timer)
}

fun winKeepSystemAwake() {
    SetThreadExecutionState(WIN_ES_SYSTEM_REQUIRED)
}
