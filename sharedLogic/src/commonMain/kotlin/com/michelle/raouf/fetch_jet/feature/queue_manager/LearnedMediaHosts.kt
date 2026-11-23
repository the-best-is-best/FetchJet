package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import okio.Path.Companion.toPath

object LearnedMediaHosts {
    private const val MAX_HOSTS = 500
    private val FILE_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "mp3", "m4a", "flac", "wav", "aac", "ogg", "opus",
        "zip", "rar", "7z", "iso", "exe", "msi", "apk", "pdf", "torrent"
    )

    @kotlin.concurrent.Volatile
    private var hosts: Set<String>? = null

    @kotlin.concurrent.Volatile
    internal var fileOverride: String? = null

    private val file get() = (fileOverride ?: "${PathResolver().getSettingsDirectory()}/learned_hosts.txt").toPath()

    internal fun resetForTest(path: String?) {
        fileOverride = path
        hosts = null
    }

    fun hostOf(url: String): String =
        url.trim().substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfterLast('@').substringBefore(':')
            .lowercase().trimEnd('.').removePrefix("www.")

    private fun current(): Set<String> {
        hosts?.let { return it }
        val loaded = try {
            if (AppFileSystem.exists(file)) {
                AppFileSystem.read(file) { readUtf8() }.lineSequence().map { it.trim() }
                    .filter { it.isNotEmpty() }.toSet()
            } else emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
        hosts = loaded
        return loaded
    }

    fun knows(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        if (h.isEmpty()) return false
        return current().any { h == it || h.endsWith(".$it") }
    }

    fun isLearnable(url: String): Boolean {
        val t = url.trim()
        if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return false
        val host = hostOf(t)
        if (host.isEmpty() || host == "localhost" || host.all { it.isDigit() || it == '.' } || ':' in host) return false
        val path = t.substringBefore('?').substringBefore('#').substringAfter("://").substringAfter('/', "")
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in FILE_EXTENSIONS || ext == "m3u8" || ext == "mpd") return false
        return true
    }

    fun learn(url: String): Boolean {
        if (!isLearnable(url)) return false
        val host = hostOf(url)
        if (knows(host)) return false
        val next = (current() + host).toList().takeLast(MAX_HOSTS).toSet()
        hosts = next
        try {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(next.joinToString("\n")) }
        } catch (_: Throwable) {
        }
        return true
    }

    fun forget(host: String) {
        val h = host.lowercase().removePrefix("www.")
        val next = current() - h
        if (next.size == current().size) return
        hosts = next
        try {
            AppFileSystem.write(file) { writeUtf8(next.joinToString("\n")) }
        } catch (_: Throwable) {
        }
    }
}
