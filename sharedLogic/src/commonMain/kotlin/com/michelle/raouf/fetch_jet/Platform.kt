package com.michelle.raouf.fetch_jet

interface Platform {
    val name: String
    val isJvm: Boolean get() = false
}

expect fun getPlatform(): Platform

expect suspend fun getClipboardText(): String?