package com.michelle.raouf.fetch_jet.core.power

actual object WakeTimer {
    actual val isSupported: Boolean = false

    actual fun arm(epochMs: Long): Boolean = false

    actual fun cancel() = Unit

    actual fun stayAwake() = Unit
}
