package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.Ipv4Preference
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

internal suspend fun retryForbiddenOverIpv4(
    first: ResolvedHttpMetadata,
    again: suspend () -> ResolvedHttpMetadata
): ResolvedHttpMetadata {
    if (first.statusCode != 403 || NetworkSettings.proxy.value.isOn) return first
    val host = runCatching { Url(first.finalUrl).host }.getOrNull().orEmpty()
    if (!Ipv4Preference.canTry(host)) return first
    Ipv4Preference.add(host)
    val second = try {
        again()
    } catch (e: CancellationException) {
        Ipv4Preference.remove(host)
        throw e
    } catch (e: Exception) {
        null
    }
    val kept = second != null && second.statusCode in 200..299
    if (!kept) Ipv4Preference.remove(host)
    AppLog.d { "HTTP 403 from $host: asked again over IPv4 -> ${second?.statusCode ?: "no answer"}, IPv4 ${if (kept) "kept for this host" else "not kept"}" }
    return if (kept && second != null) second else first
}
