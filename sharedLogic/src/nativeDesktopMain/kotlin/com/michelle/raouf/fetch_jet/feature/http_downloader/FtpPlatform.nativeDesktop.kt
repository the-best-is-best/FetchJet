package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import io.ktor.network.tls.TLSConfigBuilder
import kotlinx.coroutines.CancellationException

internal actual val ftpTlsInProcess: Boolean = false

internal actual fun ftpInsecureForTests(): Boolean = DesktopOs.getEnv("FETCHJET_FTPS_INSECURE") == "1"

internal actual fun configureFtpTls(builder: TLSConfigBuilder) = Unit

internal actual suspend fun runFtpTool(stdin: String, extraArgs: List<String>): FtpToolResult? {
    val result = try {
        DesktopOs.runProcess(
            listOf("curl") + extraArgs,
            stdinBytes = stdin.encodeToByteArray(),
            mergeStderr = true,
            timeoutMs = 12L * 60 * 60 * 1000
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
    if (result.timedOut) return null
    return FtpToolResult(result.exitCode, result.stdout)
}
