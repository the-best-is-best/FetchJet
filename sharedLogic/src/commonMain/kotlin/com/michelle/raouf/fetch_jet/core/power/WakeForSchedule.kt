package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

expect object WakeTimer {
    val isSupported: Boolean
    fun arm(epochMs: Long): Boolean
    fun cancel()
    fun stayAwake()
}

const val WAKE_LEAD_MS = 60_000L

fun wakeTimeFor(scheduledStarts: Collection<Long>, nowMs: Long, leadMs: Long = WAKE_LEAD_MS): Long? {
    val start = scheduledStarts.filter { it > nowMs }.minOrNull() ?: return null
    val wake = start - leadMs
    return if (wake > nowMs) wake else start
}

object WakeForSchedule {
    private const val POLL_MS = 5_000L
    private const val AWAKE_TICK_MS = 20_000L
    private const val AWAKE_GRACE_MS = 180_000L

    private val _armedAtMs = MutableStateFlow(0L)
    val armedAtMs: StateFlow<Long> = _armedAtMs.asStateFlow()

    @Volatile
    var dryRun = false

    val isSupported: Boolean get() = WakeTimer.isSupported

    private var job: Job? = null
    private var awakeJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null || !isSupported) return
        val tasks = QueueManagerRepository.getInstance().allTasksFlow
        job = scope.launch {
            val starts = tasks.map { scheduledStarts(it) }.distinctUntilChanged()
            combine(BehaviorSettings.wakeForSchedule, starts) { on, times -> if (on) times else emptyList() }
                .distinctUntilChanged()
                .collectLatest { times ->
                    while (true) {
                        val at = wakeTimeFor(times, nowEpochMs())
                        rearm(at)
                        if (at == null) break
                        while (nowEpochMs() < at) delay((at - nowEpochMs()).coerceIn(1L, POLL_MS))
                        keepAwake(scope, tasks)
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        awakeJob?.cancel()
        awakeJob = null
        rearm(null)
    }

    private fun scheduledStarts(tasks: List<DownloadTask>): List<Long> =
        tasks.filter { it.waitingInQueue && it.scheduledAtMs > 0L }.map { it.scheduledAtMs }.sorted()

    private fun isActive(task: DownloadTask): Boolean = !task.waitingInQueue &&
        (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.CONNECTING || task.status == DownloadStatus.DOWNLOADING)

    private fun keepAwake(scope: CoroutineScope, tasks: StateFlow<List<DownloadTask>>) {
        if (awakeJob?.isActive == true) return
        awakeJob = scope.launch {
            val graceEnd = nowEpochMs() + AWAKE_GRACE_MS
            while (BehaviorSettings.wakeForSchedule.value && (nowEpochMs() < graceEnd || tasks.value.any { isActive(it) })) {
                if (!dryRun) WakeTimer.stayAwake()
                delay(AWAKE_TICK_MS)
            }
        }
    }

    private fun rearm(at: Long?) {
        val current = _armedAtMs.value
        if (at == null) {
            if (current == 0L) return
            if (!dryRun) WakeTimer.cancel()
            _armedAtMs.value = 0L
            println("WakeForSchedule: cancelled")
            return
        }
        if (at == current) return
        val armed = dryRun || WakeTimer.arm(at)
        _armedAtMs.value = if (armed) at else 0L
        println(if (armed) "WakeForSchedule: armed for $at" else "WakeForSchedule: could not arm the wake timer")
    }
}
