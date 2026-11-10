package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout

internal object AppHttp {
    private val holder = NetworkClient {
        HttpClient(WinHttpNative) {
            install(HttpRedirect) { checkHttpMethod = false }
            install(HttpTimeout) {
                requestTimeoutMillis = null
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 60_000L
            }
            useNetworkSettings()
        }
    }

    val client: HttpClient get() = holder.current
}
