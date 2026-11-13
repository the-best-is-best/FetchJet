package com.michelle.raouf.fetch_jet.nativeapp.bridge

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class HttpRequest(
    val method: String,
    val path: String,
    val query: String,
    private val headers: Map<String, String>,
    val body: ByteArray?,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun queryValues(): Map<String, String> {
        val values = HashMap<String, String>()
        for (part in query.split('&')) {
            val cut = part.indexOf('=')
            if (cut <= 0) continue
            val raw = part.substring(cut + 1)
            values[part.substring(0, cut)] = try {
                percentDecode(raw)
            } catch (_: Throwable) {
                raw
            }
        }
        return values
    }
}

internal class HttpReply(
    val status: Int,
    val body: String = "",
    val contentType: String = "text/plain; charset=UTF-8",
    val headers: List<Pair<String, String>> = emptyList(),
)

internal fun percentDecode(text: String): String {
    if ('%' !in text && '+' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '+' -> {
                bytes.add(' '.code.toByte())
                i++
            }

            c == '%' -> {
                if (i + 2 > text.lastIndex) throw IllegalArgumentException("incomplete escape")
                val value = text.substring(i + 1, i + 3).toInt(16)
                bytes.add(value.toByte())
                i += 3
            }

            else -> {
                c.toString().encodeToByteArray().forEach { bytes.add(it) }
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun statusText(status: Int): String = when (status) {
    200 -> "OK"
    204 -> "No Content"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    409 -> "Conflict"
    413 -> "Payload Too Large"
    500 -> "Internal Server Error"
    else -> "Status"
}

internal class HttpListener(
    private val scope: CoroutineScope,
    private val host: String,
    private val port: Int,
    private val maxBodyBytes: Int,
    private val handler: suspend (HttpRequest) -> HttpReply,
) {
    private var selector: SelectorManager? = null
    private var server: ServerSocket? = null
    private var accepting: Job? = null
    private val open = AtomicInt(0)

    suspend fun start(): Boolean {
        return try {
            val created = SelectorManager(Dispatchers.IO)
            val bound = aSocket(created).tcp().bind(host, port) { reuseAddress = canReuseAddress() }
            selector = created
            server = bound
            accepting = scope.launch(Dispatchers.IO) {
                while (true) {
                    val socket = try {
                        bound.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        break
                    }
                    if (open.value >= MAX_CONNECTIONS) {
                        socket.close()
                        continue
                    }
                    open.incrementAndGet()
                    launch {
                        try {
                            serve(socket)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                        } finally {
                            open.decrementAndGet()
                            try {
                                socket.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Throwable) {
            println("bridge: cannot listen on $host:$port: ${e.message}")
            false
        }
    }

    fun stop() {
        accepting?.cancel()
        try {
            server?.close()
        } catch (_: Throwable) {
        }
        try {
            selector?.close()
        } catch (_: Throwable) {
        }
    }

    private suspend fun readHead(input: ByteReadChannel): Pair<String, Map<String, String>>? {
        val requestLine = input.readUTF8Line(MAX_LINE) ?: return null
        val headers = HashMap<String, String>()
        var total = requestLine.length
        while (true) {
            val line = input.readUTF8Line(MAX_LINE) ?: return null
            if (line.isEmpty()) break
            total += line.length
            if (total > MAX_HEAD) return null
            val cut = line.indexOf(':')
            if (cut > 0) {
                val name = line.substring(0, cut).trim().lowercase()
                if (name !in headers) headers[name] = line.substring(cut + 1).trim()
            }
        }
        return requestLine to headers
    }

    private suspend fun readChunked(input: ByteReadChannel): ByteArray? {
        val parts = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val sizeLine = input.readUTF8Line(64) ?: return null
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: return null
            if (size == 0) {
                while (true) {
                    val trailer = input.readUTF8Line(MAX_LINE) ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            total += size
            if (total > maxBodyBytes) return null
            parts.add(input.readByteArray(size))
            input.readUTF8Line(8)
        }
        val body = ByteArray(total)
        var at = 0
        for (part in parts) {
            part.copyInto(body, at)
            at += part.size
        }
        return body
    }

    private suspend fun serve(socket: Socket) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        val head = withTimeoutOrNull(HEAD_TIMEOUT_MS) { readHead(input) } ?: return
        val (requestLine, headers) = head
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        var body: ByteArray? = ByteArray(0)
        if (length > maxBodyBytes) {
            body = null
        } else if (length > 0 || chunked) {
            if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
                output.writeStringUtf8("HTTP/1.1 100 Continue\r\n\r\n")
                output.flush()
            }
            body = withTimeoutOrNull(BODY_TIMEOUT_MS) {
                if (chunked) readChunked(input) else input.readByteArray(length.toInt())
            }
        }
        val request = HttpRequest(parts[0].uppercase(), path, query, headers, body)
        val reply = try {
            handler(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            HttpReply(500, "error")
        }
        val bytes = if (reply.status == 204) ByteArray(0) else reply.body.encodeToByteArray()
        val text = StringBuilder(256)
        text.append("HTTP/1.1 ").append(reply.status).append(' ').append(statusText(reply.status)).append("\r\n")
        text.append("Content-Type: ").append(reply.contentType).append("\r\n")
        for ((name, value) in reply.headers) text.append(name).append(": ").append(value).append("\r\n")
        if (reply.status != 204) text.append("Content-Length: ").append(bytes.size).append("\r\n")
        text.append("Connection: close\r\n\r\n")
        output.writeStringUtf8(text.toString())
        if (bytes.isNotEmpty()) output.writeFully(bytes)
        output.flush()
    }

    private companion object {
        const val MAX_CONNECTIONS = 40
        const val MAX_LINE = 40 * 1024
        const val MAX_HEAD = 96 * 1024
        const val HEAD_TIMEOUT_MS = 10_000L
        const val BODY_TIMEOUT_MS = 30_000L
    }
}

// A restarted app must be able to take the same port at once: on Linux and macOS a closed listener
// keeps the port for a minute unless the address may be reused. On Windows SO_REUSEADDR would let
// another program share the port, so it stays off there.
@OptIn(ExperimentalNativeApi::class)
private fun canReuseAddress(): Boolean = Platform.osFamily != OsFamily.WINDOWS
