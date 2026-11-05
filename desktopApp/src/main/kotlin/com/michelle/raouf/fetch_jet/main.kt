package com.michelle.raouf.fetch_jet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getString
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tbib.kmp_localization.LocalizationManager
import fetchjet.sharedui.generated.resources.Res
import fetchjet.sharedui.generated.resources.ic_fetchjet_logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

private var pendingStartupUrl: String? = null

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
            query.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && parts[0].equals("url", ignoreCase = true)) {
                    return try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (_: Exception) {
                        parts[1]
                    }
                }
            }
        }
        val remainder = clean.substring(schemePrefix.length).trimStart('/')
        if (remainder.startsWith("http://", ignoreCase = true) || remainder.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            return remainder
        }
    }
    if (clean.startsWith("http://", ignoreCase = true) ||
        clean.startsWith("https://", ignoreCase = true) ||
        clean.startsWith("magnet:", ignoreCase = true)
    ) {
        return clean
    }
    // A .torrent file opened with FetchJet (file association / drag onto the exe).
    val asFile = clean.removePrefix("file://").let { File(it) }
    if (clean.lowercase().endsWith(".torrent") && asFile.isFile) {
        return asFile.absolutePath
    }
    return ""
}

/** GET /ping on the bridge port answers only when another FetchJet is already running. */
fun isAnotherInstanceRunning(): Boolean {
    return try {
        val conn = URI("http://127.0.0.1:$BRIDGE_PORT/ping").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 500
        conn.readTimeout = 500
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-FetchJet", "1")
        val ok = conn.responseCode in 200..299 && conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8).trim() == "PONG" }
        if (ok) {
            // Ask it to show itself (the /show endpoint is best-effort).
            try {
                val c2 = URI("http://127.0.0.1:$BRIDGE_PORT/show").toURL().openConnection() as HttpURLConnection
                c2.connectTimeout = 500; c2.readTimeout = 500
                c2.setRequestProperty("X-FetchJet", "1")
                c2.setRequestProperty(BridgePairing.TOKEN_HEADER, BridgePairing.token)
                c2.responseCode
            } catch (_: Exception) {}
        }
        ok
    } catch (_: Exception) {
        false
    }
}

fun trySendToRunningInstance(url: String): Boolean {
    return try {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val u = URI("http://127.0.0.1:$BRIDGE_PORT/download?url=$encoded").toURL()
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 800
        conn.readTimeout = 800
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-FetchJet", "1")
        conn.setRequestProperty(BridgePairing.TOKEN_HEADER, BridgePairing.token)
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}


private const val BRIDGE_PORT = 28450

/**
 * Last yt-dlp inspections, so the extension can list qualities (GET /inspect) and then
 * start the chosen one (POST /add with formatId) without running yt-dlp twice.
 */
private val inspectGates = HashMap<String, Any>()

/** /add requests seen in the last seconds (a click reaching us twice must not start twice). */
private val recentAdds = HashMap<String, Long>()
private val inspectCache = object :
    LinkedHashMap<String, Pair<Long, List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>>>(
        16,
        0.75f,
        true
    ) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>>>?) =
        size > 40
}

private fun inspectCached(
    url: String,
    requestHeaders: Map<String, String> = emptyMap()
): List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> {
    val key = com.michelle.raouf.fetch_jet.feature.media_extractor.MediaUrls.canonical(url)
    fun cached(): List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>? =
        synchronized(inspectCache) {
            inspectCache[key]?.let { (at, list) ->
            // Direct stream URLs expire (YouTube: ~6 h); keep results for 30 minutes.
                if (System.currentTimeMillis() - at < 30 * 60 * 1000L && list.isNotEmpty()) list else null
        }
    }
    cached()?.let { return it }
    // The popup asks for the same video in two spellings at once: the second waits for the
    // first instead of running yt-dlp twice.
    val gate = synchronized(inspectGates) { inspectGates.getOrPut(key) { Any() } }
    synchronized(gate) {
        cached()?.let { return it }
        val fresh = kotlinx.coroutines.runBlocking {
            // Same path as the Add dialog: HLS first, yt-dlp with the browser's headers, then
            // without them (Facebook serves a page yt-dlp cannot read when the login cookie is sent).
            com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository.getInstance()
                .inspectMediaQualities(url, requestHeaders)
        }
        if (fresh.any { !it.formatId.isNullOrBlank() }) synchronized(inspectCache) {
            inspectCache[key] = System.currentTimeMillis() to fresh
        }
        return fresh
    }
}

