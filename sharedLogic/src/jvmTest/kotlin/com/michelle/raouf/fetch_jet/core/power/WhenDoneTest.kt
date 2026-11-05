package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WhenDoneTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var exits = 0

    private fun task(id: String, status: DownloadStatus) =
        DownloadTask(
            id = id, url = "https://a/$id", fileName = id, destinationPath = "/tmp",
            engineType = EngineType.entries.first(), status = status
        )

    private fun start() {
        WhenDone.cancel()
        WhenDone.onTasks(emptyList(), scope)
        WhenDone.onExitApp = { exits++ }
    }

    @AfterTest
    fun tearDown() {
        WhenDone.cancel()
        scope.cancel()
    }

    @Test
    fun countdownStartsWhenLastDownloadCompletes() {
        start()
        WhenDone.choose(WhenDoneAction.EXIT)
        WhenDone.onTasks(
            listOf(
                task("a", DownloadStatus.DOWNLOADING),
                task("b", DownloadStatus.QUEUED)
            ), scope
        )
        WhenDone.onTasks(
            listOf(
                task("a", DownloadStatus.COMPLETED),
                task("b", DownloadStatus.DOWNLOADING)
            ), scope
        )
        assertNull(WhenDone.secondsLeft.value)
        WhenDone.onTasks(
            listOf(
                task("a", DownloadStatus.COMPLETED),
                task("b", DownloadStatus.COMPLETED)
            ), scope
        )
        Thread.sleep(200)
        assertEquals(WhenDone.COUNTDOWN_SECONDS, WhenDone.secondsLeft.value)
        WhenDone.runNow()
        assertEquals(1, exits)
        assertNull(WhenDone.secondsLeft.value)
        assertEquals(WhenDoneAction.NOTHING, WhenDone.action.value)
    }

    @Test
    fun pausingEverythingDoesNotTrigger() {
        start()
        WhenDone.choose(WhenDoneAction.SHUT_DOWN)
        WhenDone.onTasks(listOf(task("a", DownloadStatus.DOWNLOADING)), scope)
        WhenDone.onTasks(listOf(task("a", DownloadStatus.PAUSED)), scope)
        Thread.sleep(200)
        assertNull(WhenDone.secondsLeft.value)
        assertEquals(WhenDoneAction.SHUT_DOWN, WhenDone.action.value)
    }

    @Test
    fun nothingChosenDoesNotTrigger() {
        start()
        WhenDone.onTasks(listOf(task("a", DownloadStatus.DOWNLOADING)), scope)
        WhenDone.onTasks(listOf(task("a", DownloadStatus.COMPLETED)), scope)
        Thread.sleep(200)
        assertNull(WhenDone.secondsLeft.value)
    }

    @Test
    fun newDownloadStopsCountdownAndCancelClearsChoice() {
        start()
        WhenDone.choose(WhenDoneAction.SLEEP)
        WhenDone.onTasks(listOf(task("a", DownloadStatus.DOWNLOADING)), scope)
        WhenDone.onTasks(listOf(task("a", DownloadStatus.COMPLETED)), scope)
        Thread.sleep(200)
        assertNotNull(WhenDone.secondsLeft.value)
        WhenDone.onTasks(
            listOf(
                task("a", DownloadStatus.COMPLETED),
                task("b", DownloadStatus.CONNECTING)
            ), scope
        )
        assertNull(WhenDone.secondsLeft.value)
        assertEquals(WhenDoneAction.SLEEP, WhenDone.action.value)
        WhenDone.onTasks(
            listOf(
                task("a", DownloadStatus.COMPLETED),
                task("b", DownloadStatus.COMPLETED)
            ), scope
        )
        Thread.sleep(200)
        assertNotNull(WhenDone.secondsLeft.value)
        WhenDone.cancel()
        assertNull(WhenDone.secondsLeft.value)
        assertEquals(WhenDoneAction.NOTHING, WhenDone.action.value)
        assertEquals(0, exits)
    }
}
