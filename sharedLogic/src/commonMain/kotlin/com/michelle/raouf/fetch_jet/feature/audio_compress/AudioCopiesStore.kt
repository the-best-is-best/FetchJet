package com.michelle.raouf.fetch_jet.feature.audio_compress

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

object AudioCopiesStore {
    private val file get() = "${PathResolver().getSettingsDirectory()}/audio_copies.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val shape = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val saved = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private var loaded = false

    val counts: StateFlow<Map<String, List<String>>> get() = saved

    fun load() {
        if (loaded) return
        loaded = true
        val read = try {
            if (!AppFileSystem.exists(file)) emptyMap()
            else json.decodeFromString(shape, AppFileSystem.read(file) { readUtf8() })
        } catch (_: Throwable) {
            emptyMap()
        }
        saved.value = read
            .mapValues { (_, paths) -> paths.map(::relocateAppPath).filter { AppFileSystem.exists(it.toPath()) } }
            .filterValues { it.isNotEmpty() }
    }

    fun copies(taskId: String): List<CompressedCopy> {
        load()
        val known = saved.value[taskId].orEmpty()
        val found = known.mapNotNull { AudioCompress.copyOf(it) }
        if (found.size != known.size) {
            val paths = found.map { it.path }
            saved.update { if (paths.isEmpty()) it - taskId else it + (taskId to paths) }
            save()
        }
        return found
    }

    fun owns(taskId: String, path: String): Boolean {
        load()
        return path in saved.value[taskId].orEmpty()
    }

    fun add(taskId: String, path: String) {
        load()
        saved.update { it + (taskId to (it[taskId].orEmpty() - path + path)) }
        save()
    }

    fun remove(taskId: String, path: String) {
        load()
        saved.update { all ->
            val left = all[taskId].orEmpty() - path
            if (left.isEmpty()) all - taskId else all + (taskId to left)
        }
        save()
    }

    private fun save() {
        try {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(json.encodeToString(shape, saved.value)) }
        } catch (_: Throwable) {
        }
    }
}
