package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/** Minutes since local midnight (0..1439), for the schedule ticker. */
expect fun localMinutesOfDay(): Int

@Serializable
data class SpeedWindow(
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val capBytes: Long = 1L * 1024 * 1024,
    val capUnit: String = "MB/s"
) {
    fun contains(nowMinutes: Int): Boolean {
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes >= startMinutes && nowMinutes < endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }
}

@Serializable
private data class ScheduleFile(
    val enabled: Boolean = false,
    val windows: List<SpeedWindow> = emptyList()
)

object SpeedSchedule {
    private val file get() = "${PathResolver().getSettingsDirectory()}/schedule.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val loaded = load()

    private val _enabled = MutableStateFlow(loaded.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _windows = MutableStateFlow(loaded.windows)
    val windows: StateFlow<List<SpeedWindow>> = _windows.asStateFlow()

    /** The cap imposed by the active window right now, or null when nothing is capping (disabled or outside every window). For the UI indicator. */
    private val _activeCap = MutableStateFlow<Long?>(null)
    val activeCap: StateFlow<Long?> = _activeCap.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var apply: ((Long) -> Unit)? = null
    private var lastApplied: Long? = null
    private var started = false

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        save()
        reevaluate(forced = true)
    }

    fun addWindow(window: SpeedWindow) {
        _windows.value = _windows.value + window
        save()
        reevaluate(forced = true)
    }

    fun updateWindow(index: Int, window: SpeedWindow) {
        _windows.value = _windows.value.toMutableList().also {
            if (index in it.indices) it[index] = window
        }
        save()
        reevaluate(forced = true)
    }

    fun removeWindow(index: Int) {
        _windows.value = _windows.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
        save()
        reevaluate(forced = true)
    }

    /** The limit the schedule wants right now: a window cap, 0 (unlimited) outside windows, or null when disabled (no control). */
    fun desiredLimit(nowMinutes: Int = localMinutesOfDay()): Long? {
        if (!_enabled.value) return null
        val window = _windows.value.firstOrNull { it.contains(nowMinutes) }
        return window?.capBytes ?: 0L
    }

    /**
     * Start the ticker on a process-lived scope. [apply] sets the global download limit; it is
     * called only when the schedule-driven value changes (a window boundary or a config change),
     * so a manual change between boundaries is kept. Call once; later calls are ignored.
     */
    fun start(apply: (Long) -> Unit) {
        if (started) return
        started = true
        this.apply = apply
        reevaluate(forced = true)
        scope.launch {
            while (true) {
                reevaluate(forced = false)
                delay(30_000)
            }
        }
    }

    private fun reevaluate(forced: Boolean) {
        val now = localMinutesOfDay()
        val desired = desiredLimit(now)
        _activeCap.value = if (_enabled.value) {
            _windows.value.firstOrNull { it.contains(now) }?.capBytes
        } else {
            null
        }
        if (desired == null) {
            // Schedule turned off: release the limit it was holding back to unlimited.
            if (lastApplied != null && lastApplied != 0L) apply?.invoke(0L)
            lastApplied = null
            return
        }
        if (forced || desired != lastApplied) {
            lastApplied = desired
            apply?.invoke(desired)
        }
    }

    private fun load(): ScheduleFile {
        return try {
            if (!FileSystem.SYSTEM.exists(file)) return ScheduleFile()
            val text = FileSystem.SYSTEM.read(file) { readUtf8() }
            if (text.isBlank()) ScheduleFile() else json.decodeFromString(
                ScheduleFile.serializer(),
                text
            )
        } catch (_: Throwable) {
            ScheduleFile()
        }
    }

    private fun save() {
        val onDisk = ScheduleFile(_enabled.value, _windows.value)
        try {
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) {
                writeUtf8(json.encodeToString(ScheduleFile.serializer(), onDisk))
            }
        } catch (_: Throwable) {
        }
    }
}
