package com.michelle.raouf.fetch_jet.ios

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.feature.automation.AutomationApi
import com.michelle.raouf.fetch_jet.feature.automation.AutomationReply
import io.ktor.http.decodeURLQueryComponent
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo

internal class IosAutomationServer(private val scope: CoroutineScope) {
    private var accepting: Job? = null
    private var selector: SelectorManager? = null

    fun startIfRequested() {
        val environment = NSProcessInfo.processInfo.environment
        val fromEnvironment = (environment["FETCHJET_AUTOMATION"] as? String) == "1"
        if (fromEnvironment) {
            BridgePairing.setAutomation(true)
            writeTokenFile()
        }
        if (!BridgePairing.automationOn) return
        scope.launch(Dispatchers.IO) { listen() }
    }

    private fun writeTokenFile() {
        try {
            val path = "${PathResolver().getSettingsDirectory()}/automation-token.txt".toPath()
            FileSystem.SYSTEM.write(path) { writeUtf8(BridgePairing.token) }
        } catch (_: Throwable) {
        }
    }

    private suspend fun listen() {
        try {
            val created = SelectorManager(Dispatchers.IO)
            val bound = aSocket(created).tcp().bind("127.0.0.1", PORT) { reuseAddress = true }
            selector = created
            println("automation: listening on 127.0.0.1:$PORT")
            accepting = scope.launch(Dispatchers.IO) {
                while (true) {
                    val socket = try {
                        bound.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        break
                    }
                    launch {
                        try {
                            serve(socket)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                        } finally {
                            try {
                                socket.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            println("automation: cannot listen on 127.0.0.1:$PORT: ${e.message}")
        }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        val values = HashMap<String, String>()
        for (part in raw.split('&')) {
            val cut = part.indexOf('=')
            if (cut <= 0) continue
            val value = part.substring(cut + 1)
            values[part.substring(0, cut)] = try {
                value.decodeURLQueryComponent(plusIsSpace = true)
            } catch (_: Throwable) {
                value
            }
        }
        return values
    }

    private fun loopbackHost(host: String?): Boolean {
        if (host == null) return true
        val name = if (host.startsWith("[")) host.substringBefore("]") + "]" else host.substringBefore(':')
        return name.lowercase() in setOf("localhost", "127.0.0.1", "[::1]")
    }

    private suspend fun serve(socket: Socket) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        val headers = HashMap<String, String>()
        val requestLine = withTimeoutOrNull(HEAD_TIMEOUT_MS) {
            val first = input.readUTF8Line(MAX_LINE) ?: return@withTimeoutOrNull null
            var count = 0
            while (count++ < MAX_HEADERS) {
                val line = input.readUTF8Line(MAX_LINE) ?: break
                if (line.isEmpty()) break
                val cut = line.indexOf(':')
                if (cut > 0) headers.getOrPut(line.substring(0, cut).trim().lowercase()) { line.substring(cut + 1).trim() }
            }
            first
        } ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val target = parts[1]
        val path = target.substringBefore('?')
        val reply = when {
            !loopbackHost(headers["host"]) -> AutomationReply(403, """{"error":"forbidden"}""")
            path.startsWith("/api") -> AutomationApi.handle(
                parts[0].uppercase(),
                path,
                headers["origin"] != null,
                !headers["x-fetchjet"].isNullOrBlank(),
                headers[BridgePairing.TOKEN_HEADER.lowercase()],
                parseQuery(target.substringAfter('?', "")),
            )

            else -> AutomationReply(404, """{"error":"not found"}""")
        }
        val bytes = reply.body.encodeToByteArray()
        val head = "HTTP/1.1 ${reply.code} ${statusText(reply.code)}\r\nContent-Type: application/json; charset=UTF-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.writeStringUtf8(head)
        if (bytes.isNotEmpty()) output.writeFully(bytes)
        output.flush()
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        else -> "Status"
    }

    private companion object {
        const val PORT = 28450
        const val MAX_LINE = 8192
        const val MAX_HEADERS = 64
        const val HEAD_TIMEOUT_MS = 5_000L
    }
}