/** Only http(s) and magnet links may enter the queue or be handed to yt-dlp/ffmpeg. */
private fun isAllowedUrl(url: String): Boolean {
    val u = url.trim()
    return !(u.length > 8192 || u.any { it < ' ' }) && (u.startsWith("http://", true) || u.startsWith(
        "https://",
        true
    ) || u.startsWith("magnet:", true))
}

/**
 * Accept only requests that really come from this machine's browser extension:
 * the Host header must be loopback (blocks DNS-rebinding) and, if an Origin is sent,
 * it must be a browser-extension origin (blocks any web page from calling the bridge).
 */
private fun isTrustedRequest(exchange: HttpExchange): Boolean {
    val rawHost = exchange.requestHeaders.getFirst("Host")?.lowercase()
    val host = when {
        rawHost == null -> null
        rawHost.startsWith("[") -> rawHost.substringBefore("]") + "]"
        else -> rawHost.substringBefore(':')
    }
    if (host != null && host !in setOf("localhost", "127.0.0.1", "[::1]")) return false
    val origin = exchange.requestHeaders.getFirst("Origin") ?: return true
    return origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://") ||
            origin.startsWith("extension://")
}

private const val BRIDGE_ALLOWED_HEADERS =
    "Content-Type, X-FetchJet, X-FetchJet-Cookie, X-FetchJet-Referer, X-FetchJet-Token, X-FetchJet-Pairing"

/**
 * The app's token ([BridgePairing]), or an old extension the user allowed. Otherwise 401: an
 * extension from before 1.2.0 (it does not send `X-FetchJet-Pairing`) makes the app ask once.
 */
private fun HttpExchange.isAuthorized(onShowAndFocus: () -> Unit): Boolean {
    if (BridgePairing.isPaired(requestHeaders.getFirst(BridgePairing.TOKEN_HEADER))) return true
    val origin = requestHeaders.getFirst("Origin")
    if (BridgePairing.isLegacyAllowed(origin)) return true
    if (origin != null && BridgePairing.isExtensionOrigin(origin) &&
        !BridgePairing.isBlocked(origin) &&
        requestHeaders.getFirst("X-FetchJet-Pairing").isNullOrBlank()
    ) {
        Thread(
            { kotlinx.coroutines.runBlocking { BridgePairing.ask(origin, legacy = true) } },
            "bridge-pair"
        )
            .apply { isDaemon = true }.start()
        SwingUtilities.invokeLater { onShowAndFocus() }
    }
    reply(401, "pairing required")
    return false
}

private fun HttpExchange.reply(
    status: Int,
    body: String,
    contentType: String = "text/plain; charset=UTF-8"
) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.add("Content-Type", contentType)
    requestHeaders.getFirst("Origin")?.let { origin ->
        if (isTrustedRequest(this)) responseHeaders.add("Access-Control-Allow-Origin", origin)
    }
    if (status == 204) {
        sendResponseHeaders(204, -1)
        close()
        return
    }
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun jsonEscape(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
    }
}

/** Writes every uncaught exception and all stdout/stderr to ~/.fetch_jet/logs/app.log. */
private fun installCrashLog() {
    try {
        val logDir = File(System.getProperty("user.home"), ".fetch_jet/logs")
        logDir.mkdirs()
        val logFile = File(logDir, "app.log")
        if (logFile.length() > 2L * 1024 * 1024) logFile.delete()
        // Cookies, logins and link tokens never reach the file.
        val out = PrintStream(
            com.michelle.raouf.fetch_jet.core.log.RedactingOutputStream(
                FileOutputStream(
                    logFile,
                    true
                )
            ),
            true, "UTF-8"
        )
        System.setOut(out)
        System.setErr(out)
        println("---- FetchJet started ${java.time.LocalDateTime.now()} (java ${System.getProperty("java.version")}) ----")
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            println("Uncaught exception in thread ${t.name}:")
            e.printStackTrace(out)
        }
    } catch (_: Exception) {
    }
}

