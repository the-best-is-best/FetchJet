package com.michelle.raouf.fetch_jet.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BrowserRequests {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url

    fun open(address: String) {
        val clean = address.trim()
        if (clean.isEmpty() || clean.startsWith("http://", true) || clean.startsWith("https://", true)) _url.value = clean
    }

    fun close() {
        _url.value = null
    }
}
