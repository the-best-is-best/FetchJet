package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runForOutput
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout

actual fun createStreamHttpClient(): HttpClient = HttpClient(DesktopOs.httpEngine) {
    install(HttpRedirect) { checkHttpMethod = false }
    install(HttpTimeout) {
        requestTimeoutMillis = null
        connectTimeoutMillis = 60_000L
        socketTimeoutMillis = 60_000L
    }
    useNetworkSettings()
}

actual fun rawProbeForDiagnostics(url: String, userAgent: String): String = try {
    val output = DesktopOs.runForOutput(
        listOf(DesktopOs.curlCommand, "-s", "-o", DesktopOs.nullDevice, "-w", "%{http_code}", "-r", "0-0", "-A", userAgent, url),
        timeoutMs = 20_000
    )
    if (output == null) "raw probe not available" else "curlFromApp=" + output.trim()
} catch (e: Throwable) {
    "raw probe failed: $e"
}
