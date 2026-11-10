@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.runtime.NativeRuntimeApi::class)

import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.WinLoopbackProxy
import com.michelle.raouf.fetch_jet.platform.win.WinSha256
import com.michelle.raouf.fetch_jet.platform.win.WinSocksForwarder
import com.michelle.raouf.fetch_jet.platform.win.WinSocksUpstream
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winMemoryUse
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import com.michelle.raouf.fetch_jet.platform.win.winSha256
import com.michelle.raouf.fetch_jet.platform.win.winUptimeMs
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
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
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.readAvailable
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.time.TimeSource
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
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
import platform.windows.GetCurrentProcess
import platform.windows.GetProcessHandleCount

private fun proxyPrivateKb(): Long = (winMemoryUse()?.privateBytes ?: 0L) / 1024

private fun proxyHandleCount(): Int = memScoped {
    val count = alloc<UIntVar>()
    count.value = 0u
    GetProcessHandleCount(GetCurrentProcess(), count.ptr)
    count.value.toInt()
}

private fun proxyClient(port: Int, login: Boolean = true): HttpClient = HttpClient(WinHttpNative) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = null
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
    }
    engine {
        sslVerify = false
        proxy = ProxyBuilder.http(Url("http://127.0.0.1:$port"))
        if (login) {
            proxyUser = WinSocksForwarder.LOGIN_USER
            proxyPassword = WinSocksForwarder.loginPassword
        }
    }
}

private class ProxyFetch(val status: Int, val reason: String, val size: Long, val sha: String)

private class ProxyOutcome(val fetch: ProxyFetch?, val error: Throwable?, val ms: Long) {
    val failedCleanly: Boolean
        get() = (error is Exception && error !is CancellationException) || (fetch != null && fetch.status !in 200..299)

    val brokeOff: Boolean get() = error is Exception && error !is CancellationException

    fun ok(status: Int, size: Long, sha: String? = null): Boolean =
        fetch != null && fetch.status == status && fetch.size == size && (sha == null || fetch.sha == sha)

    fun text(): String = when {
        fetch != null -> "status ${fetch.status} \"${fetch.reason}\", ${fetch.size} bytes, $ms ms"
        error != null -> generateSequence(error) { it.cause }.take(3).joinToString(" <- ") {
            "${it::class.simpleName}: ${it.message?.take(100)}"
        } + ", $ms ms"

        else -> "nothing, $ms ms"
    }
}

private suspend fun proxyFetch(
    client: HttpClient,
    url: String,
    range: String? = null,
    progress: AtomicLong? = null,
): ProxyFetch = client.prepareGet(url) { if (range != null) header(HttpHeaders.Range, range) }.execute { response ->
    val channel = response.bodyAsChannel()
    val buffer = ByteArray(64 * 1024)
    val hash = WinSha256()
    var total = 0L
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read < 0) break
        hash.update(buffer, 0, read)
        total += read
        progress?.addAndGet(read.toLong())
    }
    ProxyFetch(response.status.value, response.status.description, total, hash.digest().toHexString())
}

private suspend fun proxyTry(block: suspend () -> ProxyFetch): ProxyOutcome {
    val mark = TimeSource.Monotonic.markNow()
    return try {
        ProxyOutcome(block(), null, mark.elapsedNow().inWholeMilliseconds)
    } catch (e: Throwable) {
        ProxyOutcome(null, e, mark.elapsedNow().inWholeMilliseconds)
    }
}

private fun proxyLogCount(path: String, text: String): Int =
    (winReadText(path) ?: "").lineSequence().count { it.contains(text) }

private suspend fun proxyStat(direct: HttpClient, base: String, key: String, field: String): Long? =
    Regex("\"$field\": (\\d+)").find(direct.get("$base/stats?of=$key").bodyAsText())?.groupValues?.get(1)?.toLongOrNull()

private suspend fun proxyPortIsFree(selector: SelectorManager, port: Int): Boolean = try {
    aSocket(selector).tcp().bind("127.0.0.1", port).close()
    true
} catch (_: Throwable) {
    false
}

private suspend fun proxyDrained(proxy: WinLoopbackProxy, limitMs: Int = 4000): Int {
    var waited = 0
    while (proxy.activeTunnels > 0 && waited < limitMs) {
        delay(25)
        waited += 25
    }
    return waited
}

