package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.feature.hidden.newStoreLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

data class SubtitleFile(val path: String, val label: String)

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

sealed interface SubtitleChoice {
    data object Off : SubtitleChoice
    data class Track(val id: String) : SubtitleChoice
    data class File(val path: String) : SubtitleChoice

    fun encode(): String = when (this) {
        Off -> "off"
        is Track -> "track:$id"
        is File -> "file:$path"
    }

    companion object {
        fun decode(text: String): SubtitleChoice? = when {
            text == "off" -> Off
            text.startsWith("track:") -> Track(text.removePrefix("track:"))
            text.startsWith("file:") -> File(text.removePrefix("file:"))
            else -> null
        }
    }
}

object PlayerSubtitles {
    val EXTENSIONS: Set<String> = setOf("srt", "vtt", "ass", "ssa")

    fun appLanguage(): String = AppLanguage.current.value.substringBefore('-').substringBefore('_').lowercase()

    fun isSubtitleName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /**
     * Subtitle files next to [videoPath] whose name starts with the video's name: `movie.srt`,
     * `movie.en.srt`, `movie.ar.vtt`. Sorted with [preferredLanguage] first, then by name.
     */
    fun sideFiles(videoPath: String, preferredLanguage: String = ""): List<SubtitleFile> {
        if (videoPath.isBlank()) return emptyList()
        val video = videoPath.toPath()
        val dir = video.parent ?: return emptyList()
        val base = video.name.substringBeforeLast('.', video.name)
        val found = runCatching { AppFileSystem.list(dir) }.getOrDefault(emptyList()).filter { p ->
            val n = p.name
            isSubtitleName(n) && (n.substringBeforeLast('.') == base || n.startsWith("$base."))
        }
        val files = found.map { p ->
            val middle = p.name.substringBeforeLast('.').removePrefix(base).trimStart('.')
            SubtitleFile(p.toString(), middle.ifEmpty { p.name.substringAfterLast('.').uppercase() })
        }
        val lang = preferredLanguage.lowercase()
        return files.sortedWith(compareBy<SubtitleFile> { !matchesLanguage(it.label, lang) }.thenBy { it.label.lowercase() })
    }

    fun matchesLanguage(label: String, language: String): Boolean {
        if (language.isEmpty()) return false
        val l = label.lowercase()
        return l == language || l.startsWith("$language-") || l.startsWith("${language}_") || l.startsWith("$language.")
    }

