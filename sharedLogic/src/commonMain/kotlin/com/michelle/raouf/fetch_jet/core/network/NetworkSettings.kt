package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
enum class ProxyKind { NONE, HTTP, SOCKS5 }

@Serializable
data class ProxySettings(
    val kind: ProxyKind = ProxyKind.NONE,
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = ""
) {
    val isOn: Boolean get() = kind != ProxyKind.NONE && host.isNotBlank() && port in 1..65535
    val hasLogin: Boolean get() = user.isNotEmpty()

    /** For yt-dlp `--proxy`: `http://user:pass@host:port` or `socks5h://...` (DNS through the proxy). */
    fun url(): String {
        val scheme = if (kind == ProxyKind.SOCKS5) "socks5h" else "http"
        val login =
            if (hasLogin) "${user.encodeURLParameter()}:${password.encodeURLParameter()}@" else ""
        return "$scheme://$login${hostForUrl()}:$port"
    }

    fun hostForUrl(): String = if (':' in host && !host.startsWith("[")) "[$host]" else host
}

@Serializable
data class SiteLogin(val site: String, val user: String, val password: String) {
    /** "example.com" also covers "files.example.com". */
    fun matches(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        val s = cleanSite(site)
        return s.isNotEmpty() && (h == s || h.endsWith(".$s"))
    }

    companion object {
        fun cleanSite(text: String): String = text.trim().substringAfter("://")
            .substringBefore('/').substringAfter('@').substringBefore(':')
            .lowercase().removePrefix("www.").trimEnd('.')
    }
}

@Serializable
private data class NetworkFile(
    val proxy: ProxySettings = ProxySettings(),
    val logins: List<SiteLogin> = emptyList()
)

/**
 * Proxy and saved site logins (settings folder, network.json). Every HTTP client of the app is
 * built through [NetworkClient] and [useNetworkSettings], so a change applies to downloads
 * started afterwards; yt-dlp gets [ytDlpArgs], libtorrent its proxy settings.
 */
object NetworkSettings {
    private val file get() = "${PathResolver().getSettingsDirectory()}/network.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val loaded = load()
    private val _proxy = MutableStateFlow(loaded.proxy)
    val proxy: StateFlow<ProxySettings> = _proxy.asStateFlow()

    private val _logins = MutableStateFlow(loaded.logins)
    val logins: StateFlow<List<SiteLogin>> = _logins.asStateFlow()

    /** Bumped on every change; [NetworkClient] rebuilds its client when it moves. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun setProxy(p: ProxySettings) {
        _proxy.value = p.copy(host = p.host.trim(), user = p.user.trim()); save()
    }

    fun saveLogin(login: SiteLogin) {
        val site = SiteLogin.cleanSite(login.site)
        if (site.isEmpty()) return
        _logins.value = _logins.value.filterNot { SiteLogin.cleanSite(it.site) == site } +
                login.copy(site = site, user = login.user.trim())
        save()
    }

    fun removeLogin(site: String) {
        _logins.value = _logins.value.filterNot { it.site == site }; save()
    }

    fun loginFor(host: String): SiteLogin? =
        _logins.value.filter { it.matches(host) }.maxByOrNull { it.site.length }

    fun ktorProxy(): ProxyConfig? {
        val p = _proxy.value
        if (!p.isOn) return null
        return when (p.kind) {
            ProxyKind.SOCKS5 -> ProxyBuilder.socks(p.host, p.port)
            else -> ProxyBuilder.http(Url("http://${p.hostForUrl()}:${p.port}"))
        }
    }

    fun ytDlpArgs(): List<String> {
        val p = _proxy.value
        return if (p.isOn) listOf("--proxy", p.url()) else emptyList()
    }

    private fun load(): NetworkFile = try {
        if (!FileSystem.SYSTEM.exists(file)) NetworkFile()
        else json.decodeFromString(
            NetworkFile.serializer(),
            FileSystem.SYSTEM.read(file) { readUtf8() })
    } catch (_: Throwable) {
        NetworkFile()
    }

    private fun save() {
        _revision.value++
        try {
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) {
                writeUtf8(
                    json.encodeToString(
                        NetworkFile.serializer(),
                        NetworkFile(_proxy.value, _logins.value)
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun basicAuth(user: String, password: String): String =
    "Basic " + Base64.encode("$user:$password".encodeToByteArray())

private val SiteLogins = createClientPlugin("FetchJetSiteLogins") {
    onRequest { request, _ ->
        val login = NetworkSettings.loginFor(request.url.host) ?: return@onRequest
        if (request.headers[HttpHeaders.Authorization] == null) {
            request.headers.append(HttpHeaders.Authorization, basicAuth(login.user, login.password))
        }
    }
}

private val PlainHttpProxyLogin = createClientPlugin("FetchJetProxyLogin") {
    onRequest { request, _ ->
        val p = NetworkSettings.proxy.value
        if (p.isOn && p.kind == ProxyKind.HTTP && p.hasLogin &&
            request.url.protocol == URLProtocol.HTTP &&
            request.headers[HttpHeaders.ProxyAuthorization] == null
        ) {
            request.headers.append(HttpHeaders.ProxyAuthorization, basicAuth(p.user, p.password))
        }
    }
}

/** Proxy (with its login, through the platform engine) and the saved site logins. */
fun HttpClientConfig<*>.useNetworkSettings() {
    install(SiteLogins)
    install(PlainHttpProxyLogin)
    @Suppress("UNCHECKED_CAST")
    (this as HttpClientConfig<HttpClientEngineConfig>).engine {
        proxy = NetworkSettings.ktorProxy()
        applyProxyLogin(this, NetworkSettings.proxy.value)
    }
}

/** Platform part: answers the proxy's 407 / SOCKS login for HTTPS tunnels. */
internal expect fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings)

/** An HTTP client that is built again after the network settings change. */
class NetworkClient(private val build: () -> HttpClient) {
    private var builtAt = -1
    private var client: HttpClient? = null

    val current: HttpClient
        get() {
            val rev = NetworkSettings.revision.value
            client?.let { if (builtAt == rev) return it }
            return build().also { client = it; builtAt = rev }
        }
}
