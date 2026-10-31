package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import java.io.File
import java.util.concurrent.TimeUnit

actual object BrowserIntegration {
    // The Store (MSIX) build may not change Edge settings by itself (its registry writes are
    // private to the package anyway): there the page links to Edge Add-ons instead.
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win") &&
            !WindowsStorePackage.isInstalled
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

    /**
     * Registers the fetchjet:// link (HKCU, no admin) so the browser extension can start the
     * installed app with a link when it is not running. The Store build gets it from its
     * manifest instead; a Gradle / IDE run has no stable path to register.
     */
    fun registerUrlScheme() {
        val onWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        if (!onWindows || WindowsStorePackage.isInstalled) return
        val exe = System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() } ?: return
        try {
            val cmd = "\"$exe\" \"%1\""
            val current = reg(
                "query",
                "HKCU\\Software\\Classes\\fetchjet\\shell\\open\\command",
                "/ve"
            ).second
            if (current.contains(exe, ignoreCase = true)) return
            reg(
                "add",
                "HKCU\\Software\\Classes\\fetchjet",
                "/ve",
                "/t",
                "REG_SZ",
                "/d",
                "URL:FetchJet link",
                "/f"
            )
            reg(
                "add",
                "HKCU\\Software\\Classes\\fetchjet",
                "/v",
                "URL Protocol",
                "/t",
                "REG_SZ",
                "/d",
                "",
                "/f"
            )
            reg(
                "add",
                "HKCU\\Software\\Classes\\fetchjet\\DefaultIcon",
                "/ve",
                "/t",
                "REG_SZ",
                "/d",
                "\"$exe\",0",
                "/f"
            )
            reg(
                "add",
                "HKCU\\Software\\Classes\\fetchjet\\shell\\open\\command",
                "/ve",
                "/t",
                "REG_SZ",
                "/d",
                cmd,
                "/f"
            )
        } catch (_: Exception) {
        }
    }

    actual fun setUpOnFirstRun() {
        registerUrlScheme()
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

    actual val canKeepReadyForBrowser: Boolean get() = false
    actual fun isKeptReadyForBrowser(): Boolean = false
    actual fun setKeepReadyForBrowser(enabled: Boolean) {}
    actual fun restoreKeepReady() {}
}
