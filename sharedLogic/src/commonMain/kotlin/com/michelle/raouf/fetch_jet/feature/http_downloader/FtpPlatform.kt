package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.network.tls.TLSConfigBuilder

/**
 * ktor's TLS client works on the JVM and Android only. Everywhere else (the native desktop apps, iOS)
 * FTP over TLS is handed to the system's `curl`, which also checks the server's certificate against the
 * system's trust store.
 */
internal expect val ftpTlsInProcess: Boolean

/** JVM / Android: lets a test accept a self-signed certificate (FETCHJET_FTPS_INSECURE=1). Nothing elsewhere. */
internal expect fun configureFtpTls(builder: TLSConfigBuilder)

internal class FtpToolResult(val exitCode: Int, val output: String)

/**
 * Runs the system's curl with [stdin] as its config file (`curl -K -`, so the login never shows up in a
 * process list). [onStarted] runs once the program is going. Null when this platform has no such tool.
 */
internal expect suspend fun runFtpTool(stdin: String, extraArgs: List<String>): FtpToolResult?

/** Whether the FTPS test hook is on (FETCHJET_FTPS_INSECURE=1): a self-signed certificate is accepted. */
internal expect fun ftpInsecureForTests(): Boolean
