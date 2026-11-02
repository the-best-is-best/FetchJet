package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WhenDoneAction { NOTHING, EXIT, SLEEP, SHUT_DOWN }

/** Sleep / shut down the computer (desktop only). */
expect object SystemPower {
    val isSupported: Boolean
    fun sleep(): Result<Unit>
    fun shutDown(): Result<Unit>
}

/**
 * "When downloads finish": exit FetchJet, sleep or shut down the computer after the last
 * running download completes, with a countdown the user can cancel. Not saved: it is for this
 * session only, so a computer never shuts down by surprise on a later day.
 */
object WhenDone {
    const val COUNTDOWN_SECONDS = 60

    private val active =
        setOf(DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.DOWNLOADING)

    private val _action = MutableStateFlow(WhenDoneAction.NOTHING)
    val action: StateFlow<WhenDoneAction> = _action.asStateFlow()

    /** Seconds left before the action runs; null = no countdown. */
    private val _secondsLeft = MutableStateFlow<Int?>(null)
    val secondsLeft: StateFlow<Int?> = _secondsLeft.asStateFlow()

    /** The action the countdown is for (the choice itself is reset when it runs). */
    private val _pending = MutableStateFlow(WhenDoneAction.NOTHING)
    val pending: StateFlow<WhenDoneAction> = _pending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set by the desktop entry point: quits the app. */
    var onExitApp: (() -> Unit)? = null

    private var running = emptySet<String>()
    private var countdown: Job? = null

    fun choose(choice: WhenDoneAction) {
        _action.value = choice
        if (choice == WhenDoneAction.NOTHING) stopCountdown()
    }

    fun onTasks(tasks: List<DownloadTask>, scope: CoroutineScope) {
        val nowRunning = tasks.filter { it.status in active }.map { it.id }.toSet()
        val finished = running - nowRunning
        running = nowRunning
        if (nowRunning.isNotEmpty()) {
            stopCountdown()
            return
        }
        if (finished.isEmpty() || _action.value == WhenDoneAction.NOTHING || countdown != null) return
        if (tasks.none { it.id in finished && it.status == DownloadStatus.COMPLETED }) return
        _pending.value = _action.value
        ExternalDownloadBridge.showAndFocus()
        countdown = scope.launch {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                _secondsLeft.value = s
                delay(1000)
            }
            countdown = null
            runNow()
        }
    }

    fun cancel() {
        stopCountdown()
        _action.value = WhenDoneAction.NOTHING
    }

    fun runNow() {
        val todo = _pending.value
        stopCountdown()
        _action.value = WhenDoneAction.NOTHING
        val result = when (todo) {
            WhenDoneAction.NOTHING -> Result.success(Unit)
            WhenDoneAction.EXIT -> runCatching { onExitApp?.invoke() ?: Unit }
            WhenDoneAction.SLEEP -> SystemPower.sleep()
            WhenDoneAction.SHUT_DOWN -> SystemPower.shutDown()
        }
        result.onFailure { _error.value = it.message ?: it.toString() }
    }

    fun dismissError() {
        _error.value = null
    }

    private fun stopCountdown() {
        countdown?.cancel()
        countdown = null
        _secondsLeft.value = null
    }
}
