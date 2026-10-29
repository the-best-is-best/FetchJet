package com.michelle.raouf.fetch_jet

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getString
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
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
    val asFile = clean.removePrefix("file://").let { java.io.File(it) }
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
private val inspectCache = object :
    LinkedHashMap<String, Pair<Long, List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>>>(
        16,
        0.75f,
        true
    ) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>>>?) =
        size > 40
}

private fun inspectCached(url: String): List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> {
    synchronized(inspectCache) {
        inspectCache[url]?.let { (at, list) ->
            // Direct stream URLs expire (YouTube: ~6 h); keep results for 30 minutes.
            if (System.currentTimeMillis() - at < 30 * 60 * 1000L && list.isNotEmpty()) return list
        }
    }
    val fresh = kotlinx.coroutines.runBlocking {
        com.michelle.raouf.fetch_jet.feature.media_extractor.inspectPlatformQualities(url)
    }
    if (fresh.isNotEmpty()) synchronized(inspectCache) {
        inspectCache[url] = System.currentTimeMillis() to fresh
    }
    return fresh
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
        val out = PrintStream(FileOutputStream(logFile, true), true, "UTF-8")
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
                    "Content-Type, X-FetchJet"
                )
                exchange.reply(204, "")
                return@createContext
            }
            // A plain <img>/<iframe>/navigation from a web page sends no Origin and cannot add
            // this header; without it nothing may make the app run yt-dlp.
            if (exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                exchange.reply(403, "missing X-FetchJet header"); return@createContext
            }
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

                val qualities = inspectCached(targetUrl)

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
                    "Content-Type, X-FetchJet"
                )
                exchange.reply(204, "")
                return@createContext
            }
            val features = com.michelle.raouf.fetch_jet.core.edition.AppFeatures
            val json =
                """{"app":"FetchJet","version":"${jsonEscape(System.getProperty("jpackage.app-version") ?: "dev")}","bridge":2,""" +
                        """"mediaExtractor":${features.mediaExtractor},"torrent":${features.torrent},""" +
                        """"endpoints":["/ping","/config","/add","/links","/download","/inspect"]}"""
            exchange.reply(200, json, "application/json; charset=UTF-8")
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
                    inspectCached(url)
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
                direct = (this["direct"] as? Boolean) == true
            )
        }

        fun HttpExchange.handlePreflightOrReject(): Boolean {
            if (!isTrustedRequest(this)) {
                reply(403, "forbidden"); return true
            }
            if (requestMethod.equals("OPTIONS", ignoreCase = true)) {
                responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
                responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, X-FetchJet")
                reply(204, "")
                return true
            }
            if (!requestMethod.equals("POST", ignoreCase = true)) {
                reply(405, "POST only"); return true
            }
            if (requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                reply(403, "missing X-FetchJet header"); return true
            }
            return false
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
            ExternalDownloadBridge.dispatch(request)
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
                    "Content-Type, X-FetchJet"
                )
                exchange.reply(204, "")
                return@createContext
            }
            if (exchange.requestHeaders.getFirst("X-FetchJet").isNullOrBlank()) {
                exchange.reply(403, "missing X-FetchJet header"); return@createContext
            }
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
    // Baked in at build time (-Dfetchjet.verboseLogs) or switched on for one run with the
    // environment variable, e.g. to collect a bug report from a release build.
    com.michelle.raouf.fetch_jet.core.log.AppLog.verbose =
        System.getenv("FETCHJET_VERBOSE_LOGS")?.trim()?.equals("true", ignoreCase = true)
            ?: System.getProperty("fetchjet.verboseLogs")?.trim()?.equals("true", ignoreCase = true)
                    ?: false
    println("Verbose logs: ${com.michelle.raouf.fetch_jet.core.log.AppLog.verbose}")
    // Started by the OS at login (see StartupLauncher): remember it, and never treat the flag
    // as a link to download.
    val startedAtLogin =
        rawArgs.any { it.trim() == com.michelle.raouf.fetch_jet.core.startup.AUTOSTART_ARG }
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
        }

        val activeTasksMap by DownloadNotificationManager.activeTasks.collectAsState()

        val trayTooltip = remember(activeTasksMap) {
            if (activeTasksMap.isEmpty()) {
                "FetchJet - Running in Background"
            } else {
                val lines = activeTasksMap.values.take(6).map { task ->
                    val cleanName =
                        if (task.fileName.length > 25) task.fileName.take(22) + "..." else task.fileName
                    "$cleanName: ${task.progressPercent}%"
                }
                val extra =
                    if (activeTasksMap.size > 6) "\n+${activeTasksMap.size - 6} more" else ""
                "FetchJet Downloads:\n" + lines.joinToString("\n") + extra
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
                Item("Show App", onClick = {
                    triggerFocus()
                })
                Separator()
                Item("Close App", onClick = {
                    exitApplication()
                })
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
            title = "FetchJet - High-Velocity Multi-Engine Downloader",
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
                        // Windows OS trick to steal focus and bring window to front of browser:
                        window.isAlwaysOnTop = true
                        window.toFront()
                        window.requestFocus()
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

            App()
        }
    }
}