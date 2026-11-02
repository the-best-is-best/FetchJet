package com.michelle.raouf.fetch_jet.core.log

actual object DiagnosticsLog {
    actual val canExport: Boolean get() = false
    actual fun install() {}
    actual fun export(): String? = null
}
