package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout

actual fun createStreamHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            // NSURLSession has no HTTP/1.1-only switch; the connection limit keeps the
            // ranged requests tame.
            setHTTPMaximumConnectionsPerHost(32)
        }
    }
    install(HttpRedirect) { checkHttpMethod = false }
    install(HttpTimeout) {
        requestTimeoutMillis = null
        connectTimeoutMillis = 60_000L
        socketTimeoutMillis = 60_000L
    }
}

actual fun rawProbeForDiagnostics(url: String, userAgent: String): String = ""
