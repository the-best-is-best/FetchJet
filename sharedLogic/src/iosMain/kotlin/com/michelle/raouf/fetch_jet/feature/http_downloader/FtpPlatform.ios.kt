package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.network.tls.TLSConfigBuilder

internal actual val ftpTlsInProcess: Boolean = false

internal actual fun ftpInsecureForTests(): Boolean = false

internal actual fun configureFtpTls(builder: TLSConfigBuilder) = Unit

internal actual suspend fun runFtpTool(stdin: String, extraArgs: List<String>): FtpToolResult? = null
