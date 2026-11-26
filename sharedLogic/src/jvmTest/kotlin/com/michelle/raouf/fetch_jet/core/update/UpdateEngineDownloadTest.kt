package com.michelle.raouf.fetch_jet.core.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateEngineDownloadTest {
    private lateinit var server: HttpServer
    private val body = Random(7).nextBytes(12 * 1024 * 1024)
    private val open = AtomicInteger()
    private val mostOpen = AtomicInteger()
    private val ranges = AtomicInteger()
    private lateinit var dir: File

    @BeforeTest
    fun start() {
        dir = File(System.getProperty("java.io.tmpdir"), "fj-update-test-${System.nanoTime()}").apply { mkdirs() }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = java.util.concurrent.Executors.newFixedThreadPool(40)
        server.createContext("/release.apk") { ex ->
            val now = open.incrementAndGet()
            mostOpen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                ex.responseHeaders.add("Accept-Ranges", "bytes")
                ex.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
                ex.responseHeaders.add("ETag", "\"release-1\"")
                val range = ex.requestHeaders.getFirst("Range")
                if (range != null && range.startsWith("bytes=")) {
                    ranges.incrementAndGet()
                    val (a, b) = range.removePrefix("bytes=").split('-')
                    val from = a.toLong()
                    val to = if (b.isEmpty()) body.size - 1L else minOf(b.toLong(), body.size - 1L)
                    ex.responseHeaders.add("Content-Range", "bytes $from-$to/${body.size}")
                    if (ex.requestMethod == "HEAD") {
                        ex.sendResponseHeaders(206, -1)
                    } else {
                        ex.sendResponseHeaders(206, to - from + 1)
                        ex.responseBody.use { out ->
                            var at = from.toInt()
                            while (at <= to) {
                                val n = minOf(64 * 1024, (to - at + 1).toInt())
                                out.write(body, at, n)
                                at += n
                                Thread.sleep(2)
                            }
                        }
                    }
                } else if (ex.requestMethod == "HEAD") {
                    ex.responseHeaders.add("Content-Length", body.size.toString())
                    ex.sendResponseHeaders(200, -1)
                } else {
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
            } catch (_: Throwable) {
            } finally {
                open.decrementAndGet()
                ex.close()
            }
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    @Test
    fun theUpdateFileComesThroughTheEngineOverSeveralConnections() = runBlocking {
        val url = "http://127.0.0.1:${server.address.port}/release.apk"
        val file = UpdateManager.downloadWithEngine(url, dir.absolutePath, "release.apk")
        assertEquals(File(dir, "release.apk").absolutePath, File(file.toString()).absolutePath)
        val want = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        assertEquals(want, UpdateManager.sha256Of(file.toString().toPath()))
        assertTrue(mostOpen.get() > 1, "connections at once: ${mostOpen.get()}")
        assertTrue(ranges.get() > 1, "range requests: ${ranges.get()}")
        assertEquals(listOf("release.apk"), dir.list()!!.toList())
    }
}
