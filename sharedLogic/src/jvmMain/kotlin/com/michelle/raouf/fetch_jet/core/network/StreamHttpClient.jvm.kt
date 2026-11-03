package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import java.net.http.HttpClient as JdkHttpClient

/**
 * Desktop: the JDK HTTP client. googlevideo answered 403 to every ranged request made through
 * OkHttp (same URL, same headers, HTTP/1.1) while the very same request through
 * java.net.http got 206 — whatever the CDN keys on, this stack passes.
 */
actual fun createStreamHttpClient(): HttpClient {
    val proxy = NetworkSettings.proxy.value
    if (proxy.isOn && proxy.kind == ProxyKind.SOCKS5) return HttpClient(OkHttp) {
        engine { config { protocols(listOf(okhttp3.Protocol.HTTP_1_1)) } }
        streamDefaults()
    }
    return HttpClient(Java) {
        engine {
            protocolVersion = JdkHttpClient.Version.HTTP_1_1
        }
        streamDefaults()
    }
}

private fun io.ktor.client.HttpClientConfig<*>.streamDefaults() {
    install(HttpRedirect) { checkHttpMethod = false }
    install(HttpTimeout) {
        requestTimeoutMillis = null
        connectTimeoutMillis = 60_000L
        socketTimeoutMillis = 60_000L
    }
    useNetworkSettings()
}

actual fun rawProbeForDiagnostics(url: String, userAgent: String): String = try {
    val uri = java.net.URI.create(url)
    val proxies = java.net.ProxySelector.getDefault()?.select(uri)?.joinToString()
    val props = listOf(
        "https.proxyHost",
        "http.proxyHost",
        "java.net.useSystemProxies",
        "jdk.tls.client.protocols",
        "https.protocols"
    )
        .joinToString { "$it=${System.getProperty(it)}" }
    val c = JdkHttpClient.newBuilder().version(JdkHttpClient.Version.HTTP_1_1)
        .proxy(java.net.ProxySelector.of(null)).build()
    val r = java.net.http.HttpRequest.newBuilder(uri).header("Range", "bytes=0-0")
        .header("User-Agent", userAgent).GET().build()
    val resp = c.send(r, java.net.http.HttpResponse.BodyHandlers.ofString())
    val c2 = JdkHttpClient.newBuilder().version(JdkHttpClient.Version.HTTP_1_1).build()
    val resp2 = c2.send(r, java.net.http.HttpResponse.BodyHandlers.ofString())
    val curl = try {
        val pb = ProcessBuilder(
            "curl.exe",
            "-s",
            "-o",
            "NUL",
            "-w",
            "%{http_code}",
            "-r",
            "0-0",
            url
        ).redirectErrorStream(true)
        val pr = pb.start();
        val out = pr.inputStream.bufferedReader().readText().trim(); pr.waitFor(); out
    } catch (e: Throwable) {
        "n/a ($e)"
    }
    val tls = try {
        val sp = javax.net.ssl.SSLContext.getDefault().defaultSSLParameters
        "tls=${sp.protocols.joinToString("/")} ciphers=${sp.cipherSuites.size}"
    } catch (e: Throwable) {
        "tls? $e"
    }
    "raw(noProxy)=${resp.statusCode()} raw(default)=${resp2.statusCode()} curlFromApp=$curl proxySelector=[$proxies] $props " +
            "java=${System.getProperty("java.version")} vendor=${System.getProperty("java.vendor")} home=${
                System.getProperty(
                    "java.home"
                )
            } $tls"
} catch (e: Throwable) {
    "raw probe failed: $e"
}
