package com.michelle.raouf.fetch_jet.feature.playlist_group

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okio.Path
import okio.Path.Companion.toPath

/** A playlist added as one item: its videos are the tasks that carry the same id. */
@Serializable
data class PlaylistRecord(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val folder: String = "",
    val quality: String = "",
    val subtitleLanguages: List<String> = emptyList(),
    val embedSubtitles: Boolean = false,
    val subtitleFormat: String = "srt",
    val audioFormat: String = "",
    val createdAtMs: Long = 0L,
)

/** The saved playlists (`playlists.json` in the settings folder). */
object PlaylistStore {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private val _records = MutableStateFlow<Map<String, PlaylistRecord>>(emptyMap())
    val records: StateFlow<Map<String, PlaylistRecord>> = _records.asStateFlow()

    private var loaded = false
    private val file: Path by lazy { "${PathResolver().getSettingsDirectory()}/playlists.json".toPath() }

    fun load() {
        if (loaded) return
        loaded = true
        _records.value = try {
            if (!AppFileSystem.exists(file)) emptyMap()
            else decode(AppFileSystem.read(file) { readUtf8() }).associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun get(id: String): PlaylistRecord? {
        load()
        return _records.value[id]
    }

    /**
     * The saved playlist one of [links] points into: the same playlist link, or a video link that
     * carries the playlist's id (YouTube `list=`).
     */
    fun forLink(vararg links: String): PlaylistRecord? {
        load()
        fun listId(u: String) = Regex("""[?&]list=([\w-]+)""").find(u)?.groupValues?.get(1).orEmpty()
        val wanted = links.map { listId(it) }.filter { it.isNotEmpty() }.toSet()
        val exact = links.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty() && exact.isEmpty()) return null
        return _records.value.values.filter { r ->
            r.url.isNotBlank() && (r.url.trim() in exact || listId(r.url).let { it.isNotEmpty() && it in wanted })
        }.maxByOrNull { it.createdAtMs }
    }

    /** The saved playlist whose videos are saved in [folder], if any. */
    fun inFolder(folder: String): PlaylistRecord? {
        load()
        fun key(p: String) = p.replace('\\', '/').trimEnd('/').lowercase()
        val wanted = key(folder)
        if (wanted.isEmpty()) return null
        return _records.value.values.filter { it.folder.isNotBlank() && key(it.folder) == wanted }
            .maxByOrNull { it.createdAtMs }
    }

    fun put(record: PlaylistRecord) {
        load()
        _records.update { it + (record.id to record) }
        save()
    }

    fun update(id: String, change: (PlaylistRecord) -> PlaylistRecord) {
        load()
        val old = _records.value[id] ?: return
        put(change(old))
    }

    fun remove(ids: Collection<String>) {
        load()
        if (ids.none { it in _records.value }) return
        _records.update { it - ids.toSet() }
        save()
    }

    fun encode(records: Collection<PlaylistRecord>): String =
        json.encodeToString(ListSerializer(PlaylistRecord.serializer()), records.toList())

    fun decode(text: String): List<PlaylistRecord> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(PlaylistRecord.serializer(), element)
                    .takeIf { it.id.isNotBlank() }
                    ?.let { it.copy(folder = relocateAppPath(it.folder)) }
            } catch (_: Throwable) {
                null
            }
        }

    private fun save() {
        try {
            val text = encode(_records.value.values)
            val tmp = "$file.tmp".toPath()
            AppFileSystem.write(tmp) { writeUtf8(text) }
            AppFileSystem.atomicMove(tmp, file)
        } catch (_: Exception) {
        }
    }
}
