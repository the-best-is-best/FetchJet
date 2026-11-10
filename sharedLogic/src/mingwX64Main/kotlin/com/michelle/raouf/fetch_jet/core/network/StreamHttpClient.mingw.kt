package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking

actual fun createStreamHttpClient(): HttpClient = HttpClient(WinHttpNative) {
    install(HttpRedirect) { checkHttpMethod = false }
    install(HttpTimeout) {
        requestTimeoutMillis = null
        connectTimeoutMillis = 60_000L
        socketTimeoutMillis = 60_000L
    }
    useNetworkSettings()
}

actual fun rawProbeForDiagnostics(url: String, userAgent: String): String = try {
    val result = runBlocking {
        winRunProcess(
            listOf("curl.exe", "-s", "-o", "NUL", "-w", "%{http_code}", "-r", "0-0", "-A", userAgent, url),
            timeoutMs = 20_000
        )
    }
    "curlFromApp=" + result.stdout.trim()
} catch (e: Throwable) {
    "raw probe failed: $e"
}
