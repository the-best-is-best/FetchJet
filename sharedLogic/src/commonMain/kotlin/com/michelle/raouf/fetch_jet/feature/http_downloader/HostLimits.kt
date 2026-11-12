package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.time.TimeSource

private val hostLimitsStart = TimeSource.Monotonic.markNow()

internal class HostLimits(
    private val nowMs: () -> Long = { hostLimitsStart.elapsedNow().inWholeMilliseconds }
) {
    private class Entry(val cap: Int, val learnedAtMs: Long)

    @Volatile
    private var byHost: Map<String, Entry> = emptyMap()

    fun capFor(host: String): Int? = live(host)?.cap

    fun learn(host: String, cap: Int) {
        if (host.isEmpty()) return
        val now = nowMs()
        val wanted = cap.coerceIn(1, ChunkLayout.MAX_CONNECTIONS)
        val known = live(host)?.cap
        byHost = byHost.filterValues { now - it.learnedAtMs < EXPIRE_MS } +
                (host to Entry(if (known == null) wanted else min(known, wanted), now))
    }

    fun climb(host: String, working: Int) {
        val known = live(host) ?: return
        if (working <= known.cap) return
        val raised = known.cap + 1
        byHost = if (raised >= ChunkLayout.MAX_CONNECTIONS) byHost - host
        else byHost + (host to Entry(raised, known.learnedAtMs))
    }

    fun clear() {
        byHost = emptyMap()
    }

    private fun live(host: String): Entry? {
        if (host.isEmpty()) return null
        val entry = byHost[host] ?: return null
        return entry.takeIf { nowMs() - it.learnedAtMs < EXPIRE_MS }
    }

    companion object {
        const val EXPIRE_MS = 24L * 60 * 60 * 1000

        val shared = HostLimits()

        fun hostOf(url: String): String =
            url.substringAfter("://", "").substringBefore('/').substringBefore('?')
                .substringBefore('#').substringAfterLast('@').lowercase()
    }
}