fun startLocalHttpServer(onShowAndFocus: () -> Unit) {
    try {
        val server =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), BRIDGE_PORT), 0)

        server.createContext("/ping") { exchange ->
            if (!isTrustedRequest(exchange)) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, OPTIONS")
                exchange.reply(204, "")
                return@createContext
            }
            exchange.reply(200, "PONG")
        }

        server.createContext("/inspect") { exchange ->
            if (!isTrustedRequest(exchange)) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, OPTIONS")
                exchange.responseHeaders.add(
                    "Access-Control-Allow-Headers",
                    BRIDGE_ALLOWED_HEADERS
                )
                exchange.reply(204, "")
                return@createContext
            }
            // A plain <img>/<iframe>/navigation from a web page sends no Origin and cannot add
            // this header; without it nothing may make the app run yt-dlp.
            if (exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                exchange.reply(403, "missing X-FetchJet header"); return@createContext
            }
            if (!exchange.isAuthorized(onShowAndFocus)) return@createContext
            try {
                val query = exchange.requestURI.query ?: ""
                var targetUrl = ""
                query.split("&").forEach { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0] == "url") {
                        targetUrl = try {
                            URLDecoder.decode(parts[1], "UTF-8")
                        } catch (_: Exception) {
                            parts[1]
                        }
                    }
                }

                if (targetUrl.isBlank() || !isAllowedUrl(targetUrl)) {
                    exchange.reply(
                        400,
                        """{"error":"Missing or unsupported url parameter"}""",
                        "application/json; charset=UTF-8"
                    )
                    return@createContext
                }

                // The extension sends the page's cookies / referer so logged-in sites (Udemy...)
                // answer yt-dlp like they answer the browser.
                fun hdr(name: String) = exchange.requestHeaders.getFirst(name)?.trim().orEmpty()
                    .take(16 * 1024).filter { it != '\r' && it != '\n' }

                val headers = buildMap {
                    hdr("X-FetchJet-Cookie").takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                    hdr("X-FetchJet-Referer").takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                }
                com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.remember(
                    targetUrl, headers
                )
                val qualities = inspectCached(targetUrl, headers)

                fun escape(s: String): String = jsonEscape(s)

                val json = qualities.map { q ->
                    """{"index":${q.index},"quality":"${escape(q.qualityLabel)}","format":"${
                        escape(
                            q.format
                        )
                    }","size":${q.size},"directUrl":"${escape(q.directUrl)}","title":"${escape(q.videoTitle ?: "")}","formatId":"${
                        escape(
                            q.formatId ?: ""
                        )
                    }","duration":${q.durationSeconds}}"""
                }.joinToString(",", "[", "]")

                exchange.reply(200, json, "application/json; charset=UTF-8")
            } catch (e: Exception) {
                exchange.reply(
                    500,
                    """{"error":"${jsonEscape(e.message ?: "Failed to inspect")}"}""",
                    "application/json; charset=UTF-8"
                )
            }
        }

        /** Extension handshake: what this app can do, so the extension adapts its UI. */
        server.createContext("/config") { exchange ->
            if (!isTrustedRequest(exchange)) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, OPTIONS")
                exchange.responseHeaders.add(
                    "Access-Control-Allow-Headers",
                    BRIDGE_ALLOWED_HEADERS
                )
                exchange.reply(204, "")
                return@createContext
            }
            val features = com.michelle.raouf.fetch_jet.core.edition.AppFeatures
            val json =
                """{"app":"FetchJet","version":"${jsonEscape(System.getProperty("jpackage.app-version") ?: "dev")}","bridge":2,""" +
                        """"mediaExtractor":${features.mediaExtractor},"torrent":${features.torrent},""" +
                        """"language":"${jsonEscape(com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.current.value)}",""" +
                        """"pairing":true,"pairedMerge":true,"coursePlaylist":true,"paired":${
                            BridgePairing.isPaired(
                                exchange.requestHeaders.getFirst(
                                    BridgePairing.TOKEN_HEADER
                                )
                            )
                        },""" +
                        """"endpoints":["/ping","/config","/pair","/add","/links","/download","/inspect"]}"""
            exchange.reply(200, json, "application/json; charset=UTF-8")
        }

        /**
         * POST /pair from the FetchJet extension: the app asks the user ("Allow this browser
         * extension?") and answers {"token": ...}; the extension sends it with every request.
         * The request waits for the answer (up to 2 minutes).
         */
        server.createContext("/pair") { exchange ->
            if (!isTrustedRequest(exchange)) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
                exchange.responseHeaders.add("Access-Control-Allow-Headers", BRIDGE_ALLOWED_HEADERS)
                exchange.reply(204, "")
                return@createContext
            }
            val origin = exchange.requestHeaders.getFirst("Origin")
            if (!exchange.requestMethod.equals("POST", ignoreCase = true) ||
                exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank() ||
                origin == null || !BridgePairing.isExtensionOrigin(origin)
            ) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            SwingUtilities.invokeLater { onShowAndFocus() }
            val allowed =
                kotlinx.coroutines.runBlocking { BridgePairing.ask(origin, legacy = false) }
            if (allowed) exchange.reply(
                200, """{"token":"${BridgePairing.token}"}""", "application/json; charset=UTF-8"
            ) else exchange.reply(403, "refused")
        }

        /**
         * POST /add  {url, cookie?, referer?, userAgent?, fileName?}
         * POST /links [ {url, ...}, ... ]   (queued directly, no dialog)
         * The extension sets `X-FetchJet: 1`; a plain web page cannot add that header
         * without a CORS preflight, which only extension origins pass.
         */
        fun HttpExchange.readJsonBody(): Any? {
            val length = requestHeaders.getFirst("Content-Length")?.toLongOrNull() ?: 0L
            if (length > 4L * 1024 * 1024) return null
            val text = requestBody.use { it.readBytes() }.toString(Charsets.UTF_8)
            if (text.isBlank()) return null
            return try {
                MiniJson.parse(text)
            } catch (_: Exception) {
                null
            }
        }

        fun Map<String, Any?>.toRequest(): ExternalDownloadRequest? {
            val url = getString("url")?.trim().orEmpty()
            if (url.isBlank() || !isAllowedUrl(url)) return null
            fun hdr(key: String) = getString(key)?.trim().orEmpty().take(16 * 1024)
                .filter { it != '\r' && it != '\n' }

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
                openClip = (this["openClip"] as? Boolean) == true,
                audioUrl = getString("audioUrl")?.trim().orEmpty(),
                playlist = com.michelle.raouf.fetch_jet.feature.media_extractor.parseExternalPlaylist(
                    this["playlist"]
                ),
                reading = (this["reading"] as? Map<*, *>)?.let { r ->
                    val done = (r["done"] as? Number)?.toInt() ?: return@let null
                    val total = (r["total"] as? Number)?.toInt() ?: return@let null
                    done.coerceAtLeast(0) to total.coerceAtLeast(0)
                },
                readingFailed = getString("readingFailed")?.trim().orEmpty().take(300),
                group = getString("group")?.trim().orEmpty().take(150)
            )
        }

        fun HttpExchange.handlePreflightOrReject(): Boolean {
            if (!isTrustedRequest(this)) {
                reply(403, "forbidden"); return true
            }
            if (requestMethod.equals("OPTIONS", ignoreCase = true)) {
                responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
                responseHeaders.add(
                    "Access-Control-Allow-Headers",
                    BRIDGE_ALLOWED_HEADERS
                )
                reply(204, "")
                return true
            }
            if (!requestMethod.equals("POST", ignoreCase = true)) {
                reply(405, "POST only"); return true
            }
            if (requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                reply(403, "missing X-FetchJet header"); return true
            }
            return !isAuthorized(onShowAndFocus)
        }

        server.createContext("/add") { exchange ->
            if (exchange.handlePreflightOrReject()) return@createContext
            @Suppress("UNCHECKED_CAST")
            val obj = exchange.readJsonBody() as? Map<String, Any?>
            val request = obj?.toRequest()
            if (request == null) {
                exchange.reply(
                    400,
                    """{"error":"missing or unsupported url"}""",
                    "application/json; charset=UTF-8"
                )
                return@createContext
            }
            if (!obj.getString("formatId").isNullOrBlank() && request.mediaQualities.isEmpty()) {
                exchange.reply(
                    409,
                    """{"error":"quality not available any more, inspect again"}""",
                    "application/json; charset=UTF-8"
                )
                return@createContext
            }
            val dedupeKey =
                request.url + "|" + (obj.getString("formatId")
                    ?: "") + "|" + request.openClip + "|" + request.reading + "|" + request.readingFailed + "|" + (request.playlist?.entries?.size
                    ?: 0)
            val now = System.currentTimeMillis()
            val repeat = synchronized(recentAdds) {
                recentAdds.entries.removeAll { now - it.value > 5_000 }
                if (recentAdds.containsKey(dedupeKey)) true else {
                    recentAdds[dedupeKey] = now; false
                }
            }
            if (!repeat) ExternalDownloadBridge.dispatch(request)
            SwingUtilities.invokeLater { onShowAndFocus() }
            exchange.reply(200, """{"ok":true}""", "application/json; charset=UTF-8")
        }

        server.createContext("/links") { exchange ->
            if (exchange.handlePreflightOrReject()) return@createContext
            @Suppress("UNCHECKED_CAST")
            val items: List<Any?> = when (val body = exchange.readJsonBody()) {
                is List<*> -> body
                is Map<*, *> -> (body as Map<String, Any?>)["links"] as? List<Any?> ?: emptyList()
                else -> emptyList()
            }

            @Suppress("UNCHECKED_CAST")
            val requests = items.mapNotNull { (it as? Map<String, Any?>)?.toRequest() }
                .distinctBy { it.url }
                .take(500)
            if (requests.isEmpty()) {
                exchange.reply(
                    400,
                    """{"error":"no usable links"}""",
                    "application/json; charset=UTF-8"
                )
                return@createContext
            }
            ExternalDownloadBridge.dispatchBatch(requests)
            SwingUtilities.invokeLater { onShowAndFocus() }
            exchange.reply(
                200,
                """{"ok":true,"accepted":${requests.size}}""",
                "application/json; charset=UTF-8"
            )
        }

        server.createContext("/show") { exchange ->
            if (!isTrustedRequest(exchange)) { exchange.reply(403, "forbidden"); return@createContext }
            if (exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) { exchange.reply(403, "missing X-FetchJet header"); return@createContext }
            if (!exchange.isAuthorized(onShowAndFocus)) return@createContext
            SwingUtilities.invokeLater { onShowAndFocus() }
            exchange.reply(200, "OK")
        }

        server.createContext("/download") { exchange ->
            if (!isTrustedRequest(exchange)) {
                exchange.reply(403, "forbidden"); return@createContext
            }
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, OPTIONS")
                exchange.responseHeaders.add(
                    "Access-Control-Allow-Headers",
                    BRIDGE_ALLOWED_HEADERS
                )
                exchange.reply(204, "")
                return@createContext
            }
            if (exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                exchange.reply(403, "missing X-FetchJet header"); return@createContext
            }
            if (!exchange.isAuthorized(onShowAndFocus)) return@createContext
            val query = exchange.requestURI.query ?: ""
            var url = ""
            var cookie = ""
            query.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (_: Exception) {
                        parts[1]
                    }
                    if (key == "url") url = value
                    if (key == "cookie") cookie = value
                }
            }

            if (url.isBlank() || !isAllowedUrl(url)) {
                exchange.reply(400, "unsupported url")
                return@createContext
            }
            ExternalDownloadBridge.dispatch(url, cookie)
            // Compose/Swing state must be touched on the UI thread, not the HTTP thread.
            SwingUtilities.invokeLater { onShowAndFocus() }
            exchange.reply(200, "OK")
        }
        // One request at a time would make /ping stall while /inspect runs yt-dlp (up to 25 s)
        // and a second app instance would then start. Use a pool.
        server.executor = Executors.newCachedThreadPool { r ->
            Thread(r, "fetchjet-bridge").apply {
                isDaemon = true
            }
        }
        server.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun main(rawArgs: Array<String>) {
    installCrashLog()
    LocalizationManager.start()
    // Baked in at build time (-Dfetchjet.verboseLogs) or switched on for one run with the
    // environment variable, e.g. to collect a bug report from a release build.
    com.michelle.raouf.fetch_jet.core.log.AppLog.verbose =
        System.getenv("FETCHJET_VERBOSE_LOGS")?.trim()?.equals("true", ignoreCase = true)
            ?: System.getProperty("fetchjet.verboseLogs")?.trim()?.equals("true", ignoreCase = true)
                    ?: false
    println("Verbose logs: ${com.michelle.raouf.fetch_jet.core.log.AppLog.verbose}")
    // A run from Gradle / Android Studio is a plain `java` process: macOS put the Java icon in
    // the Dock. The installed app has its own icon (launcher.icns); this one is for those runs.
    try {
        if (java.awt.Taskbar.isTaskbarSupported() &&
            java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)
        ) {
            Thread.currentThread().contextClassLoader.getResource("launcher.png")?.let {
                java.awt.Taskbar.getTaskbar().iconImage = javax.imageio.ImageIO.read(it)
            }
        }
    } catch (_: Throwable) {
    }
    // First run on Windows: tell Edge about the FetchJet extension (like IDM); Edge then
    // asks the user to turn it on.
    Thread(
        { com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration.setUpOnFirstRun() },
        "browser-integration"
    )
        .apply { isDaemon = true }.start()
    // Started by the OS at login (see StartupLauncher): remember it, and never treat the flag
    // as a link to download.
    val startedAtLogin =
        rawArgs.any { it.trim() == com.michelle.raouf.fetch_jet.core.startup.AUTOSTART_ARG } ||
                // Microsoft Store build: Windows' startup task starts it with no arguments.
                (rawArgs.isEmpty() &&
                        com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage.looksLikeLoginStart())
    com.michelle.raouf.fetch_jet.core.startup.StartupPreferences.launchedAtLogin = startedAtLogin
    val args =
        rawArgs.filter { it.trim() != com.michelle.raouf.fetch_jet.core.startup.AUTOSTART_ARG }
    val rawArg = args.firstOrNull()?.trim()
    if (!rawArg.isNullOrBlank()) {
        val targetUrl = parseUrlFromArg(rawArg)
        if (targetUrl.isNotBlank()) {
            if (trySendToRunningInstance(targetUrl)) {
                return
            }
            pendingStartupUrl = targetUrl
        }
    }
    // Single instance: a second plain launch (no link) just brings the running app forward.
    if (pendingStartupUrl.isNullOrBlank() && isAnotherInstanceRunning()) {
        return
    }

    // At login, start in the tray when the user asked for it (and a tray exists to come back from).
    val startInTray = startedAtLogin &&
            com.michelle.raouf.fetch_jet.core.startup.StartupPreferences.startHidden &&
            (try {
                java.awt.SystemTray.isSupported()
            } catch (_: Throwable) {
                false
            })

    application {
        var isWindowVisible by remember { mutableStateOf(!startInTray) }
        var bringToFrontTrigger by remember { mutableStateOf(0) }
        val trayState = rememberTrayState()
        val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
        val appIcon = painterResource(Res.drawable.ic_fetchjet_logo)

        val triggerFocus: () -> Unit = {
            isWindowVisible = true
            windowState.isMinimized = false
            bringToFrontTrigger++
        }

        remember {
            ExternalDownloadBridge.onShowAndFocus =
                { SwingUtilities.invokeLater { triggerFocus() } }
            // (The bridge server calls onShowAndFocus itself; the ViewModel owns onExternalRequest.)
            ExternalDownloadBridge.openInBrowserFallback = { url ->
                try {
                    println("Desktop: opening browser fallback URL: $url")
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop()
                            .isSupported(java.awt.Desktop.Action.BROWSE)
                    ) {
                        java.awt.Desktop.getDesktop().browse(URI(url))
                        println("Desktop: browser browse invoked successfully.")
                    } else {
                        println("Desktop: Desktop BROWSE not supported on this platform.")
                    }
                } catch (e: Exception) {
                    println("Desktop: Error browsing fallback URL: ${e.message}")
                    e.printStackTrace()
                }
            }
            startLocalHttpServer(triggerFocus)
            com.michelle.raouf.fetch_jet.core.power.WhenDone.onExitApp =
                { SwingUtilities.invokeLater { exitApplication() } }
        }

        val activeTasksMap by DownloadNotificationManager.activeTasks.collectAsState()
        val language by com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.current.collectAsState()
        val whenDone by com.michelle.raouf.fetch_jet.core.power.WhenDone.action.collectAsState()

        val trayTooltip = remember(activeTasksMap, language) {
            if (activeTasksMap.isEmpty()) {
                LocalizationManager.Strings.desktopTrayIdleTooltip
            } else {
                val lines = activeTasksMap.values.take(6).map { task ->
                    val cleanName =
                        if (task.fileName.length > 25) task.fileName.take(22) + "..." else task.fileName
                    "$cleanName: ${task.progressPercent}%"
                }
                val extra =
                    if (activeTasksMap.size > 6) "\n" + LocalizationManager.Strings.desktopTrayMore(
                        activeTasksMap.size - 6
                    ) else ""
                LocalizationManager.Strings.desktopTrayDownloadsHeader + "\n" + lines.joinToString("\n") + extra
            }
        }

        Tray(
            state = trayState,
            icon = appIcon,
            tooltip = trayTooltip,
            onAction = {
                if (isWindowVisible && !windowState.isMinimized) {
                    isWindowVisible = false
                } else {
                    triggerFocus()
                }
            },
            menu = {
                key(language) {
                    Item(LocalizationManager.Strings.desktopTrayShowApp, onClick = {
                        triggerFocus()
                    })
                    Menu(LocalizationManager.Strings.whenDoneGroup) {
                        com.michelle.raouf.fetch_jet.core.power.WhenDoneAction.entries.forEach { choice ->
                            CheckboxItem(
                                com.michelle.raouf.fetch_jet.ui.whenDoneLabel(choice),
                                checked = whenDone == choice,
                                onCheckedChange = {
                                    com.michelle.raouf.fetch_jet.core.power.WhenDone.choose(choice)
                                }
                            )
                        }
                    }
                    Separator()
                    Item(LocalizationManager.Strings.desktopTrayCloseApp, onClick = {
                        exitApplication()
                    })
                }
            }
        )

        // Without a system tray (GNOME/Wayland by default) "hide to tray" would leave an
        // invisible process with no way back: quit instead.
        val traySupported = remember { try { java.awt.SystemTray.isSupported() } catch (_: Throwable) { false } }
        Window(
            visible = isWindowVisible,
            onCloseRequest = {
                if (traySupported) isWindowVisible = false else exitApplication()
            },
            title = "FetchJet - ${LocalizationManager.Strings.desktopWindowTagline}",
            state = windowState,
            icon = appIcon
        ) {
            LaunchedEffect(bringToFrontTrigger) {
                if (bringToFrontTrigger > 0) {
                    try {
                        isWindowVisible = true
                        window.state = Frame.NORMAL
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                        // Windows refuses to let a background process take the foreground
                        // (SetForegroundWindow lock). A synthetic Alt tap counts as user input
                        // and lifts the lock; always-on-top for a moment does the rest.
                        try {
                            val robot = java.awt.Robot()
                            robot.keyPress(java.awt.event.KeyEvent.VK_ALT)
                            robot.keyRelease(java.awt.event.KeyEvent.VK_ALT)
                        } catch (_: Throwable) {
                        }
                        window.isAlwaysOnTop = true
                        window.toFront()
                        window.requestFocus()
                        delay(150)
                        window.isAlwaysOnTop = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            LaunchedEffect(Unit) {
                pendingStartupUrl?.let { url ->
                    delay(600)
                    ExternalDownloadBridge.dispatch(url, "")
                    triggerFocus()
                    pendingStartupUrl = null
                }
            }

            val baseClipboard = LocalClipboard.current
            val safeClipboard = remember(baseClipboard) {
                object : Clipboard {
                    override suspend fun getClipEntry(): ClipEntry? =
                        try {
                            baseClipboard.getClipEntry()
                        } catch (_: Throwable) {
                            null
                        }

                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                        try {
                            baseClipboard.setClipEntry(clipEntry)
                        } catch (_: Throwable) {
                        }
                    }

                    override val nativeClipboard: NativeClipboard
                        get() = baseClipboard.nativeClipboard
                }
            }
            CompositionLocalProvider(LocalClipboard provides safeClipboard) {
                App()
            }
        }
    }
}