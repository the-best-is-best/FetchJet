package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.feature.hidden.newStoreLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

object PlayerProgress {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val lock = newStoreLock()

    @kotlin.concurrent.Volatile
    internal var fileOverride: String? = null

    private var loaded = false
    private var last: Map<String, String> = emptyMap()

    private val file get() = (fileOverride ?: "${PathResolver().getSettingsDirectory()}/player_progress.json").toPath()

    private fun load() {
        if (loaded) return
        loaded = true
        last = try {
            if (AppFileSystem.exists(file)) json.decodeFromString(serializer, AppFileSystem.read(file) { readUtf8() }) else emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun save() {
        runCatching {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(json.encodeToString(serializer, last)) }
        }
    }

    internal fun resetForTest(path: String?) = lock.withLock {
        fileOverride = path
        loaded = false
        last = emptyMap()
    }

    fun lastPlayed(groupKey: String): String? {
        if (groupKey.isEmpty()) return null
        return lock.withLock {
            load()
            last[groupKey]
        }
    }

    fun remember(groupKey: String, taskId: String) {
        if (groupKey.isEmpty()) return
        lock.withLock {
            load()
            if (last[groupKey] != taskId) {
                last = last + (groupKey to taskId)
                save()
            }
        }
    }

    fun clear(groupKey: String) {
        if (groupKey.isEmpty()) return
        lock.withLock {
            load()
            if (groupKey in last) {
                last = last - groupKey
                save()
            }
        }
    }
}
