package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.win.winArmWakeTimer
import com.michelle.raouf.fetch_jet.platform.win.winCancelWakeTimer
import com.michelle.raouf.fetch_jet.platform.win.winKeepSystemAwake

actual object WakeTimer {
    actual val isSupported: Boolean = true

    actual fun arm(epochMs: Long): Boolean = winArmWakeTimer(epochMs)

    actual fun cancel() = winCancelWakeTimer()

    actual fun stayAwake() = winKeepSystemAwake()
}
