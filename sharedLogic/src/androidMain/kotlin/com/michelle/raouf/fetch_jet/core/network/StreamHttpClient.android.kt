package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

actual fun createStreamHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            protocols(listOf(Protocol.HTTP_1_1))
            retryOnConnectionFailure(true)
            // Diagnostic: what OkHttp really put on the wire when a CDN says 403.
            addNetworkInterceptor { chain ->
                val req = chain.request()
                val resp = chain.proceed(req)
                if (resp.code == 403) {
                    com.michelle.raouf.fetch_jet.core.log.AppLog.e(
                        "StreamHttpClient 403\n  okUrl : ${req.url}\n  okHdr : " +
                                req.headers.joinToString { "${it.first}=${it.second}" } +
                                "\n  proto : ${resp.protocol}  tls=${resp.handshake?.tlsVersion}/${resp.handshake?.cipherSuite}"
                    )
                }
                resp
            }
            connectTimeout(60, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
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
