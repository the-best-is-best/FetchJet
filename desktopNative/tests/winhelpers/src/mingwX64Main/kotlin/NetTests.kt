@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.runtime.NativeRuntimeApi::class)

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.WinSha256
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winMemoryUse
import com.michelle.raouf.fetch_jet.platform.win.winSha256
import com.michelle.raouf.fetch_jet.platform.win.winWriteFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.winhttp.WinHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private fun privateKb(): Long = (winMemoryUse()?.privateBytes ?: 0L) / 1024

private var nativeEngine = true

private fun client(
    follow: Boolean = true,
    connectMs: Long? = 60_000,
    socketMs: Long? = 60_000,
    requestMs: Long? = null,
    verifyTls: Boolean = true,
    proxyConfig: ProxyConfig? = null,
    proxyLogin: Pair<String, String>? = null,
): HttpClient {
    val common: HttpClientConfig<*>.() -> Unit = {
        followRedirects = follow
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = requestMs
            connectTimeoutMillis = connectMs
            socketTimeoutMillis = socketMs
        }
    }
    return if (nativeEngine) {
        HttpClient(WinHttpNative) {
            common()
            engine {
                sslVerify = verifyTls
                proxy = proxyConfig
                proxyUser = proxyLogin?.first.orEmpty()
                proxyPassword = proxyLogin?.second.orEmpty()
            }
        }
    } else {
        HttpClient(WinHttp) {
            common()
            engine {
                sslVerify = verifyTls
                proxy = proxyConfig
            }
        }
    }
}

private suspend fun failure(block: suspend () -> Unit): Pair<Throwable?, Long> {
    val mark = TimeSource.Monotonic.markNow()
    return try {
        block()
        null to mark.elapsedNow().inWholeMilliseconds
    } catch (e: Throwable) {
        e to mark.elapsedNow().inWholeMilliseconds
    }
}

private fun describe(e: Throwable?): String {
    if (e == null) return "no exception"
    val chain = generateSequence(e) { it.cause }.take(4).joinToString(" <- ") { "${it::class.qualifiedName}: ${it.message?.take(90)}" }
    return chain + (if (e is CancellationException) " [CANCELLATION]" else "")
}

