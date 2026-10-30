package com.michelle.raouf.fetch_jet.core.browser

import java.io.File
import java.util.concurrent.TimeUnit

actual object BrowserIntegration {
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
    private const val EDGE_KEY = "HKCU\\Software\\Microsoft\\Edge\\Extensions\\$EDGE_EXTENSION_ID"
    private const val EDGE_UPDATE_URL = "https://edge.microsoft.com/extensionwebstorebase/v1/crx"

    actual val canAddToEdgeAutomatically: Boolean get() = isWindows

    private fun reg(vararg args: String): Pair<Int, String> = try {
        val p = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroy(); -1 to "timeout"
        } else p.exitValue() to out
    } catch (e: Exception) {
        -1 to (e.message ?: "reg failed")
    }

    actual fun isEdgeRegistered(): Boolean =
        isWindows && reg("query", EDGE_KEY, "/v", "update_url").first == 0

    actual fun addToEdge(): Result<Unit> {
        if (!isWindows) return Result.failure(UnsupportedOperationException("Windows only"))
        val (code, out) = reg(
            "add",
            EDGE_KEY,
            "/v",
            "update_url",
            "/t",
            "REG_SZ",
            "/d",
            EDGE_UPDATE_URL,
            "/f"
        )
        return if (code == 0) Result.success(Unit) else Result.failure(
            Exception(
                out.trim().ifBlank { "reg add failed" })
        )
    }

    actual fun removeFromEdge(): Result<Unit> {
        if (!isWindows) return Result.failure(UnsupportedOperationException("Windows only"))
        val (code, out) = reg("delete", EDGE_KEY, "/f")
        return if (code == 0) Result.success(Unit) else Result.failure(
            Exception(
                out.trim().ifBlank { "reg delete failed" })
        )
    }

    actual fun setUpOnFirstRun() {
        if (!isWindows) return
        try {
            val marker =
                File(System.getProperty("user.home"), ".fetch_jet/browser-integration.done")
            if (marker.exists()) return
            marker.parentFile?.mkdirs()
            if (!isEdgeRegistered()) addToEdge()
            marker.writeText("edge")
        } catch (_: Exception) {
        }
    }
}
