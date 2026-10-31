package com.michelle.raouf.fetch_jet

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.feature.media_extractor.inspectPlatformQualities
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * The same local bridge as the desktop app (127.0.0.1:28450), so the FetchJet extension in
 * Firefox for Android can hand links to this app: /ping, /config, /inspect, /add, /links,
 * /download, /show. Only loopback connections are accepted, and the same checks apply: a
 * loopback Host header, an extension Origin when one is sent, and the X-FetchJet header on
 * every call that does something.
 *
 * The server lives as long as the app process: while FetchJet is open, in the background,
 * or downloading (foreground service).
 */
object ExtensionBridgeServer {
    private const val PORT = 28450
    private const val MAX_BODY = 4 * 1024 * 1024

    @Volatile
    private var started = false
    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "fetchjet-bridge").apply {
            isDaemon = true
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        Thread({
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
                    while (true) {
                        val socket = server.accept()
                        pool.execute { handle(socket) }
                    }
                }
            } catch (e: Exception) {
                println("ExtensionBridgeServer: stopped (${e.message})")
                started = false
            }
        }, "fetchjet-bridge-accept").apply { isDaemon = true }.start()
    }

    // ---- HTTP plumbing ------------------------------------------------------------------

    private class Req(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: ByteArray
    ) {
        fun header(name: String) = headers[name.lowercase()]
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream())
                val req = readRequest(input) ?: return
                route(req, s.getOutputStream())
            } catch (_: Exception) {
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (c == '\n'.code) return buf.toString("ISO-8859-1").trimEnd('\r')
            if (buf.size() > 16 * 1024) return null
            buf.write(c)
        }
    }

    private fun readRequest(input: InputStream): Req? {
        val first = readLine(input) ?: return null
        val parts = first.split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] =
                line.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > MAX_BODY) return null
        val body = ByteArray(len.coerceAtLeast(0))
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) break
            read += n
        }
        val target = parts[1]
        return Req(
            method = parts[0].uppercase(),
            path = target.substringBefore('?'),
            query = target.substringAfter('?', ""),
            headers = headers,
            body = body
        )
    }

    private fun trusted(r: Req): Boolean {
        val rawHost = r.header("Host")?.lowercase()
        val host = when {
            rawHost == null -> null
            rawHost.startsWith("[") -> rawHost.substringBefore("]") + "]"
            else -> rawHost.substringBefore(':')
        }
        if (host != null && host !in setOf("localhost", "127.0.0.1", "[::1]")) return false
        val origin = r.header("Origin") ?: return true
        return origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://") ||
                origin.startsWith("extension://")
    }

    private fun reply(
        out: OutputStream,
        r: Req,
        status: Int,
        body: String,
        contentType: String = "text/plain; charset=UTF-8",
        extra: Map<String, String> = emptyMap()
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; 409 -> "Conflict"; else -> "Error"
        }
        val sb = StringBuilder("HTTP/1.1 $status $reason\r\n")
        sb.append("Content-Type: $contentType\r\n")
        r.header("Origin")
            ?.let { if (trusted(r)) sb.append("Access-Control-Allow-Origin: $it\r\n") }
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Content-Length: ${if (status == 204) 0 else bytes.size}\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (status != 204) out.write(bytes)
        out.flush()
    }

    private fun preflight(out: OutputStream, r: Req, methods: String) =
        reply(
            out, r, 204, "", extra = mapOf(
                "Access-Control-Allow-Methods" to methods,
                "Access-Control-Allow-Headers" to "Content-Type, X-FetchJet, X-FetchJet-Cookie, X-FetchJet-Referer"
            )
        )

    private fun queryParam(q: String, key: String): String? =
        q.split("&").firstNotNullOfOrNull { p ->
            val kv = p.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == key) try {
                URLDecoder.decode(kv[1], "UTF-8")
            } catch (_: Exception) {
                kv[1]
            } else null
        }

    private fun isAllowedUrl(url: String): Boolean {
        val u = url.trim()
        return !(u.length > 8192 || u.any { it < ' ' }) &&
                (u.startsWith("http://", true) || u.startsWith(
                    "https://",
                    true
                ) || u.startsWith("magnet:", true))
    }

    private fun json(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
    }

    // ---- inspection cache (same as desktop) ------------------------------------------------

    private val inspectCache =
        object : LinkedHashMap<String, Pair<Long, List<MediaQualityEntry>>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<MediaQualityEntry>>>?) =
                size > 40
        }

    private fun inspectCached(
        url: String,
        requestHeaders: Map<String, String> = emptyMap()
    ): List<MediaQualityEntry> {
        synchronized(inspectCache) {
            inspectCache[url]?.let { (at, list) ->
                if (System.currentTimeMillis() - at < 30 * 60 * 1000L && list.isNotEmpty()) return list
            }
        }
        val fresh = runBlocking { inspectPlatformQualities(url, requestHeaders) }
        if (fresh.isNotEmpty()) synchronized(inspectCache) {
            inspectCache[url] = System.currentTimeMillis() to fresh
        }
        return fresh
    }

    // ---- routes -----------------------------------------------------------------------------

    private fun route(r: Req, out: OutputStream) {
        if (!trusted(r)) return reply(out, r, 403, "forbidden")
        val isOptions = r.method == "OPTIONS"
        when (r.path) {
            "/ping" -> if (isOptions) preflight(out, r, "GET, OPTIONS") else reply(
                out,
                r,
                200,
                "PONG"
            )

            "/config" -> if (isOptions) preflight(out, r, "GET, OPTIONS") else {
                val version = try {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                        ?: "dev"
                } catch (_: Exception) {
                    "dev"
                }
                reply(
                    out, r, 200,
                    """{"app":"FetchJet","platform":"android","version":"${json(version)}","bridge":2,""" +
                            """"mediaExtractor":${AppFeatures.mediaExtractor},"torrent":${AppFeatures.torrent},""" +
                            """"endpoints":["/ping","/config","/add","/links","/download","/inspect"]}""",
                    "application/json; charset=UTF-8"
                )
            }

            "/inspect" -> {
                if (isOptions) return preflight(out, r, "GET, OPTIONS")
                if (r.header("X-FetchJet").isNullOrBlank()) return reply(
                    out,
                    r,
                    403,
                    "missing X-FetchJet header"
                )
                val url = queryParam(r.query, "url").orEmpty()
                if (url.isBlank() || !isAllowedUrl(url)) {
                    return reply(
                        out,
                        r,
                        400,
                        """{"error":"Missing or unsupported url parameter"}""",
                        "application/json; charset=UTF-8"
                    )
                }
                try {
                    val headers = buildMap {
                        r.header("X-FetchJet-Cookie")?.takeIf { it.isNotBlank() }
                            ?.let { put("Cookie", it) }
                        r.header("X-FetchJet-Referer")?.takeIf { it.isNotBlank() }
                            ?.let { put("Referer", it) }
                    }
                    com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.remember(
                        headers["Referer"] ?: url, headers
                    )
                    val list = inspectCached(url, headers).joinToString(",", "[", "]") { q ->
                        """{"index":${q.index},"quality":"${json(q.qualityLabel)}","format":"${
                            json(
                                q.format
                            )
                        }",""" +
                                """"size":${q.size},"directUrl":"${json(q.directUrl)}","title":"${
                                    json(
                                        q.videoTitle ?: ""
                                    )
                                }",""" +
                                """"formatId":"${json(q.formatId ?: "")}","duration":${q.durationSeconds}}"""
                    }
                    reply(out, r, 200, list, "application/json; charset=UTF-8")
                } catch (e: Exception) {
                    reply(
                        out,
                        r,
                        500,
                        """{"error":"${json(e.message ?: "Failed to inspect")}"}""",
                        "application/json; charset=UTF-8"
                    )
                }
            }

            "/add", "/links" -> {
                if (isOptions) return preflight(out, r, "POST, OPTIONS")
                if (r.method != "POST") return reply(out, r, 405, "POST only")
                if (r.header("X-FetchJet").isNullOrBlank()) return reply(
                    out,
                    r,
                    403,
                    "missing X-FetchJet header"
                )
                val body = try {
                    MiniJson.parse(r.body.toString(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
                if (r.path == "/add") {
                    @Suppress("UNCHECKED_CAST")
                    val obj = body as? Map<String, Any?>
                    val req = obj?.toRequest()
                        ?: return reply(
                            out,
                            r,
                            400,
                            """{"error":"missing or unsupported url"}""",
                            "application/json; charset=UTF-8"
                        )
                    if (!obj.getString("formatId")
                            .isNullOrBlank() && req.mediaQualities.isEmpty()
                    ) {
                        return reply(
                            out,
                            r,
                            409,
                            """{"error":"quality not available any more, inspect again"}""",
                            "application/json; charset=UTF-8"
                        )
                    }
                    deliver { ExternalDownloadBridge.dispatch(req) }
                    reply(out, r, 200, """{"ok":true}""", "application/json; charset=UTF-8")
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val items: List<Any?> = when (body) {
                        is List<*> -> body
                        is Map<*, *> -> (body as Map<String, Any?>)["links"] as? List<Any?>
                            ?: emptyList()

                        else -> emptyList()
                    }

                    @Suppress("UNCHECKED_CAST")
                    val reqs = items.mapNotNull { (it as? Map<String, Any?>)?.toRequest() }
                        .distinctBy { it.url }.take(500)
                    if (reqs.isEmpty()) return reply(
                        out,
                        r,
                        400,
                        """{"error":"no usable links"}""",
                        "application/json; charset=UTF-8"
                    )
                    deliver { ExternalDownloadBridge.dispatchBatch(reqs) }
                    reply(
                        out,
                        r,
                        200,
                        """{"ok":true,"accepted":${reqs.size}}""",
                        "application/json; charset=UTF-8"
                    )
                }
            }

            "/download" -> {
                if (isOptions) return preflight(out, r, "GET, OPTIONS")
                if (r.header("X-FetchJet").isNullOrBlank()) return reply(
                    out,
                    r,
                    403,
                    "missing X-FetchJet header"
                )
                val url = queryParam(r.query, "url").orEmpty()
                if (url.isBlank() || !isAllowedUrl(url)) return reply(
                    out,
                    r,
                    400,
                    "unsupported url"
                )
                val cookie = queryParam(r.query, "cookie").orEmpty()
                deliver { ExternalDownloadBridge.dispatch(url, cookie) }
                reply(out, r, 200, "OK")
            }

            "/show" -> {
                if (r.header("X-FetchJet").isNullOrBlank()) return reply(
                    out,
                    r,
                    403,
                    "missing X-FetchJet header"
                )
                deliver {}
                reply(out, r, 200, "OK")
            }

            else -> reply(out, r, 404, "not found")
        }
    }

    private fun Map<String, Any?>.toRequest(): ExternalDownloadRequest? {
        val url = getString("url")?.trim().orEmpty()
        if (url.isBlank() || !isAllowedUrl(url)) return null
        fun hdr(key: String) =
            getString(key)?.trim().orEmpty().take(16 * 1024).filter { it != '\r' && it != '\n' }

        val formatId = getString("formatId")?.trim().orEmpty()
        val qualities = if (formatId.isNotBlank()) {
            val list = try {
                inspectCached(url, buildMap {
                    hdr("cookie").takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                    hdr("referer").takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                })
            } catch (_: Exception) {
                emptyList()
            }
            val match = list.firstOrNull { it.formatId == formatId }
            if (match == null) emptyList() else list.map { it.copy(isSelected = it === match) }
        } else emptyList()
        return ExternalDownloadRequest(
            url = url,
            cookie = hdr("cookie"),
            referer = hdr("referer"),
            userAgent = hdr("userAgent"),
            fileName = getString("fileName")?.trim().orEmpty().take(255),
            mediaQualities = qualities,
            direct = (this["direct"] as? Boolean) == true,
            openClip = (this["openClip"] as? Boolean) == true
        )
    }

    /**
     * Hands the request to the app on the main thread and brings FetchJet forward. Android
     * may refuse to start an activity from the background (Android 10+); the toast then tells
     * the user the link is waiting in FetchJet.
     */
    private fun deliver(action: () -> Unit) {
        main.post {
            action()
            try {
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            } catch (_: Exception) {
            }
            Toast.makeText(appContext, "Sent to FetchJet", Toast.LENGTH_SHORT).show()
        }
    }
}
