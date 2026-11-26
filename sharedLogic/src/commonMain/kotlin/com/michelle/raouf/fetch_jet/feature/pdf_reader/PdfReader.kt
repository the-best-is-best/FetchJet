package com.michelle.raouf.fetch_jet.feature.pdf_reader

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.hidden.newStoreLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

object PdfMode {
    const val ASK = "ask"
    const val ON = "on"
    const val OFF = "off"

    fun current(): String = BehaviorSettings.inAppPdf.value

    fun isOn(): Boolean = current() == ON

    fun asks(): Boolean = current() == ASK

    fun isOff(): Boolean = current() == OFF

    fun set(mode: String) = BehaviorSettings.setInAppPdf(mode)
}

object PdfReader {
    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 5f
    const val ZOOM_STEP = 1.25f

    fun isPdfName(name: String): Boolean = name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    fun canRead(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED && isPdfName(task.fileName)

    fun pathOf(task: DownloadTask): String = AudioCompress.sourcePath(task)

    fun titleOf(task: DownloadTask): String = task.fileName.substringBeforeLast('.')

    fun zoomIn(zoom: Float): Float = (zoom * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun zoomOut(zoom: Float): Float = (zoom / ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun clampPage(page: Int, pageCount: Int): Int = if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)

    fun pageLabel(page: Int, pageCount: Int): String = "${page + 1} / $pageCount"

    fun parsePage(text: String, pageCount: Int): Int? {
        val n = text.trim().toIntOrNull() ?: return null
        if (n < 1 || n > pageCount) return null
        return n - 1
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val lock = newStoreLock()

    @kotlin.concurrent.Volatile
    internal var fileOverride: String? = null
    private var loaded = false
    private var saved: Map<String, String> = emptyMap()

    private val file get() = (fileOverride ?: "${PathResolver().getSettingsDirectory()}/pdf_pages.json").toPath()

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

    fun lastPage(task: DownloadTask): Int {
        if (task.hidden) return 0
        return lock.withLock {
            load()
            saved[task.id]?.toIntOrNull() ?: 0
        }
    }

    fun rememberPage(task: DownloadTask, page: Int) {
        if (task.hidden || task.id.isEmpty()) return
        lock.withLock {
            load()
            val text = page.coerceAtLeast(0).toString()
            if (saved[task.id] != text) {
                saved = saved + (task.id to text)
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
}