fun proxyTests(work: String, options: List<String>) = runBlocking {
    fun number(name: String, fallback: Int): Int =
        options.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.toIntOrNull() ?: fallback

    val http = number("server", 18200)
    val tls = number("https", 18443)
    val socksLogin = number("socks", 11081)
    val socksOpen = number("socksopen", 11082)
    val only = options.firstOrNull { it.startsWith("only=") }?.substringAfter('=')?.split(',')?.toSet()
    fun wanted(name: String) = only == null || name in only
    val base = "http://127.0.0.1:$http"
    val name = "files.fjtest.example"
    val plain = "http://$name:$http"
    val secure = "https://$name:$tls"
    val loginLog = "$work\\servers\\proxy-login.log"
    val openLog = "$work\\servers\\proxy-open.log"
    val forwarderLogin = WinSocksForwarder.LOGIN_USER + ":" + WinSocksForwarder.loginPassword
    val stamp = winUptimeMs()
    val curl = "C:\\Windows\\System32\\curl.exe"
    val direct = HttpClient(WinHttpNative) { expectSuccess = false }
    val selector = SelectorManager(Dispatchers.IO)

    val ready = try {
        direct.get("$base/sha256/1KB.bin").status.value == 200
    } catch (_: Throwable) {
        false
    }
    if (!ready || winReadText(loginLog) == null || winReadText(openLog) == null) {
        T.check("the test servers run and write their logs to $work\\servers (desktopNative/tools/testserver/servers.sh start)", false)
        direct.close()
        selector.close()
        return@runBlocking
    }
    val sha1Mb = direct.get("$base/sha256/1MB.bin").bodyAsText().trim()
    val idle = proxyPrivateKb()

    if (wanted("forward")) T.guarded("forward") {
        val port = WinSocksForwarder.portFor("127.0.0.1", socksLogin, "fj", "secret")
        val again = WinSocksForwarder.portFor("127.0.0.1", socksLogin, "fj", "secret")
        val open = WinSocksForwarder.portFor("127.0.0.1", socksOpen, "", "")
        T.check(
            "portFor starts a forwarder on a free port and gives the same port for the same proxy and login",
            port in 1024..65535 && again == port && open in 1024..65535 && open != port, "login proxy -> $port, open proxy -> $open"
        )
        val viaLogin = proxyClient(port)
        val viaOpen = proxyClient(open)

        suspend fun through(label: String, client: HttpClient, log: String, user: String) {
            for ((kind, root, targetPort) in listOf(Triple("http", plain, http), Triple("https", secure, tls))) {
                val line = "SOCKS OK $name:$targetPort name user=$user"
                val before = proxyLogCount(log, line)
                val got = proxyTry { proxyFetch(client, "$root/file/1MB.bin") }
                val after = proxyLogCount(log, line)
                T.check(
                    "$label: $kind download by host name, right content, the proxy got the name (remote DNS)",
                    got.ok(200, 1048576L, sha1Mb) && after > before, "${got.text()}; proxy log \"$line\": $before -> $after"
                )
            }
        }
        through("SOCKS5 proxy with login", viaLogin, loginLog, "fj")
        through("SOCKS5 proxy without login", viaOpen, openLog, "")

        val part = direct.get("$base/file/1MB.bin") { header(HttpHeaders.Range, "bytes=1000-1999") }.readRawBytes()
        val ranged = proxyTry { proxyFetch(viaLogin, "$plain/file/1MB.bin", "bytes=1000-1999") }
        T.check("Range request through the forwarder: 206 and the right bytes", ranged.ok(206, 1000L, winSha256(part).toHexString()), ranged.text())
        val head = viaLogin.head("$plain/file/64MB.bin")
        T.check(
            "HEAD through the forwarder: length and no body",
            head.status.value == 200 && head.headers[HttpHeaders.ContentLength] == "67108864" && head.readRawBytes().isEmpty()
        )
        val literal = "SOCKS OK 127.0.0.1:$http ip user=fj"
        val literalBefore = proxyLogCount(loginLog, literal)
        val byAddress = proxyTry { proxyFetch(viaLogin, "$base/file/5KB.bin") }
        T.check(
            "an IP address target goes to the proxy as an address",
            byAddress.ok(200, 5120L) && proxyLogCount(loginLog, literal) > literalBefore, byAddress.text()
        )
        val tunnel = "SOCKS OK $name:$tls name user=fj"
        val tunnelsBefore = proxyLogCount(loginLog, tunnel)
        var reused = 0
        repeat(40) { if (proxyFetch(viaLogin, "$secure/file/1KB.bin").status == 200) reused++ }
        val opened = proxyLogCount(loginLog, tunnel) - tunnelsBefore
        T.check("40 https downloads one after another reuse the tunnel", reused == 40 && opened <= 3, "new SOCKS5 connections: $opened")
        viaLogin.close()
        viaOpen.close()
    }

    if (wanted("refuse")) T.guarded("refuse") {
        suspend fun refused(label: String, client: HttpClient, mark: String?, limitMs: Long) {
            for ((kind, root) in listOf("http" to plain, "https" to secure)) {
                val key = "refuse-$stamp-${label.hashCode()}-$kind"
                val before = if (mark == null) 0 else proxyLogCount(loginLog, mark)
                val got = proxyTry { proxyFetch(client, "$root/file/5KB.bin?key=$key") }
                val seen = if (mark == null) 1 else proxyLogCount(loginLog, mark) - before
                val reached = proxyStat(direct, base, key, "requests")
                T.check(
                    "$label: the $kind request fails cleanly and nothing reaches the server",
                    got.failedCleanly && got.ms < limitMs && seen > 0 && (reached ?: 0L) == 0L,
                    got.text() + (if (mark == null) "" else "; proxy log \"$mark\": +$seen") + "; requests at the server: ${reached ?: 0}"
                )
            }
        }

        val wrong = proxyClient(WinSocksForwarder.portFor("127.0.0.1", socksLogin, "fj", "wrong"))
        refused("wrong SOCKS5 password", wrong, "SOCKS AUTH-FAIL fj", 8000)
        wrong.close()
        val nameless = proxyClient(WinSocksForwarder.portFor("127.0.0.1", socksLogin, "", ""))
        refused("no login for a SOCKS5 proxy that asks for one", nameless, "SOCKS NO-LOGIN-OFFERED", 8000)
        nameless.close()
        val dead = proxyClient(WinSocksForwarder.portFor("127.0.0.1", 1, "", ""))
        refused("SOCKS5 proxy that is not running", dead, null, 15000)
        dead.close()

        val open = WinSocksForwarder.portFor("127.0.0.1", socksOpen, "", "")
        val viaOpen = proxyClient(open)
        val closedPort = proxyTry { proxyFetch(viaOpen, "http://$name:1/x") }
        T.check("the proxy cannot reach the target: the request fails cleanly", closedPort.failedCleanly && closedPort.ms < 15000, closedPort.text())
        viaOpen.close()

        val stranger = proxyClient(open, login = false)
        val key = "stranger-$stamp"
        val unknown = proxyTry { proxyFetch(stranger, "$plain/file/5KB.bin?key=$key") }
        val unknownTls = proxyTry { proxyFetch(stranger, "$secure/file/5KB.bin?key=$key") }
        val byCurl = winRunProcess(
            listOf(curl, "-s", "-m", "20", "-o", "NUL", "-w", "%{http_code}", "-x", "http://127.0.0.1:$open", "$plain/file/5KB.bin?key=$key")
        ).stdout.trim()
        T.check(
            "a program without the forwarder's own login gets 407 and reaches nothing",
            unknown.fetch?.status == 407 && unknownTls.failedCleanly && byCurl == "407" && (proxyStat(direct, base, key, "requests") ?: 0L) == 0L,
            "http: ${unknown.text()}; https: ${unknownTls.text()}; curl: $byCurl"
        )
        stranger.close()
    }

    if (wanted("parallel")) T.guarded("parallel") {
        val forwarder = WinLoopbackProxy.start(WinSocksUpstream("127.0.0.1", socksLogin, "fj", "secret"), forwarderLogin)
        for ((kind, root) in listOf("http" to plain, "https" to secure)) {
            val client = proxyClient(forwarder.port)
            val count = 120
            val piece = 256 * 1024
            val key = "fwd-$kind-$stamp"
            val peak = AtomicInt(0)
            val before = proxyPrivateKb()
            val peakKb = AtomicLong(before)
            val watcher = launch(Dispatchers.Default) {
                while (true) {
                    peak.value = maxOf(peak.value, forwarder.activeTunnels)
                    peakKb.value = maxOf(peakKb.value, proxyPrivateKb())
                    delay(40)
                }
            }
            val mark = TimeSource.Monotonic.markNow()
            val parts = (0 until count).map { index ->
                async(Dispatchers.Default) {
                    delay(index * 12L)
                    val from = index.toLong() * piece
                    val response = client.get("$root/file/64MB.bin?key=$key&throttle=48") {
                        header(HttpHeaders.Range, "bytes=$from-${from + piece - 1}")
                    }
                    response.status.value to response.readRawBytes()
                }
            }.awaitAll()
            val took = mark.elapsedNow().inWholeMilliseconds
            watcher.cancelAndJoin()
            val hash = WinSha256()
            parts.forEach { hash.update(it.second) }
            val expected = direct.get("$base/sha256/x?size=${count.toLong() * piece}").bodyAsText().trim()
            val maxActive = proxyStat(direct, base, key, "maxActive")
            T.check(
                "$count $kind downloads at once through one forwarder: $count tunnels, every byte right",
                parts.all { it.first == 206 } && hash.digest().toHexString() == expected && maxActive == count.toLong() && peak.value >= count,
                "server saw $maxActive connections at once, forwarder peak ${peak.value} tunnels, $took ms, private $before KB -> peak ${peakKb.value} KB"
            )
            client.close()
            val waited = proxyDrained(forwarder)
            T.check("after the client is closed no tunnel is left ($kind)", forwarder.activeTunnels == 0, "tunnels=${forwarder.activeTunnels} after $waited ms")
        }
        forwarder.close()
        kotlin.native.runtime.GC.collect()
        T.info("private after the parallel rounds and a collection: ${proxyPrivateKb()} KB")
    }

    if (wanted("cancel")) T.guarded("cancel") {
        val forwarder = WinLoopbackProxy.start(WinSocksUpstream("127.0.0.1", socksOpen), forwarderLogin)
        val client = proxyClient(forwarder.port)
        for ((kind, root) in listOf("http" to plain, "https" to secure)) {
            val key = "fwd-cancel-$kind-$stamp"
            val got = AtomicLong(0L)
            val job = launch(Dispatchers.Default) {
                try {
                    proxyFetch(client, "$root/file/64MB.bin?throttle=512&key=$key", progress = got)
                } catch (_: Throwable) {
                }
            }
            delay(1500)
            val mark = TimeSource.Monotonic.markNow()
            val joined = withTimeoutOrNull(5000) {
                job.cancelAndJoin()
                true
            } ?: false
            val took = mark.elapsedNow().inWholeMilliseconds
            delay(700)
            val firstBytes = proxyStat(direct, base, key, "liveBytes")
            delay(1500)
            val active = proxyStat(direct, base, key, "active")
            val secondBytes = proxyStat(direct, base, key, "liveBytes")
            T.check(
                "pause in the middle of a $kind download through the forwarder: the server's connection is freed at once",
                joined && took < 1500 && got.value > 0L && active == 0L && secondBytes == firstBytes && forwarder.activeTunnels == 0,
                "got=${got.value} join=$took ms; server sent $firstBytes after 0.7 s, $secondBytes after 2.2 s, active=$active; tunnels=${forwarder.activeTunnels}"
            )
        }
        client.close()
        forwarder.close()
    }

    if (wanted("close")) T.guarded("close") {
        val forwarder = WinLoopbackProxy.start(WinSocksUpstream("127.0.0.1", socksOpen), forwarderLogin)
        val port = forwarder.port
        val client = proxyClient(port)
        val stuck = (0 until 10).map { index ->
            async(Dispatchers.Default) { proxyTry { proxyFetch(client, (if (index % 2 == 0) plain else secure) + "/stall") } }
        }
        var waited = 0
        while (forwarder.activeTunnels < 10 && waited < 5000) {
            delay(50)
            waited += 50
        }
        delay(500)
        val tunnels = forwarder.activeTunnels
        val mark = TimeSource.Monotonic.markNow()
        forwarder.close()
        val closeMs = mark.elapsedNow().inWholeMilliseconds
        val ended = withTimeoutOrNull(8000) { stuck.awaitAll() }
        val endMs = mark.elapsedNow().inWholeMilliseconds
        val drainMs = proxyDrained(forwarder)
        T.check(
            "close with 10 waiting tunnels: returns at once, every request ends with an exception, no tunnel is left",
            tunnels == 10 && closeMs < 1000 && ended != null && ended.all { it.brokeOff } && endMs < 5000 &&
                forwarder.activeTunnels == 0 && !forwarder.isOpen,
            "tunnels before=$tunnels, close took $closeMs ms, requests ended after $endMs ms, tunnels gone after $drainMs ms more: " +
                (ended?.firstOrNull()?.text() ?: "requests still waiting")
        )
        T.check("close frees the port", proxyPortIsFree(selector, port), "port $port")
        val gone = proxyTry { proxyFetch(client, "$plain/file/1KB.bin") }
        T.check("after close the port refuses connections", gone.brokeOff, gone.text())
        client.close()

        WinSocksForwarder.closeAll()
        val fresh = proxyClient(WinSocksForwarder.portFor("127.0.0.1", socksLogin, "fj", "secret"))
        val works = proxyTry { proxyFetch(fresh, "$plain/file/1MB.bin") }
        T.check("closeAll, then portFor starts a new forwarder that works", works.ok(200, 1048576L, sha1Mb), works.text())
        fresh.close()
        WinSocksForwarder.closeAll()
    }

    if (wanted("leak")) T.guarded("leak") {
        val forwarder = WinLoopbackProxy.start(WinSocksUpstream("127.0.0.1", socksOpen), forwarderLogin)
        val client = proxyClient(forwarder.port)
        repeat(40) { proxyFetch(client, "$plain/file/1KB.bin") }
        proxyDrained(forwarder)
        kotlin.native.runtime.GC.collect()
        delay(300)
        val kbBefore = proxyPrivateKb()
        val handlesBefore = proxyHandleCount()
        var right = 0
        val mark = TimeSource.Monotonic.markNow()
        repeat(400) { if (proxyFetch(client, "$plain/file/1KB.bin").size == 1024L) right++ }
        val took = mark.elapsedNow().inWholeMilliseconds
        proxyDrained(forwarder)
        kotlin.native.runtime.GC.collect()
        delay(300)
        val kbAfter = proxyPrivateKb()
        val handlesAfter = proxyHandleCount()
        T.check(
            "400 http downloads one after another (400 tunnels, 800 threads): all right, no tunnel, handle or memory left behind",
            right == 400 && forwarder.activeTunnels == 0 && handlesAfter - handlesBefore < 24 && kbAfter - kbBefore < 4096,
            "$took ms, handles $handlesBefore -> $handlesAfter, private $kbBefore KB -> $kbAfter KB"
        )
        client.close()
        forwarder.close()
    }

    if (wanted("throttle")) T.guarded("throttle") {
        val size8 = 8L * 1024 * 1024
        val sha8 = direct.get("$base/sha256/8MB.bin").bodyAsText().trim()
        val open = WinLoopbackProxy.start()
        val openClient = proxyClient(open.port, login = false)
        val free = proxyTry { proxyFetch(openClient, "$base/file/8MB.bin") }
        T.check(
            "no limit: 8 MB pass unchanged and the counter has them",
            free.ok(200, size8, sha8) && open.downloadedBytes in size8..(size8 + 4096) && open.uploadedBytes > 0L,
            "${free.text()}; downloaded counter ${open.downloadedBytes} (file $size8 + response head), uploaded counter ${open.uploadedBytes}"
        )
        openClient.close()
        open.close()

        val size16 = 16L * 1024 * 1024
        val sha16 = direct.get("$base/sha256/16MB.bin").bodyAsText().trim()
        val limit = AtomicLong(200L * 1024)
        val limited = WinLoopbackProxy.start(speedLimit = { limit.value })
        val client = proxyClient(limited.port, login = false)
        val received = AtomicLong(0L)
        val download = async(Dispatchers.Default) { proxyTry { proxyFetch(client, "$base/file/16MB.bin", progress = received) } }

        suspend fun rate(windowMs: Long): Long {
            val from = received.value
            val mark = TimeSource.Monotonic.markNow()
            delay(windowMs)
            return (received.value - from) * 1000L / mark.elapsedNow().inWholeMilliseconds
        }

        delay(1000)
        val speeds = ArrayList<Long>()
        val sampler = launch {
            while (true) {
                delay(300)
                speeds.add(limited.downloadSpeedBytesPerSec())
            }
        }
        val first = rate(5000)
        sampler.cancelAndJoin()
        T.check("limit 200 KB/s holds", first in 180_000L..230_000L, "measured ${first / 1024} KB/s over 5 s at the client")
        T.check(
            "the proxy's own speed number follows the limit",
            speeds.size >= 10 && speeds.drop(3).all { it in 150_000L..260_000L },
            "every 300 ms, KB/s: " + speeds.joinToString(" ") { (it / 1024).toString() }
        )
        limit.value = 500L * 1024
        delay(700)
        val second = rate(4000)
        T.check("a limit changed to 500 KB/s in the middle of the download applies", second in 450_000L..575_000L, "measured ${second / 1024} KB/s over 4 s")
        val soFar = received.value
        limit.value = 0L
        val rest = withTimeoutOrNull(20_000) { download.await() }
        T.check(
            "limit removed in the middle: the rest comes at full speed, the file and the counter are right",
            rest != null && rest.ok(200, size16, sha16) && limited.downloadedBytes in size16..(size16 + 4096),
            "${rest?.text() ?: "still running"}; ${soFar / 1024} KB had arrived under the limits; counter ${limited.downloadedBytes}"
        )
        client.close()
        limited.close()

        val shared = WinLoopbackProxy.start(speedLimit = { 256L * 1024 })

        suspend fun curlGet(url: String): List<String> = winRunProcess(
            listOf(
                curl, "-s", "-k", "-m", "40", "-o", "NUL", "-w", "%{http_code} %{size_download} %{time_total}",
                "-x", "http://127.0.0.1:${shared.port}", url
            )
        ).stdout.trim().split(' ')

        val overTls = curlGet("https://127.0.0.1:$tls/file/1MB.bin")
        val tlsSeconds = overTls.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        T.check(
            "curl through CONNECT (https, the way yt-dlp uses the proxy) at 256 KB/s: 1 MB takes about 4 s",
            overTls.getOrNull(0) == "200" && overTls.getOrNull(1) == "1048576" && tlsSeconds in 3.4..5.2, overTls.joinToString(" ")
        )
        val pair = listOf("a", "b").map { tag ->
            async(Dispatchers.Default) { curlGet("$base/file/512KB.bin?key=share-$stamp-$tag") }
        }.awaitAll()
        T.check(
            "two downloads share one limit: 2 x 512 KB at 256 KB/s take about 4 s each",
            pair.all { it.getOrNull(0) == "200" && it.getOrNull(1) == "524288" && (it.getOrNull(2)?.toDoubleOrNull() ?: 0.0) in 3.2..5.2 },
            pair.joinToString(" | ") { it.joinToString(" ") }
        )
        val unreachable = curlGet("http://127.0.0.1:1/x")
        T.check("a server that refuses the connection: the proxy answers 502", unreachable.getOrNull(0) == "502", unreachable.joinToString(" "))
        shared.close()

        val closing = WinLoopbackProxy.start(speedLimit = { 100L * 1024 })
        val closingClient = proxyClient(closing.port, login = false)
        val running = async(Dispatchers.Default) { proxyTry { proxyFetch(closingClient, "$base/file/8MB.bin") } }
        delay(1500)
        val tunnels = closing.activeTunnels
        val mark = TimeSource.Monotonic.markNow()
        closing.close()
        val closeMs = mark.elapsedNow().inWholeMilliseconds
        val ended = withTimeoutOrNull(8000) { running.await() }
        val endMs = mark.elapsedNow().inWholeMilliseconds
        proxyDrained(closing)
        T.check(
            "close in the middle of a limited download: the client gets an error at once, nothing is left, the port is free",
            tunnels == 1 && closeMs < 1000 && ended != null && ended.brokeOff && endMs < 3000 && closing.activeTunnels == 0 &&
                proxyPortIsFree(selector, closing.port),
            "close took $closeMs ms, the download ended after $endMs ms: ${ended?.text() ?: "still running"}"
        )
        closingClient.close()
    }

    direct.close()
    selector.close()
    kotlin.native.runtime.GC.collect()
    T.info("private bytes: before the proxy tests $idle KB, at the end ${proxyPrivateKb()} KB (after a collection)")
}
