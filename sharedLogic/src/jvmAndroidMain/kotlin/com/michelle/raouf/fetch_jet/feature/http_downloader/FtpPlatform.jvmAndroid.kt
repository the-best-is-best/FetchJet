package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.network.tls.TLSConfigBuilder
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal actual val ftpTlsInProcess: Boolean = true

internal actual fun ftpInsecureForTests(): Boolean = System.getenv("FETCHJET_FTPS_INSECURE") == "1"

internal actual fun configureFtpTls(builder: TLSConfigBuilder) {
    if (ftpInsecureForTests()) {
        builder.trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }
}

internal actual suspend fun runFtpTool(stdin: String, extraArgs: List<String>): FtpToolResult? = null
