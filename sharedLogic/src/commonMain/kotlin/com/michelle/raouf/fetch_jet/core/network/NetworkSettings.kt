package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings.ytDlpArgs
import com.michelle.raouf.fetch_jet.core.security.Secrets
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
import okio.Path.Companion.toPath
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
enum class DnsMode { SYSTEM, CLOUDFLARE, GOOGLE, QUAD9, ADGUARD, CUSTOM }

@Serializable
data class DnsSettings(val mode: DnsMode = DnsMode.SYSTEM, val custom: String = "") {
    val isOn: Boolean get() = mode != DnsMode.SYSTEM && (mode != DnsMode.CUSTOM || custom.isNotBlank())

    fun dohUrl(): String? = when (mode) {
        DnsMode.CLOUDFLARE -> "https://1.1.1.1/dns-query"
        DnsMode.GOOGLE -> "https://8.8.8.8/dns-query"
        DnsMode.QUAD9 -> "https://9.9.9.9/dns-query"
        DnsMode.ADGUARD -> "https://94.140.14.14/dns-query"
        DnsMode.CUSTOM -> custom.trim().takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
        DnsMode.SYSTEM -> null
    }

    fun servers(): List<String> =
        if (mode != DnsMode.CUSTOM || dohUrl() != null) emptyList()
        else custom.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
}

@Serializable
private data class NetworkFile(
    val proxy: ProxySettings = ProxySettings(),
    val logins: List<SiteLogin> = emptyList(),
    val dns: DnsSettings = DnsSettings(),
    val adapter: String = "",
)

internal expect fun localNetworkProxyPort(dns: DnsSettings, adapter: String): Int?

internal expect val ownDnsSupported: Boolean

/**
 * Proxy and saved site logins (settings folder, network.json; passwords encrypted by [Secrets]). Every HTTP client of the app is
 * built through [NetworkClient] and [useNetworkSettings], so a change applies to downloads
 * started afterwards; yt-dlp gets [ytDlpArgs], libtorrent its proxy settings.
 */
object NetworkSettings {
    private val file get() = "${PathResolver().getSettingsDirectory()}/network.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** Encrypted passwords this device cannot open (key gone): written back unchanged. */
    private var lockedProxyPassword: String? = null
    private val lockedLogins = mutableMapOf<String, String>()
    private var plainOnDisk = false

    private val loaded = load()
    private val _proxy = MutableStateFlow(loaded.proxy)
    val proxy: StateFlow<ProxySettings> = _proxy.asStateFlow()

    private val _logins = MutableStateFlow(loaded.logins)
    val logins: StateFlow<List<SiteLogin>> = _logins.asStateFlow()

    private val _dns = MutableStateFlow(loaded.dns)
    val dns: StateFlow<DnsSettings> = _dns.asStateFlow()

    private val _adapter = MutableStateFlow(loaded.adapter)
    val adapter: StateFlow<String> = _adapter.asStateFlow()

    val ownNetworkSupported: Boolean get() = ownDnsSupported

    fun setDns(value: DnsSettings) {
        val clean = value.copy(custom = value.custom.trim())
        if (clean == _dns.value) return
        _dns.value = clean; save()
    }

    fun setAdapter(value: String) {
        val clean = value.trim()
        if (clean == _adapter.value) return
        _adapter.value = clean; save()
    }

    fun activeProxy(): ProxySettings {
        val p = _proxy.value
        if (p.isOn) return p
        val port = runCatching { localNetworkProxyPort(_dns.value, _adapter.value) }.getOrNull() ?: return p
        return ProxySettings(ProxyKind.HTTP, "127.0.0.1", port)
    }

    /** Bumped on every change; [NetworkClient] rebuilds its client when it moves. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        // A network.json from before 1.2.0 has the passwords in plain text: encrypt them now.
        if (plainOnDisk) save()
    }

    fun setProxy(p: ProxySettings) {
        val old = _proxy.value
        if (p.password.isNotEmpty() || p.host.trim() != old.host || p.user.trim() != old.user) {
            lockedProxyPassword = null
        }
        _proxy.value = p.copy(host = p.host.trim(), user = p.user.trim()); save()
    }

    fun saveLogin(login: SiteLogin) {
        val site = SiteLogin.cleanSite(login.site)
        if (site.isEmpty()) return
        lockedLogins.remove(site)
        _logins.value = _logins.value.filterNot { SiteLogin.cleanSite(it.site) == site } +
                login.copy(site = site, user = login.user.trim())
        save()
    }

    fun removeLogin(site: String) {
        lockedLogins.remove(site)
        _logins.value = _logins.value.filterNot { it.site == site }; save()
    }

    fun loginFor(host: String): SiteLogin? =
        _logins.value.filter { it.matches(host) }.maxByOrNull { it.site.length }

    fun ktorProxy(): ProxyConfig? {
        val p = activeProxy()
        if (!p.isOn) return null
        return when (p.kind) {
            ProxyKind.SOCKS5 -> ProxyBuilder.socks(p.host, p.port)
            else -> ProxyBuilder.http(Url("http://${p.hostForUrl()}:${p.port}"))
        }
    }

    fun ytDlpArgs(): List<String> {
        val p = activeProxy()
        return if (p.isOn) listOf("--proxy", p.url()) else emptyList()
    }

    private fun load(): NetworkFile {
        val stored = try {
            if (!AppFileSystem.exists(file)) return NetworkFile()
            json.decodeFromString(
                NetworkFile.serializer(),
                AppFileSystem.read(file) { readUtf8() })
        } catch (_: Throwable) {
            return NetworkFile()
        }

        fun open(text: String, onLocked: () -> Unit): String {
            if (text.isNotEmpty() && !Secrets.isProtected(text)) plainOnDisk = true
            return Secrets.reveal(text) ?: "".also { onLocked() }
        }

        val proxy = stored.proxy.let { p ->
            p.copy(password = open(p.password) { lockedProxyPassword = p.password })
        }
        val logins = stored.logins.map { l ->
            l.copy(password = open(l.password) { lockedLogins[l.site] = l.password })
        }
        return NetworkFile(proxy, logins, stored.dns, stored.adapter)
    }

    private fun save() {
        _revision.value++
        val p = _proxy.value
        val onDisk = NetworkFile(
            p.copy(password = lockedProxyPassword?.takeIf { p.password.isEmpty() }
                ?: Secrets.protect(p.password)),
            _logins.value.map { l ->
                l.copy(password = lockedLogins[l.site]?.takeIf { l.password.isEmpty() }
                    ?: Secrets.protect(l.password))
            },
            _dns.value,
            _adapter.value,
        )
        try {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) {
                writeUtf8(json.encodeToString(NetworkFile.serializer(), onDisk))
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

/** Proxy (with its login, through the platform engine), the saved site logins, no Cookie across sites on a redirect. */
fun HttpClientConfig<*>.useNetworkSettings() {
    install(RedirectCookieGuard)
    install(SiteLogins)
    install(PlainHttpProxyLogin)
    @Suppress("UNCHECKED_CAST")
    (this as HttpClientConfig<HttpClientEngineConfig>).engine {
        proxy = NetworkSettings.ktorProxy()
        applyProxyLogin(this, NetworkSettings.activeProxy())
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
