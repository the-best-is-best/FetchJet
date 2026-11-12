@file:OptIn(DelicateCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.findOnPath
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

object LinuxUserIdle {
    private class Source(val dest: String, val path: String, val method: String, val needsProof: Boolean)

    private class Reading(val atMs: Long, val idleMs: Long)

    private const val REFRESH_MS = 5_000L
    private const val RETRY_MS = 60_000L
    private const val STALE_MS = 15_000L
    private const val MAX_FAILURES = 3

    private val mutter = Source(
        "org.gnome.Mutter.IdleMonitor", "/org/gnome/Mutter/IdleMonitor/Core",
        "org.gnome.Mutter.IdleMonitor.GetIdletime", needsProof = false
    )
    private val kdeScreenSaver = listOf("/org/freedesktop/ScreenSaver", "/ScreenSaver").map { path ->
        Source("org.freedesktop.ScreenSaver", path, "org.freedesktop.ScreenSaver.GetSessionIdleTime", needsProof = true)
    }
    private val number = Regex("""uint(?:32|64) (\d+)""")

    private val started = TimeSource.Monotonic.markNow()
    private val busy = AtomicInt(0)

    @Volatile
    private var reading: Reading? = null

    @Volatile
    private var nextAtMs = 0L

    private var working: Source? = null
    private var proven = false
    private var failures = 0

    private fun nowMs(): Long = started.elapsedNow().inWholeMilliseconds

    fun warmUp() {
        idleMs()
    }

    fun idleMs(): Long? {
        val now = nowMs()
        if (now >= nextAtMs && busy.compareAndSet(0, 1)) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    refresh()
                } catch (_: Throwable) {
                    nextAtMs = nowMs() + RETRY_MS
                } finally {
                    busy.value = 0
                }
            }
        }
        val last = reading ?: return null
        return if (now - last.atMs > STALE_MS) 0L else last.idleMs
    }

    private fun onKde(): Boolean =
        linuxGetEnv("XDG_CURRENT_DESKTOP").orEmpty().contains("KDE", ignoreCase = true) ||
            linuxGetEnv("KDE_FULL_SESSION") == "true"

    private suspend fun refresh() {
        if (DesktopOs.findOnPath("gdbus") == null) {
            reading = null
            nextAtMs = nowMs() + RETRY_MS
            return
        }
        val known = working
        if (known != null) {
            val value = ask(known)
            if (value != null) {
                failures = 0
                accept(known, value)
                nextAtMs = nowMs() + REFRESH_MS
                return
            }
            if (++failures < MAX_FAILURES) {
                nextAtMs = nowMs() + REFRESH_MS
                return
            }
            working = null
            proven = false
            failures = 0
        }
        val candidates = if (onKde()) listOf(mutter) + kdeScreenSaver else listOf(mutter)
        for (source in candidates) {
            val value = ask(source) ?: continue
            working = source
            accept(source, value)
            nextAtMs = nowMs() + REFRESH_MS
            return
        }
        reading = null
        nextAtMs = nowMs() + RETRY_MS
    }

    private fun accept(source: Source, value: Long) {
        if (source.needsProof && !proven) {
            if (value == 0L) {
                reading = null
                return
            }
            proven = true
        }
        reading = Reading(nowMs(), value)
    }

    private suspend fun ask(source: Source): Long? = try {
        val result = DesktopOs.runProcess(
            listOf(
                "gdbus", "call", "--session", "--timeout", "2", "--dest", source.dest,
                "--object-path", source.path, "--method", source.method
            ),
            timeoutMs = 3_000
        )
        if (result.timedOut || result.exitCode != 0) null
        else number.find(result.stdout)?.groupValues?.get(1)?.toLongOrNull()
    } catch (_: Throwable) {
        null
    }
}
