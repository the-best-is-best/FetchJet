package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class SiteCookieTest {
    private lateinit var home: File
    private var oldHome: String? = null
    private lateinit var server: HttpServer
    private val port get() = server.address.port

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjcookie", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo") { ex ->
            val body = (ex.requestHeaders.getFirst("Cookie") ?: "none").toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        NetworkSettings.removeCookie("127.0.0.1")
        server.stop(0)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private suspend fun cookieSeenByServer(extra: Map<String, String> = emptyMap()): String {
        val client = HttpClient { useNetworkSettings() }
        return try {
            client.get("http://127.0.0.1:$port/echo") { extra.forEach { (k, v) -> header(k, v) } }.bodyAsText()
        } finally {
            client.close()
        }
    }

    @Test
    fun aSavedBrowserSessionIsSentToThatSiteAndRemovedOnRequest() = runBlocking {
        assertEquals("none", cookieSeenByServer())
        NetworkSettings.saveCookie("127.0.0.1", "session=abc; theme=dark")
        assertEquals("session=abc; theme=dark", cookieSeenByServer())
        assertEquals("127.0.0.1", NetworkSettings.cookieFor("127.0.0.1")?.site)
        NetworkSettings.removeCookie("127.0.0.1")
        assertEquals("none", cookieSeenByServer())
        assertNull(NetworkSettings.cookieFor("127.0.0.1"))
    }

    @Test
    fun aCookieTheTaskAlreadyCarriesIsNotReplaced() = runBlocking {
        NetworkSettings.saveCookie("127.0.0.1", "session=saved")
        assertEquals("from=task", cookieSeenByServer(mapOf("Cookie" to "from=task")))
    }

    @Test
    fun aBlankCookieIsNotSavedAndTheFileDoesNotKeepItInPlainText() {
        NetworkSettings.saveCookie("127.0.0.1", "   ")
        assertNull(NetworkSettings.cookieFor("127.0.0.1"))
        NetworkSettings.saveCookie("127.0.0.1", "session=verysecretvalue")
        val file = File("${PathResolver().getSettingsDirectory()}/network.json")
        assertFalse(file.readText().contains("verysecretvalue"), "the cookie is stored encrypted")
    }
}
