package com.michelle.raouf.fetch_jet.nativeapp

import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.startup.AUTOSTART_ARG
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import com.michelle.raouf.fetch_jet.nativeapp.bridge.percentDecode
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
internal data class LaunchResult(
    val exit: Boolean = false,
    val hidden: Boolean = false,
    val loginStart: Boolean = false,
    val url: String = "",
)

internal object Launch {
    @Volatile
    var pendingUrl: String = ""

    @Volatile
    var loginStart: Boolean = false

    fun parseUrlFromArg(arg: String): String {
        val clean = arg.trim().removeSurrounding("\"")
        val schemePrefix = when {
            clean.startsWith("fetchjet://", ignoreCase = true) -> "fetchjet://"
            clean.startsWith("kortex://", ignoreCase = true) -> "kortex://"
            else -> null
        }
        if (schemePrefix != null) {
            val queryIndex = clean.indexOf('?')
            if (queryIndex != -1 && queryIndex < clean.length - 1) {
                val query = clean.substring(queryIndex + 1)
                for (param in query.split("&")) {
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].equals("url", ignoreCase = true)) {
                        return try {
                            percentDecode(parts[1])
                        } catch (_: Throwable) {
                            parts[1]
                        }
                    }
                }
            }
            val remainder = clean.substring(schemePrefix.length).trimStart('/')
            if (remainder.startsWith("http://", ignoreCase = true) || remainder.startsWith("https://", ignoreCase = true)) {
                return remainder
            }
        }
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("magnet:", ignoreCase = true)
        ) {
            return clean
        }
        val path = clean.removePrefix("file://")
        if (clean.lowercase().endsWith(".torrent") && winExists(path) && !winIsDirectory(path)) {
            return winFullPath(path)
        }
        return ""
    }

    private fun get(port: Int, path: String, timeoutMs: Long, withToken: Boolean): Pair<Int, String>? = try {
        runBlocking {
            val client = HttpClient(WinHttpNative) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                    requestTimeoutMillis = timeoutMs * 3
                }
            }
            try {
                val response = client.get("http://127.0.0.1:$port$path") {
                    header("X-FetchJet", "1")
                    if (withToken) header(BridgePairing.TOKEN_HEADER, BridgePairing.token)
                }
                response.status.value to response.bodyAsText()
            } finally {
                client.close()
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun sendToRunningInstance(port: Int, url: String): Boolean {
        val answer = get(port, "/download?url=" + url.encodeURLParameter(spaceToPlus = true), 800, withToken = true)
        return answer != null && answer.first in 200..299
    }

    private fun anotherInstanceRunning(port: Int): Boolean {
        val answer = get(port, "/ping", 500, withToken = false) ?: return false
        val running = answer.first in 200..299 && answer.second.trim() == "PONG"
        if (running) get(port, "/show", 500, withToken = true)
        return running
    }

    fun prepare(config: StartConfig): LaunchResult {
        val port = if (config.bridgePort > 0) config.bridgePort else com.michelle.raouf.fetch_jet.nativeapp.bridge.BRIDGE_PORT
        val startedAtLogin = config.args.any { it.trim() == AUTOSTART_ARG } ||
            (config.args.isEmpty() && WinApp.looksLikeLoginStart())
        val rest = config.args.filter { it.trim() != AUTOSTART_ARG }
        val url = rest.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { parseUrlFromArg(it) }.orEmpty()
        if (config.bridge) {
            val attempts = if (config.secondInstance) 10 else 1
            for (attempt in 1..attempts) {
                val handed = if (url.isNotBlank()) sendToRunningInstance(port, url) else anotherInstanceRunning(port)
                if (handed) return LaunchResult(exit = true)
                if (attempt < attempts) runBlocking { delay(500) }
            }
            if (config.secondInstance) return LaunchResult(exit = true)
        }
        pendingUrl = url
        loginStart = startedAtLogin
        val hidden = startedAtLogin && try {
            StartupPreferences.startHidden
        } catch (_: Throwable) {
            false
        }
        return LaunchResult(exit = false, hidden = hidden, loginStart = startedAtLogin, url = url)
    }
}
