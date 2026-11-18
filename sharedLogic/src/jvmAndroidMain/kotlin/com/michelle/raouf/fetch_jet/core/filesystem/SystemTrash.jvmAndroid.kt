package com.michelle.raouf.fetch_jet.core.filesystem

actual object SystemTrash {
    actual val isSupported: Boolean = false

    actual fun moveToTrash(path: String): Boolean = false
}
