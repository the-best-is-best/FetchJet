package com.michelle.raouf.fetch_jet.core.log

actual object DiagnosticsLog {
    actual val canExport: Boolean = false

    actual fun install() {
        NativeLog.install(redirect = true)
    }

    actual fun export(): String? = null
}