fun netTests(work: String, options: List<String>) = runBlocking {
    val port = options.firstOrNull { it.startsWith("server=") }?.substringAfter('=')?.toIntOrNull() ?: 18200
    val base = "http://127.0.0.1:$port"
    nativeEngine = options.none { it == "engine=ktor" }
    T.info("engine under test: " + if (nativeEngine) "WinHttpNative (FetchJet)" else "WinHttp (ktor)")
    val only = options.firstOrNull { it.startsWith("only=") }?.substringAfter('=')?.split(',')?.toSet()
    fun wanted(name: String) = only == null || name in only
    val http = client()
    val idle = privateKb()

    if (wanted("basic")) T.guarded("basic") {
        val expected = http.get("$base/sha256/1MB.bin").bodyAsText().trim()
        val response = http.get("$base/file/1MB.bin")
        val bytes = response.readRawBytes()
        T.check(
            "GET 1 MB: status, length, content",
            response.status.value == 200 && bytes.size == 1048576 && winSha256(bytes).toHexString() == expected,
            "version=${response.version} length-header=${response.headers[HttpHeaders.ContentLength]}"
        )
        val head = http.head("$base/file/64MB.bin")
        T.check(
            "HEAD: length and Accept-Ranges, no body",
            head.status.value == 200 && head.headers[HttpHeaders.ContentLength] == "67108864" &&
                head.headers[HttpHeaders.AcceptRanges] == "bytes" && head.readRawBytes().isEmpty()
        )
        val echo = http.get("$base/echo") {
            header(HttpHeaders.Range, "bytes=5-9")
            header(HttpHeaders.Cookie, "a=1; b=two")
            header(HttpHeaders.Referrer, "https://example.com/page?x=1")
            header(HttpHeaders.UserAgent, "FetchJet-Test/1.0")
            header(HttpHeaders.Authorization, "Basic c2l0ZTpwdw==")
            header("X-FetchJet", "1")
        }.bodyAsText()
        T.check(
            "custom headers arrive: Range, Cookie, Referer, User-Agent, Authorization, X-FetchJet",
            echo.contains("\"Range\": \"bytes=5-9\"") && echo.contains("\"Cookie\": \"a=1; b=two\"") &&
                echo.contains("\"Referer\": \"https://example.com/page?x=1\"") &&
                echo.contains("\"User-Agent\": \"FetchJet-Test/1.0\"") &&
                echo.contains("\"Authorization\": \"Basic c2l0ZTpwdw==\"") && echo.contains("\"X-FetchJet\": \"1\"")
        )
        T.info("headers of a plain GET: " + http.get("$base/echo").bodyAsText().substringAfter("\"headers\":").substringBefore("}").replace("\n", " ").replace(Regex(" +"), " "))
        val partial = http.get("$base/file/1MB.bin") { header(HttpHeaders.Range, "bytes=1000-1999") }
        T.check(
            "Range request: 206, Content-Range, right bytes",
            partial.status.value == 206 && partial.headers[HttpHeaders.ContentRange] == "bytes 1000-1999/1048576" &&
                partial.readRawBytes().contentEquals(bytes.copyOfRange(1000, 2000))
        )
        val chunked = http.get("$base/file/300KB.bin?chunked=1")
        T.check("chunked body without Content-Length", chunked.readRawBytes().size == 307200 && chunked.headers[HttpHeaders.ContentLength] == null)
        val noLength = http.get("$base/file/300KB.bin?nolength=1")
        T.check("body ended by connection close", noLength.readRawBytes().size == 307200)
        val arabic = http.get("$base/file/%D8%AA%D9%82%D8%B1%D9%8A%D8%B1.pdf?size=3KB&name=%D8%AA%D9%82%D8%B1%D9%8A%D8%B1%20%D9%86%D9%87%D8%A7%D8%A6%D9%8A.pdf")
        T.check("Arabic path and Content-Disposition", arabic.status.value == 200 && arabic.headers[HttpHeaders.ContentDisposition]?.contains("UTF-8''") == true)
        val unauthorized = http.get("$base/file/1KB.bin?auth=site:pw")
        T.check("401 with WWW-Authenticate is returned, not thrown", unauthorized.status.value == 401 && unauthorized.headers[HttpHeaders.WWWAuthenticate] != null)
        val busy = http.get("$base/status/429")
        val gone = http.get("$base/status/503")
        T.check("429 and 503 are returned as responses", busy.status.value == 429 && gone.status.value == 503)
    }

    if (wanted("stream")) T.guarded("stream") {
        val expected = http.get("$base/sha256/200MB.bin").bodyAsText().trim()
        val before = privateKb()
        var peak = before
        val mark = TimeSource.Monotonic.markNow()
        val hash = WinSha256()
        var total = 0L
        http.prepareGet("$base/file/200MB.bin").execute { response ->
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var reads = 0
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                hash.update(buffer, 0, read)
                total += read
                if (++reads % 256 == 0) peak = maxOf(peak, privateKb())
            }
        }
        val took = mark.elapsedNow().inWholeMilliseconds
        T.check(
            "200 MB streamed through bodyAsChannel with flat memory",
            total == 200L * 1024 * 1024 && hash.digest().toHexString() == expected && peak - before < 24 * 1024,
            "${total / 1048576} MB in $took ms (${total / 1048576 * 1000 / maxOf(took, 1)} MB/s), private before=$before KB peak=$peak KB after=${privateKb()} KB"
        )
    }

    if (wanted("parallel")) T.guarded("parallel") {
        for (count in listOf(16, 32)) {
            val piece = 2 * 1024 * 1024
            val key = "par$count"
            val mark = TimeSource.Monotonic.markNow()
            val parts = (0 until count).map { index ->
                async(Dispatchers.Default) {
                    val from = index.toLong() * piece
                    val response = http.get("$base/file/64MB.bin?key=$key&throttle=1024") {
                        header(HttpHeaders.Range, "bytes=$from-${from + piece - 1}")
                    }
                    response.status.value to response.readRawBytes()
                }
            }.awaitAll()
            val took = mark.elapsedNow().inWholeMilliseconds
            val hash = WinSha256()
            parts.forEach { hash.update(it.second) }
            val expected = http.get("$base/sha256/x?size=${count.toLong() * piece}").bodyAsText().trim()
            val stats = http.get("$base/stats?of=$key").bodyAsText()
            val maxActive = Regex("\"maxActive\": (\\d+)").find(stats)?.groupValues?.get(1)?.toIntOrNull()
            T.check(
                "$count ranged requests at once on $count connections",
                parts.all { it.first == 206 } && hash.digest().toHexString() == expected && maxActive == count,
                "maxActive=$maxActive took=$took ms (one after another would take ${count * 2000} ms)"
            )
        }
    }

    if (only != null && "memory" in only) T.guarded("memory") {
        T.info("memory: start ${privateKb()} KB")
        repeat(5) { round ->
            val parts = (0 until 32).map { index ->
                async(Dispatchers.Default) {
                    val from = index.toLong() * 2097152
                    http.get("$base/file/64MB.bin") { header(HttpHeaders.Range, "bytes=$from-${from + 2097151}") }.readRawBytes().size
                }
            }.awaitAll()
            val before = privateKb()
            kotlin.native.runtime.GC.collect()
            T.info("memory: round $round got ${parts.sum() / 1048576} MB, private $before KB, after a collection ${privateKb()} KB")
        }
        delay(12_000)
        T.info("memory: 12 s later ${privateKb()} KB")
        kotlin.native.runtime.GC.collect()
        T.info("memory: after one more collection ${privateKb()} KB")
        repeat(3) { round ->
            var total = 0L
            (0 until 32).map { index ->
                async(Dispatchers.Default) {
                    val from = index.toLong() * 2097152
                    http.prepareGet("$base/file/64MB.bin") { header(HttpHeaders.Range, "bytes=$from-${from + 2097151}") }.execute { response ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(64 * 1024)
                        var mine = 0L
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read < 0) break
                            mine += read
                        }
                        mine
                    }
                }
            }.awaitAll().forEach { total += it }
            val before = privateKb()
            kotlin.native.runtime.GC.collect()
            T.info("memory: streamed round $round got ${total / 1048576} MB, private $before KB, after a collection ${privateKb()} KB")
        }
    }

    if (wanted("redirect")) T.guarded("redirect") {
        val noFollow = client(follow = false)
        for (mode in listOf("abs", "rel", "root", "proto")) {
            val response = noFollow.get("$base/redirect/$mode/file/2KB.bin")
            T.check(
                "followRedirects off: 302 and Location are visible ($mode)",
                response.status.value == 302 && response.headers[HttpHeaders.Location] != null, response.headers[HttpHeaders.Location].orEmpty()
            )
            val followed = http.get("$base/redirect/$mode/file/2KB.bin")
            T.check("followRedirects on reaches the file ($mode)", followed.status.value == 200 && followed.readRawBytes().size == 2048)
        }
        val chain = http.get("$base/redirect/abs/file/2KB.bin?n=5&code=307")
        T.check("a chain of 5 redirects (307)", chain.status.value == 200 && chain.readRawBytes().size == 2048)
        val (loop, loopMs) = failure { http.get("$base/redirect/loop/x") }
        T.check("a redirect loop ends with an exception", loop != null && loop !is CancellationException, "${describe(loop)} after $loopMs ms")
        noFollow.close()
    }

    if (wanted("errors")) T.guarded("errors") {
        val (refused, refusedMs) = failure { http.get("http://127.0.0.1:1/x") }
        T.check("refused connection: an Exception, not a cancellation", refused is Exception && refused !is CancellationException, "${describe(refused)} after $refusedMs ms")
        val (dns, dnsMs) = failure { http.get("http://fetchjet-no-such-host.invalid/x") }
        T.check("unknown host: an Exception, not a cancellation", dns is Exception && dns !is CancellationException, "${describe(dns)} after $dnsMs ms")
        val (cut, cutMs) = failure { http.get("$base/file/1MB.bin?cut=100KB").readRawBytes() }
        T.check("server drops the connection in the middle of the body: an Exception", cut is Exception && cut !is CancellationException, "${describe(cut)} after $cutMs ms")
        var received = 0L
        val (cutStream, _) = failure {
            http.prepareGet("$base/file/1MB.bin?cut=300KB").execute { response ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    received += read
                }
            }
        }
        T.check(
            "the same while streaming: bytes before the cut arrive, then an Exception (never a silent short body)",
            cutStream is Exception && cutStream !is CancellationException && received <= 307200, "received=$received ${describe(cutStream)}"
        )
        var receivedNoLength = 0L
        val (cutNoLength, _) = failure {
            http.prepareGet("$base/file/1MB.bin?cut=300KB&chunked=1").execute { response ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    receivedNoLength += read
                }
            }
        }
        T.check(
            "a chunked body cut in the middle is an Exception too", cutNoLength is Exception && cutNoLength !is CancellationException,
            "received=$receivedNoLength ${describe(cutNoLength)}"
        )
    }

    if (wanted("timeouts")) T.guarded("timeouts") {
        val quick = client(connectMs = 1500, socketMs = 1500)
        val (connect, connectMs) = failure { quick.get("http://10.255.255.1:81/x") }
        T.check("connect timeout 1.5 s", connect is Exception && connect !is CancellationException && connectMs in 1000..6000, "${describe(connect)} after $connectMs ms")
        val (silent, silentMs) = failure { quick.get("$base/stall") }
        T.check("socket timeout 1.5 s when the server never answers", silent is Exception && silent !is CancellationException && silentMs in 1000..6000, "${describe(silent)} after $silentMs ms")
        val (half, halfMs) = failure { quick.get("$base/stall?after=headers").readRawBytes() }
        T.check("socket timeout 1.5 s when the body stops coming", half is Exception && half !is CancellationException && halfMs in 1000..6000, "${describe(half)} after $halfMs ms")
        val slow = quick.get("$base/file/3KB.bin?delay=700")
        T.check("a first byte after 0.7 s is inside the 1.5 s limit", slow.status.value == 200)
        val throttled = quick.get("$base/file/600KB.bin?throttle=200")
        T.check("a slow but moving body (3 s) does not hit the socket timeout", throttled.readRawBytes().size == 614400)
        quick.close()
        val limited = client(requestMs = 1500)
        val (whole, wholeMs) = failure { limited.get("$base/file/8MB.bin?throttle=200").readRawBytes() }
        T.check("request timeout 1.5 s on a long transfer", whole is Exception && wholeMs in 1000..6000, "${describe(whole)} after $wholeMs ms")
        limited.close()
    }

    if (wanted("cancel")) T.guarded("cancel") {
        suspend fun live(key: String): Pair<Int?, Long?> {
            val stats = http.get("$base/stats?of=$key").bodyAsText()
            return Regex("\"active\": (\\d+)").find(stats)?.groupValues?.get(1)?.toIntOrNull() to
                Regex("\"liveBytes\": (\\d+)").find(stats)?.groupValues?.get(1)?.toLongOrNull()
        }
        for (mode in listOf("stream", "whole", "closeClient")) {
            val key = "cancel-$mode"
            val own = if (mode == "closeClient") client() else http
            var got = 0L
            val job = launch(Dispatchers.Default) {
                if (mode == "whole") {
                    got = own.get("$base/file/64MB.bin?throttle=1024&key=$key").readRawBytes().size.toLong()
                } else {
                    own.prepareGet("$base/file/64MB.bin?throttle=1024&key=$key").execute { response ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read < 0) break
                            got += read
                        }
                    }
                }
            }
            delay(1500)
            val mark = TimeSource.Monotonic.markNow()
            val joined = withTimeoutOrNull(5000) {
                job.cancelAndJoin()
                true
            } ?: false
            val took = mark.elapsedNow().inWholeMilliseconds
            if (mode == "closeClient") own.close()
            delay(700)
            val first = live(key)
            delay(2000)
            val second = live(key)
            T.check(
                "cancel in the middle of a body ($mode): returns at once and the server stops sending",
                joined && took < 1500 && second.first == 0 && second.second == first.second,
                "got=$got join=$took ms; server after 0.7 s active=${first.first} sent=${first.second}, after 2.7 s active=${second.first} sent=${second.second}"
            )
        }
    }

    if (wanted("https")) T.guarded("https") {
        val tls = options.firstOrNull { it.startsWith("https=") }?.substringAfter('=')?.toIntOrNull()
        if (tls == null) {
            T.info("https tests skipped (no https= option)")
        } else {
            val (untrusted, untrustedMs) = failure { http.get("https://127.0.0.1:$tls/file/1KB.bin") }
            T.check("a self-signed certificate is refused by default", untrusted is Exception && untrusted !is CancellationException, "${describe(untrusted)} after $untrustedMs ms")
            val relaxed = client(verifyTls = false)
            val response = relaxed.get("https://127.0.0.1:$tls/file/1MB.bin")
            T.check("with sslVerify off the same server works over TLS", response.status.value == 200 && response.readRawBytes().size == 1048576, "version=${response.version}")
            relaxed.close()
        }
    }

    if (wanted("proxy")) T.guarded("proxy") {
        val tls = options.firstOrNull { it.startsWith("https=") }?.substringAfter('=')?.toIntOrNull() ?: 18443
        val name = "files.fjtest.example"
        val plainTarget = "http://$name:$port/file/5KB.bin"
        val secureTarget = "https://$name:$tls/file/5KB.bin"
        suspend fun attempt(label: String, target: String, proxyUrl: String?, socksPort: Int?, loginHeader: Boolean, login: Pair<String, String>? = null) {
            val (error, _) = failure {
                val via = client(
                    verifyTls = false,
                    proxyConfig = if (socksPort != null) ProxyBuilder.socks("127.0.0.1", socksPort) else ProxyBuilder.http(Url(proxyUrl!!)),
                    proxyLogin = login,
                )
                try {
                    val response = via.get(target) { if (loginHeader) header(HttpHeaders.ProxyAuthorization, "Basic Zmo6c2VjcmV0") }
                    val size = response.readRawBytes().size
                    T.info("$label: status ${response.status.value}, $size bytes")
                } finally {
                    via.close()
                }
            }
            if (error != null) T.info("$label: ${describe(error)}")
        }
        attempt("open HTTP proxy, http target", plainTarget, "http://127.0.0.1:18082", null, false)
        attempt("open HTTP proxy, https target", secureTarget, "http://127.0.0.1:18082", null, false)
        attempt("login HTTP proxy, http target, no login sent", plainTarget, "http://127.0.0.1:18081", null, false)
        attempt("login HTTP proxy, http target, Proxy-Authorization header", plainTarget, "http://127.0.0.1:18081", null, true)
        attempt("login HTTP proxy, https target, Proxy-Authorization header", secureTarget, "http://127.0.0.1:18081", null, true)
        attempt("login HTTP proxy, https target, user:password in the proxy URL", secureTarget, "http://fj:secret@127.0.0.1:18081", null, false)
        attempt("open SOCKS5 proxy, http target", plainTarget, null, 11082, false)
        if (nativeEngine) {
            attempt("login HTTP proxy, http target, engine login", plainTarget, "http://127.0.0.1:18081", null, false, "fj" to "secret")
            attempt("login HTTP proxy, https target, engine login", secureTarget, "http://127.0.0.1:18081", null, false, "fj" to "secret")
            attempt("login HTTP proxy, https target, wrong password", secureTarget, "http://127.0.0.1:18081", null, false, "fj" to "wrong")
            attempt("open HTTP proxy, loopback target (bypass list <-loopback>)", "http://127.0.0.1:$port/file/5KB.bin", "http://127.0.0.1:18082", null, false)
            attempt("open SOCKS5 proxy, https target", secureTarget, null, 11082, false)
            attempt("login SOCKS5 proxy, http target, engine login", plainTarget, null, 11081, false, "fj" to "secret")
        }
    }

    if (wanted("sockets")) T.guarded("sockets") {
        val selector = SelectorManager(Dispatchers.IO)
        val listenPort = 28471
        val server = aSocket(selector).tcp().bind("127.0.0.1", listenPort)
        var served = 0
        val accepting = launch(Dispatchers.IO) {
            while (true) {
                val socket = server.accept()
                launch {
                    try {
                        val input = socket.openReadChannel()
                        val output = socket.openWriteChannel(autoFlush = false)
                        val requestLine = input.readUTF8Line() ?: ""
                        val headers = HashMap<String, String>()
                        while (true) {
                            val line = input.readUTF8Line() ?: break
                            if (line.isEmpty()) break
                            val cut = line.indexOf(':')
                            if (cut > 0) headers[line.substring(0, cut).lowercase()] = line.substring(cut + 1).trim()
                        }
                        val length = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = if (length > 0) input.readByteArray(length) else ByteArray(0)
                        val method = requestLine.substringBefore(' ')
                        val answer = "{\"method\":\"$method\",\"path\":\"${requestLine.split(' ').getOrElse(1) { "" }}\"," +
                            "\"bodyBytes\":${body.size},\"sha256\":\"${winSha256(body).toHexString()}\"," +
                            "\"token\":\"${headers["x-fetchjet-token"].orEmpty()}\",\"origin\":\"${headers["origin"].orEmpty()}\"}"
                        val bytes = answer.encodeToByteArray()
                        val status = if (method == "OPTIONS") "204 No Content" else "200 OK"
                        output.writeStringUtf8(
                            "HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\n" +
                                "Access-Control-Allow-Origin: ${headers["origin"] ?: "*"}\r\n" +
                                "Access-Control-Allow-Headers: X-FetchJet, X-FetchJet-Token, Content-Type\r\n" +
                                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                                "Content-Length: ${if (method == "OPTIONS") 0 else bytes.size}\r\nConnection: close\r\n\r\n"
                        )
                        if (method != "OPTIONS") output.writeFully(bytes)
                        output.flush()
                        served++
                    } catch (e: Throwable) {
                        T.info("server side error: $e")
                    } finally {
                        socket.close()
                    }
                }
            }
        }
        val local = "http://127.0.0.1:$listenPort"
        val curl = "C:\\Windows\\System32\\curl.exe"
        val get = DesktopOs.runProcess(listOf(curl, "-s", "-m", "10", "-H", "X-FetchJet-Token: abc", "$local/config?x=1"))
        T.check("ktor-network TCP server answers curl GET", get.stdout.contains("\"method\":\"GET\"") && get.stdout.contains("\"path\":\"/config?x=1\"") && get.stdout.contains("\"token\":\"abc\""), get.stdout.take(160))
        val payload = ByteArray(3 * 1024 * 1024) { (it * 31 + 7).toByte() }
        winWriteFile("$work\\post-body.bin", payload)
        val post = DesktopOs.runProcess(listOf(curl, "-s", "-m", "20", "-X", "POST", "-H", "Content-Type: application/json", "--data-binary", "@$work\\post-body.bin", "$local/links"))
        T.check("POST with a 3 MB body arrives whole", post.stdout.contains("\"bodyBytes\":${payload.size}") && post.stdout.contains(winSha256(payload).toHexString()), post.stdout.take(120))
        val preflight = DesktopOs.runProcess(listOf(curl, "-s", "-m", "10", "-i", "-X", "OPTIONS", "-H", "Origin: chrome-extension://abc", "-H", "Access-Control-Request-Headers: x-fetchjet-token", "$local/add"))
        T.check("OPTIONS preflight gets 204 and the CORS headers", preflight.stdout.contains("204") && preflight.stdout.contains("Access-Control-Allow-Origin: chrome-extension://abc"), preflight.stdout.lines().take(2).joinToString())
        T.info("private before the request storms: ${privateKb()} KB")
        val mark = TimeSource.Monotonic.markNow()
        val many = (0 until 40).map { i ->
            async(Dispatchers.Default) { http.get("$local/n/$i").bodyAsText().contains("\"path\":\"/n/$i\"") }
        }.awaitAll()
        T.check("40 requests at once from the ktor client", many.all { it }, "${mark.elapsedNow().inWholeMilliseconds} ms")
        var sequential = 0
        repeat(300) { i -> if (http.get("$local/s/$i").status.value == 200) sequential++ }
        val beforeCollect = privateKb()
        kotlin.native.runtime.GC.collect()
        T.check("300 requests one after another", sequential == 300, "served=$served private=$beforeCollect KB, after a collection ${privateKb()} KB")

        val clientSocket = aSocket(selector).tcp().connect("127.0.0.1", listenPort)
        val out = clientSocket.openWriteChannel(autoFlush = true)
        val input = clientSocket.openReadChannel()
        out.writeStringUtf8("GET /raw HTTP/1.1\r\nHost: x\r\n\r\n")
        val status = input.readUTF8Line()
        clientSocket.close()
        T.check("ktor-network TCP client talks to it", status == "HTTP/1.1 200 OK", status.orEmpty())

        val (busyPort, _) = failure { aSocket(selector).tcp().bind("127.0.0.1", listenPort).close() }
        T.check("binding the same port twice fails with an Exception", busyPort is Exception, describe(busyPort))
        accepting.cancelAndJoin()
        server.close()
        delay(200)
        val (afterClose, _) = failure { http.get("$local/gone") }
        T.check("after close the port refuses connections", afterClose is Exception, describe(afterClose).take(120))

        val udpA = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 28472)) { broadcast = true; reuseAddress = true }
        val udpB = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", 28473)) { broadcast = true; reuseAddress = true }
        val packet = Buffer()
        packet.write("fj-announce اعلان".encodeToByteArray())
        udpA.send(Datagram(packet, InetSocketAddress("127.0.0.1", 28473)))
        val received = withTimeoutOrNull(3000) { udpB.receive() }
        T.check(
            "UDP datagram on loopback with the sender's address",
            received?.packet?.readByteArray()?.decodeToString() == "fj-announce اعلان" && received.address.toString().contains("28472"), received?.address.toString()
        )
        val nothing = withTimeoutOrNull(500) { udpB.receive() }
        T.check("UDP receive can be given a timeout", nothing == null)
        val broadcast = Buffer()
        broadcast.write("fj-broadcast".encodeToByteArray())
        val (sendError, _) = failure { udpA.send(Datagram(broadcast, InetSocketAddress("127.255.255.255", 28473))) }
        val viaBroadcast = withTimeoutOrNull(1500) { udpB.receive() }
        T.info("UDP send to 127.255.255.255: ${if (sendError == null) "sent" else describe(sendError)}, received=${viaBroadcast != null}")
        udpA.close()
        udpB.close()
        selector.close()
    }

    http.close()
    kotlin.native.runtime.GC.collect()
    T.info("private bytes: idle after the client was made $idle KB, at the end ${privateKb()} KB (after a collection)")
}