    /**
     * What to show when a video opens: the saved choice, else a side file in [preferredLanguage],
     * else the first side file, else null (the player's own default for tracks inside the video).
     */
    fun initialChoice(taskId: String, sideFiles: List<SubtitleFile>, preferredLanguage: String, remember: Boolean = true): SubtitleChoice? {
        val saved = if (remember) savedChoice(taskId) else null
        if (saved != null) {
            if (saved is SubtitleChoice.File && !AppFileSystem.exists(saved.path.toPath())) return sideFiles.firstOrNull()?.let { SubtitleChoice.File(it.path) }
            return saved
        }
        val pick = sideFiles.firstOrNull { matchesLanguage(it.label, preferredLanguage.lowercase()) } ?: sideFiles.firstOrNull()
        return pick?.let { SubtitleChoice.File(it.path) }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val lock = newStoreLock()

    @kotlin.concurrent.Volatile
    internal var fileOverride: String? = null
    private var loaded = false
    private var saved: Map<String, String> = emptyMap()

    private val file get() = (fileOverride ?: "${PathResolver().getSettingsDirectory()}/player_subtitles.json").toPath()

    private fun load() {
        if (loaded) return
        loaded = true
        saved = try {
            if (AppFileSystem.exists(file)) json.decodeFromString(serializer, AppFileSystem.read(file) { readUtf8() }) else emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun save() {
        runCatching {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(json.encodeToString(serializer, saved)) }
        }
    }

    internal fun resetForTest(path: String?) = lock.withLock {
        fileOverride = path
        loaded = false
        saved = emptyMap()
    }

    fun savedChoice(taskId: String): SubtitleChoice? {
        if (taskId.isEmpty()) return null
        return lock.withLock {
            load()
            saved[taskId]?.let { SubtitleChoice.decode(it) }
        }
    }

    fun remember(taskId: String, choice: SubtitleChoice) {
        if (taskId.isEmpty()) return
        lock.withLock {
            load()
            val text = choice.encode()
            if (saved[taskId] != text) {
                saved = saved + (taskId to text)
                save()
            }
        }
    }

    fun forget(taskId: String) {
        lock.withLock {
            load()
            if (taskId in saved) {
                saved = saved - taskId
                save()
            }
        }
    }

    fun readCues(path: String): List<SubtitleCue> = runCatching {
        parseCues(AppFileSystem.read(path.toPath()) { readUtf8() }, path.substringAfterLast('.', "").lowercase())
    }.getOrDefault(emptyList())

    fun parseCues(text: String, extension: String): List<SubtitleCue> {
        val body = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return when (extension) {
            "ass", "ssa" -> parseAss(body)
            else -> parseSrtOrVtt(body)
        }.sortedBy { it.startMs }
    }

    fun cueAt(cues: List<SubtitleCue>, positionMs: Long): String {
        if (cues.isEmpty()) return ""
        var lo = 0
        var hi = cues.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (cues[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        if (found < 0) return ""
        val lines = mutableListOf<String>()
        var i = found
        while (i >= 0 && i >= found - 8) {
            val c = cues[i]
            if (positionMs in c.startMs until c.endMs) lines.add(0, c.text)
            i--
        }
        return lines.joinToString("\n")
    }

    private val timeLine = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")
    private val tags = Regex("""<[^>]+>""")
    private val assTags = Regex("""\{[^}]*\}""")

    private fun parseSrtOrVtt(body: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = body.split('\n')
        var i = 0
        while (i < lines.size) {
            val match = timeLine.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val start = parseClock(match.groupValues[1])
            val end = parseClock(match.groupValues[2])
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (timeLine.containsMatchIn(lines[i])) break
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[i])
                i++
            }
            val clean = cleanText(text.toString().replace(tags, ""))
            if (start != null && end != null && end > start && clean.isNotBlank()) cues.add(SubtitleCue(start, end, clean))
        }
        return cues
    }

    private fun parseAss(body: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var format = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (line in body.split('\n')) {
            val t = line.trim()
            if (t.startsWith("Format:", ignoreCase = true) && t.contains("Text", ignoreCase = true)) {
                format = t.substringAfter(':').split(',').map { it.trim().lowercase() }
                continue
            }
            if (!t.startsWith("Dialogue:", ignoreCase = true)) continue
            val parts = t.substringAfter(':').split(',', limit = format.size)
            if (parts.size < format.size) continue
            val start = parseClock(parts[format.indexOf("start").coerceAtLeast(0)].trim())
            val end = parseClock(parts[format.indexOf("end").coerceAtLeast(0)].trim())
            val raw = parts[format.indexOf("text").takeIf { it >= 0 } ?: parts.lastIndex]
            val clean = cleanText(raw.replace(assTags, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " "))
            if (start != null && end != null && end > start && clean.isNotBlank()) cues.add(SubtitleCue(start, end, clean))
        }
        return cues
    }

    private fun cleanText(text: String): String =
        text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()

    private fun parseClock(text: String): Long? {
        val parts = text.replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val h = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val m = parts[parts.size - 2].toLongOrNull() ?: return null
        val secParts = parts.last().split('.')
        val s = secParts[0].toLongOrNull() ?: return null
        val frac = secParts.getOrNull(1) ?: ""
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return ((h * 60 + m) * 60 + s) * 1000 + ms
    }
}
