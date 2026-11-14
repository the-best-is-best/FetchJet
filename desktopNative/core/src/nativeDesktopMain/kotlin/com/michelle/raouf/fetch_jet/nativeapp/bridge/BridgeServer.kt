package com.michelle.raouf.fetch_jet.nativeapp.bridge

import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.core.network.SiteCookieJar
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaUrls
import com.michelle.raouf.fetch_jet.feature.media_extractor.parseExternalPlaylist
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.Launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val BRIDGE_PORT = 28450

private const val JSON = "application/json; charset=UTF-8"
private const val ALLOWED_HEADERS =
    "Content-Type, X-FetchJet, X-FetchJet-Cookie, X-FetchJet-Referer, X-FetchJet-Token, X-FetchJet-Pairing, X-FetchJet-Ext"
private const val MAX_BODY = 4 * 1024 * 1024
private val KNOWN_PATHS = listOf("/ping", "/inspect", "/config", "/pair", "/add", "/links", "/show", "/download")

internal fun jsonEscape(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
        }
    }
}

internal fun isAllowedUrl(url: String): Boolean {
    val u = url.trim()
    return !(u.length > 8192 || u.any { it < ' ' }) &&
        (u.startsWith("http://", true) || u.startsWith("https://", true) || u.startsWith("magnet:", true) ||
            com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(u))
}

