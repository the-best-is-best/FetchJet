package com.michelle.raouf.fetch_jet.core.filesystem

expect object SystemTrash {
    val isSupported: Boolean

    fun moveToTrash(path: String): Boolean
}
