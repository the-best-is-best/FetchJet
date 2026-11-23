package com.michelle.raouf.fetch_jet.core.telemetry

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.network.NetworkClient
import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.core.security.hexOf
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import okio.Path.Companion.toPath
import kotlin.random.Random

internal const val USAGE_PING_OFF_ENV = "FETCHJET_NO_USAGE_PING"
internal const val USAGE_PING_URL = "https://fetchjet.the-best-is-best.workers.dev/ping"
private const val DAY_MS = 86_400_000L

internal expect fun usagePlatform(): String?

object UsagePing {
    private val file get() = "${PathResolver().getSettingsDirectory()}/usage.properties".toPath()

    private val clients = NetworkClient {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 15_000L
                requestTimeoutMillis = 20_000L
            }
            useNetworkSettings()
        }
    }

    @kotlin.concurrent.Volatile
    private var running = false

    suspend fun sendIfDue() {
        if (running) return
        running = true
        try {
            val platform = usagePlatform() ?: return
            val today = currentEpochMs() / DAY_MS
            val saved = read()
            if (saved.second == today) return
            val id = saved.first ?: newId()
            val sent = post(id, platform)
            write(id, if (sent) today else saved.second)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            running = false
        }
    }

    private suspend fun post(id: String, platform: String): Boolean {
        val body = """{"id":"$id","version":"$APP_VERSION","platform":"$platform"}"""
        val response = clients.current.post(USAGE_PING_URL) {
            header("User-Agent", "FetchJet/$APP_VERSION")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.status.isSuccess()
    }

    private fun newId(): String =
        hexOf(Random.nextBytes(16))

    private fun read(): Pair<String?, Long> = try {
        if (!AppFileSystem.exists(file)) null to -1L
        else {
            val lines = AppFileSystem.read(file) { readUtf8() }.lineSequence().map { it.trim() }.toList()
            fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()
            val id = value("installId")?.takeIf { it.length >= 16 }
            id to (value("lastDay")?.toLongOrNull() ?: -1L)
        }
    } catch (_: Throwable) {
        null to -1L
    }

    private fun write(id: String, day: Long) {
        try {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8("installId=$id\nlastDay=$day\n") }
        } catch (_: Throwable) {
        }
    }
}