internal class BridgeServer(
    private val scope: CoroutineScope,
    private val port: Int,
    private val showAndFocus: () -> Unit,
    private val isDark: () -> Boolean = { true },
) {
    private val cacheLock = Mutex()
    private val inspectCache = LinkedHashMap<String, Pair<Long, List<MediaQualityEntry>>>()
    private val inspectGates = HashMap<String, Mutex>()
    private val addsLock = Mutex()
    private val recentAdds = HashMap<String, Long>()
    private var listener: HttpListener? = null

    suspend fun start(): Boolean {
        val created = HttpListener(scope, "127.0.0.1", port, MAX_BODY) { request -> handle(request) }
        val ok = created.start()
        if (ok) listener = created
        println(if (ok) "bridge: listening on 127.0.0.1:$port" else "bridge: not started")
        return ok
    }

    fun stop() {
        listener?.stop()
        listener = null
    }

    private suspend fun cachedInspection(key: String): List<MediaQualityEntry>? = cacheLock.withLock {
        val entry = inspectCache[key] ?: return@withLock null
        if (currentEpochMs() - entry.first < 30 * 60 * 1000L && entry.second.isNotEmpty()) {
            inspectCache.remove(key)
            inspectCache[key] = entry
            entry.second
        } else null
    }

    private suspend fun inspectCached(url: String, headers: Map<String, String> = emptyMap()): List<MediaQualityEntry> {
        val key = MediaUrls.canonical(url)
        cachedInspection(key)?.let { return it }
        val gate = cacheLock.withLock { inspectGates.getOrPut(key) { Mutex() } }
        return gate.withLock {
            cachedInspection(key) ?: run {
                val fresh = QueueManagerRepository.getInstance().inspectMediaQualities(url, headers)
                if (fresh.any { !it.formatId.isNullOrBlank() }) cacheLock.withLock {
                    inspectCache.remove(key)
                    inspectCache[key] = currentEpochMs() to fresh
                    while (inspectCache.size > 40) inspectCache.remove(inspectCache.keys.first())
                }
                fresh
            }
        }
    }

    private fun isTrusted(request: HttpRequest): Boolean {
        val rawHost = request.header("Host")?.lowercase()
        val host = when {
            rawHost == null -> null
            rawHost.startsWith("[") -> rawHost.substringBefore("]") + "]"
            else -> rawHost.substringBefore(':')
        }
        if (host != null && host !in setOf("localhost", "127.0.0.1", "[::1]")) return false
        val origin = request.header("Origin") ?: return true
        return origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://") ||
            origin.startsWith("extension://")
    }

    private fun reply(
        request: HttpRequest,
        status: Int,
        body: String,
        contentType: String = "text/plain; charset=UTF-8",
        extra: List<Pair<String, String>> = emptyList(),
    ): HttpReply {
        val headers = ArrayList<Pair<String, String>>(extra)
        val origin = request.header("Origin")
        if (origin != null && isTrusted(request)) headers.add("Access-Control-Allow-Origin" to origin)
        return HttpReply(status, body, contentType, headers)
    }

    private fun preflight(request: HttpRequest, methods: String, withHeaders: Boolean = true): HttpReply = reply(
        request,
        204,
        "",
        extra = buildList {
            add("Access-Control-Allow-Methods" to methods)
            if (withHeaders) add("Access-Control-Allow-Headers" to ALLOWED_HEADERS)
        },
    )

    private fun noteBrowser(request: HttpRequest) {
        BridgePairing.noteBrowser(request.header("User-Agent"), currentEpochMs(), request.header("X-FetchJet-Ext"))
    }

    private fun refusal(request: HttpRequest): HttpReply? {
        if (BridgePairing.isPaired(request.header(BridgePairing.TOKEN_HEADER))) {
            noteBrowser(request)
            return null
        }
        val origin = request.header("Origin")
        if (BridgePairing.isLegacyAllowed(origin)) return null
        if (origin != null && BridgePairing.isExtensionOrigin(origin) && !BridgePairing.isBlocked(origin) &&
            request.header("X-FetchJet-Pairing").isNullOrBlank()
        ) {
            scope.launch { BridgePairing.ask(origin, legacy = true) }
            showAndFocus()
        }
        return reply(request, 401, "pairing required")
    }

    private fun parseBody(request: HttpRequest): Any? {
        val bytes = request.body ?: return null
        val text = bytes.decodeToString()
        if (text.isBlank()) return null
        return try {
            MiniJson.parse(text)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun toRequest(item: Map<String, Any?>): ExternalDownloadRequest? {
        val url = item.getString("url")?.trim().orEmpty()
        if (url.isBlank() || !isAllowedUrl(url)) return null
        fun header(key: String) = item.getString(key)?.trim().orEmpty().take(16 * 1024).filter { it != '\r' && it != '\n' }
        val formatId = item.getString("formatId")?.trim().orEmpty()
        val qualities = if (formatId.isNotBlank()) {
            val list = try {
                inspectCached(url, buildMap {
                    header("cookie").takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                    header("referer").takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
            val match = list.firstOrNull { it.formatId == formatId }
            if (match == null) emptyList() else list.map { it.copy(isSelected = it === match) }
        } else emptyList()
        return ExternalDownloadRequest(
            url = url,
            cookie = header("cookie"),
            referer = header("referer"),
            userAgent = header("userAgent"),
            fileName = item.getString("fileName")?.trim().orEmpty().take(255),
            mediaQualities = qualities,
            direct = (item["direct"] as? Boolean) == true,
            openClip = (item["openClip"] as? Boolean) == true,
            audioUrl = item.getString("audioUrl")?.trim().orEmpty(),
            playlist = parseExternalPlaylist(item["playlist"]),
            reading = (item["reading"] as? Map<*, *>)?.let { r ->
                val done = (r["done"] as? Number)?.toInt() ?: return@let null
                val total = (r["total"] as? Number)?.toInt() ?: return@let null
                done.coerceAtLeast(0) to total.coerceAtLeast(0)
            },
            readingFailed = item.getString("readingFailed")?.trim().orEmpty().take(300),
            group = item.getString("group")?.trim().orEmpty().take(150),
        )
    }

    private fun postGate(request: HttpRequest): HttpReply? {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "POST, OPTIONS")
        if (request.method != "POST") return reply(request, 405, "POST only")
        if (request.header("X-FetchJet").isNullOrBlank()) return reply(request, 403, "missing X-FetchJet header")
        return refusal(request)
    }

    private suspend fun handle(request: HttpRequest): HttpReply {
        val route = KNOWN_PATHS.filter { request.path.startsWith(it) }.maxByOrNull { it.length }
            ?: return HttpReply(404, "<h1>404 Not Found</h1>No context found for request", "text/html; charset=UTF-8")
        return when (route) {
            "/ping" -> ping(request)
            "/inspect" -> inspect(request)
            "/config" -> config(request)
            "/pair" -> pair(request)
            "/add" -> add(request)
            "/links" -> links(request)
            "/show" -> show(request)
            else -> download(request)
        }
    }

    private fun ping(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "GET, OPTIONS", withHeaders = false)
        return reply(request, 200, "PONG")
    }

    private suspend fun inspect(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "GET, OPTIONS")
        if (request.header("X-FetchJet").isNullOrBlank()) return reply(request, 403, "missing X-FetchJet header")
        refusal(request)?.let { return it }
        return try {
            val target = request.queryValues()["url"].orEmpty()
            if (target.isBlank() || !isAllowedUrl(target)) {
                return reply(request, 400, """{"error":"Missing or unsupported url parameter"}""", JSON)
            }
            fun header(name: String) = request.header(name)?.trim().orEmpty().take(16 * 1024).filter { it != '\r' && it != '\n' }
            val headers = buildMap {
                header("X-FetchJet-Cookie").takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                header("X-FetchJet-Referer").takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            }
            SiteCookieJar.remember(target, headers)
            val qualities = inspectCached(target, headers)
            val json = qualities.joinToString(",", "[", "]") { q ->
                """{"index":${q.index},"quality":"${jsonEscape(q.qualityLabel)}","format":"${jsonEscape(q.format)}",""" +
                    """"size":${q.size},"directUrl":"${jsonEscape(q.directUrl)}","title":"${jsonEscape(q.videoTitle ?: "")}",""" +
                    """"formatId":"${jsonEscape(q.formatId ?: "")}","duration":${q.durationSeconds}}"""
            }
            reply(request, 200, json, JSON)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            reply(request, 500, """{"error":"${jsonEscape(e.message ?: "Failed to inspect")}"}""", JSON)
        }
    }

    private fun config(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "GET, OPTIONS")
        val paired = BridgePairing.isPaired(request.header(BridgePairing.TOKEN_HEADER))
        if (paired || BridgePairing.isExtensionOrigin(request.header("Origin")) || !request.header("X-FetchJet-Ext").isNullOrBlank()) {
            noteBrowser(request)
        }
        val json = """{"app":"FetchJet","version":"${jsonEscape(APP_VERSION)}","bridge":2,""" +
            """"mediaExtractor":${AppFeatures.mediaExtractor},"torrent":${AppFeatures.torrent},""" +
            """"language":"${jsonEscape(AppLanguage.current.value)}","theme":"${if (isDark()) "dark" else "light"}",""" +
            """"pairing":true,"pairedMerge":true,"coursePlaylist":true,"paired":$paired,""" +
            """"endpoints":["/ping","/config","/pair","/add","/links","/download","/inspect"]}"""
        return reply(request, 200, json, JSON)
    }

    private suspend fun pair(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "POST, OPTIONS")
        val origin = request.header("Origin")
        if (request.method != "POST" || request.header("X-FetchJet").isNullOrBlank() ||
            origin == null || !BridgePairing.isExtensionOrigin(origin)
        ) return reply(request, 403, "forbidden")
        showAndFocus()
        val allowed = BridgePairing.ask(origin, legacy = false)
        if (!allowed) return reply(request, 403, "refused")
        noteBrowser(request)
        return reply(request, 200, """{"token":"${BridgePairing.token}"}""", JSON)
    }

    private suspend fun add(request: HttpRequest): HttpReply {
        postGate(request)?.let { return it }
        @Suppress("UNCHECKED_CAST")
        val item = parseBody(request) as? Map<String, Any?>
        val wanted = item?.let { toRequest(it) }
            ?: return reply(request, 400, """{"error":"missing or unsupported url"}""", JSON)
        if (!item.getString("formatId").isNullOrBlank() && wanted.mediaQualities.isEmpty()) {
            return reply(request, 409, """{"error":"quality not available any more, inspect again"}""", JSON)
        }
        val key = wanted.url + "|" + (item.getString("formatId") ?: "") + "|" + wanted.openClip + "|" + wanted.reading + "|" +
            wanted.readingFailed + "|" + (wanted.playlist?.entries?.size ?: 0)
        val now = currentEpochMs()
        val repeat = addsLock.withLock {
            recentAdds.entries.removeAll { now - it.value > 5_000 }
            if (recentAdds.containsKey(key)) true else {
                recentAdds[key] = now
                false
            }
        }
        if (!repeat) ExternalDownloadBridge.dispatch(wanted)
        showAndFocus()
        return reply(request, 200, """{"ok":true}""", JSON)
    }

    private suspend fun links(request: HttpRequest): HttpReply {
        postGate(request)?.let { return it }
        @Suppress("UNCHECKED_CAST")
        val items: List<Any?> = when (val body = parseBody(request)) {
            is List<*> -> body
            is Map<*, *> -> (body as Map<String, Any?>)["links"] as? List<Any?> ?: emptyList()
            else -> emptyList()
        }
        val requests = ArrayList<ExternalDownloadRequest>()
        for (entry in items) {
            @Suppress("UNCHECKED_CAST")
            val wanted = (entry as? Map<String, Any?>)?.let { toRequest(it) } ?: continue
            requests.add(wanted)
        }
        val distinct = requests.distinctBy { it.url }.take(500)
        if (distinct.isEmpty()) return reply(request, 400, """{"error":"no usable links"}""", JSON)
        ExternalDownloadBridge.dispatchBatch(distinct)
        showAndFocus()
        return reply(request, 200, """{"ok":true,"accepted":${distinct.size}}""", JSON)
    }

    private fun show(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.header("X-FetchJet").isNullOrBlank()) return reply(request, 403, "missing X-FetchJet header")
        refusal(request)?.let { return it }
        showAndFocus()
        return reply(request, 200, "OK")
    }

    private fun download(request: HttpRequest): HttpReply {
        if (!isTrusted(request)) return reply(request, 403, "forbidden")
        if (request.method == "OPTIONS") return preflight(request, "GET, OPTIONS")
        if (request.header("X-FetchJet").isNullOrBlank()) return reply(request, 403, "missing X-FetchJet header")
        refusal(request)?.let { return it }
        val values = request.queryValues()
        if (values["cli"] == "1") {
            if (request.header("Origin") != null) return reply(request, 403, "forbidden")
            val command = Launch.commandFromQuery(values) ?: return reply(request, 400, "unsupported url")
            scope.launch { Launch.run(command) }
            return reply(request, 200, "OK")
        }
        val url = values["url"].orEmpty()
        if (url.isBlank() || !isAllowedUrl(url)) return reply(request, 400, "unsupported url")
        ExternalDownloadBridge.dispatch(url, values["cookie"].orEmpty())
        showAndFocus()
        return reply(request, 200, "OK")
    }
}
