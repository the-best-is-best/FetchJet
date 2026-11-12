package com.michelle.raouf.fetch_jet.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.TimeSource

object Ipv4Preference {
    const val KEEP_MS: Long = 24L * 60L * 60L * 1000L

    var supported: Boolean = false
    var onAdded: (() -> Unit)? = null

    private val started = TimeSource.Monotonic.markNow()
    internal var now: () -> Long = { started.elapsedNow().inWholeMilliseconds }

    private val hosts = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun prefers(host: String): Boolean {
        val until = hosts.value[clean(host)] ?: return false
        return now() < until
    }

    fun canTry(host: String): Boolean = supported && isName(clean(host)) && !prefers(host)

    fun add(host: String) {
        val key = clean(host)
        if (!isName(key)) return
        val time = now()
        hosts.update { old -> old.filterValues { it > time } + (key to time + KEEP_MS) }
        onAdded?.invoke()
    }

    fun remove(host: String) {
        val key = clean(host)
        hosts.update { it - key }
    }

    fun clear() {
        hosts.value = emptyMap()
    }

    private fun clean(host: String): String = host.trim().lowercase().trimEnd('.')

    private fun isName(host: String): Boolean =
        host.isNotEmpty() && ':' !in host && host.any { it.isLetter() }
}
