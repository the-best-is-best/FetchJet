package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.network.NetworkClient
import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.core.update.UPDATE_REPOSITORY
import com.michelle.raouf.fetch_jet.core.update.isOlderExtension
import io.ktor.client.statement.readRawBytes
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The extension version each store has published right now, so an outdated extension is sent to
 * the right place: the store when it already has the version the app needs (the browser updates
 * it by itself), the copy inside the app when the store is behind (Chromium browsers only).
 */
object ExtensionStore {
    private val clients = NetworkClient {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 15_000L
            }
            useNetworkSettings()
        }
    }

    private const val AMO_API =
        "https://addons.mozilla.org/api/v5/addons/addon/fetchjet-download-manager/"
    private const val EDGE_UPDATE =
        "https://edge.microsoft.com/extensionwebstorebase/v1/crx?response=updatecheck&acceptformat=crx3&prodversion=142.0&x=id%3D$EDGE_EXTENSION_ID%26installsource%3Dondemand%26uc"

    /** Store version for browser [id] ("firefox", "edge", Android Firefox packages); null = none / unknown. */
    suspend fun versionFor(id: String): String? = try {
        when {
            id == "firefox" || id.startsWith("org.mozilla.") -> firefox()
            id == "edge" -> edge()
            else -> null
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** A signed Firefox build attached to a FetchJet release (CI signs one with every release). */
    data class SignedXpi(val version: String, val url: String, val fileName: String)

    /** The newest signed .xpi among the latest published releases; null when none / offline. */
    suspend fun githubXpi(): SignedXpi? = try {
        val body = text("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=10")
        @Suppress("UNCHECKED_CAST")
        val releases = (body?.let { MiniJson.parse(it) } as? List<Map<String, Any?>>).orEmpty()
        releases.asSequence()
            .filter { it["draft"] != true }
            .flatMap { rel ->
                @Suppress("UNCHECKED_CAST")
                (rel["assets"] as? List<Map<String, Any?>>).orEmpty().asSequence()
            }
            .mapNotNull { a ->
                val name = a["name"] as? String ?: return@mapNotNull null
                val url = a["browser_download_url"] as? String ?: return@mapNotNull null
                val version = XPI_NAME.find(name)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!url.startsWith("https://github.com/$UPDATE_REPOSITORY/releases/download/")) return@mapNotNull null
                SignedXpi(version, url, name)
            }
            .sortedWith { a, b ->
                when {
                    isOlderExtension(a.version, b.version) -> 1
                    isOlderExtension(b.version, a.version) -> -1
                    else -> 0
                }
            }
            .firstOrNull()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Downloads [xpi]; null on failure. */
    suspend fun download(xpi: SignedXpi): ByteArray? = try {
        val res = clients.current.get(xpi.url) { header("User-Agent", "FetchJet/$APP_VERSION") }
        if (res.status.isSuccess()) res.readRawBytes().takeIf { it.size > 1024 } else null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private val XPI_NAME = Regex("""^fetchjet_download_manager-([0-9.]+)\.xpi$""")

    private suspend fun text(url: String): String? {
        val res = clients.current.get(url) { header("User-Agent", "FetchJet/$APP_VERSION") }
        return if (res.status.isSuccess()) res.bodyAsText() else null
    }

    private suspend fun firefox(): String? {
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(text(AMO_API) ?: return null) as? Map<String, Any?> ?: return null
        @Suppress("UNCHECKED_CAST")
        return (json["current_version"] as? Map<String, Any?>)?.get("version") as? String
    }

    private suspend fun edge(): String? {
        val xml = text(EDGE_UPDATE) ?: return null
        return Regex("""<updatecheck[^>]*\sversion="([^"]+)"""").find(xml)?.groupValues?.get(1)
    }
}
