package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenCheck
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.queue_manager.HiddenMoveReport
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.sun.net.httpserver.HttpServer
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardHiddenTest {
    private lateinit var home: File
    private lateinit var work: File
    private lateinit var server: HttpServer
    private lateinit var repo: QueueManagerRepository
    private lateinit var vm: DashboardViewModel
    private var oldHome: String? = null
    private val payload = ByteArray(20_000) { (it % 251).toByte() }

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        work = File(home, "Work").also { it.mkdirs() }
        HiddenStore.resetForTest(File(home, "hidden-test.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        HiddenStore.acceptNotice()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { ex ->
            ex.responseHeaders.add("Content-Type", "application/octet-stream")
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.start()
        repo = QueueManagerRepository()
        LocalizationManager.start()
        vm = DashboardViewModel(repo)
    }

    @AfterTest
    fun tearDown() {
        runCatching { vm.deviceSync.stop() }
        vm.scope.cancel()
        repo.scope.cancel()
        (repo.httpEngine as? com.michelle.raouf.fetch_jet.feature.http_downloader.HttpChunkDownloader)?.scope?.cancel()
        server.stop(0)
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun waitFor(what: String, check: () -> Boolean) = runBlocking {
        withTimeout(30_000) { while (!check()) delay(50) }
    }

    private fun task(id: String): DownloadTask? = repo.allTasksFlow.value.firstOrNull { it.id == id }

    private suspend fun add(name: String, dir: File = work, playlist: String = ""): String {
        dir.mkdirs()
        val id = repo.enqueueTask(
            url = "$base/$name",
            customDestinationPath = dir.absolutePath,
            customFileName = name,
            forcedEngineType = EngineType.HTTP_DIRECT,
            sortByType = false,
            playlistId = playlist
        ).getOrThrow().id
        waitFor("completed") { task(id)?.status == DownloadStatus.COMPLETED }
        return id
    }

    private fun unlock() {
        vm.onEvent(DashboardUiEvent.OnHiddenUnlock("1234"))
        assertTrue(vm.uiState.value.hidden.unlocked)
    }

    @Test
    fun theStepsRunTasksThenPlaylistsThenFolders() {
        val steps = DashboardUiEvent.OnHideTargets(listOf("t"), listOf("p1", "p2", "p1"), listOf("/a", "/b")).steps()
        assertEquals(
            listOf(
                HideStep(HideStep.Kind.TASKS, ""),
                HideStep(HideStep.Kind.PLAYLIST, "p1"),
                HideStep(HideStep.Kind.PLAYLIST, "p2"),
                HideStep(HideStep.Kind.FOLDER, "/a"),
                HideStep(HideStep.Kind.FOLDER, "/b")
            ),
            steps
        )
        assertEquals(listOf(HideStep(HideStep.Kind.FOLDER, "/a")), DashboardUiEvent.OnHideTargets(folders = listOf("/a")).steps())
        assertTrue(DashboardUiEvent.OnHideTargets().steps().isEmpty())
    }

    @Test
    fun oneEventHidesTasksPlaylistsAndFoldersAndClosesTheOpenGroups() = runBlocking {
        val folder = File(work, "Docs")
        val single = add("one.bin")
        val inFolder = add("f1.bin", folder)
        val inList = add("l1.bin", playlist = "pl")
        val folderKey = HiddenStore.folderKeyOf(folder.absolutePath)
        vm._uiState.value = vm._uiState.value.copy(openFolder = folderKey, openPlaylistId = "pl")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(single), listOf("pl"), listOf(folderKey)))
        waitFor("all hidden") {
            listOf(single, inFolder, inList).all { task(it)?.hidden == true } &&
                    "pl" in HiddenStore.state.value.playlists && folderKey in HiddenStore.state.value.folders &&
                    vm.uiState.value.openPlaylistId == null && vm.uiState.value.openFolder == null
        }
        assertTrue(HiddenFiles.isPrivate(repo.pathResolver, task(single)!!.destinationPath))
        waitFor("done notice") { vm.uiState.value.hidden.notice == HiddenNotice.HIDDEN_DONE }
    }

    @Test
    fun theFirstTimeNoticeHoldsTheHideUntilItIsAccepted() = runBlocking {
        HiddenStore.resetForTest(File(home, "hidden-fresh.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        val id = add("clip.mp4")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
        assertTrue(vm.uiState.value.hidden.encryptNoticeNeeded)
        delay(300)
        assertFalse(task(id)!!.hidden)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        assertFalse(vm.uiState.value.hidden.encryptNoticeNeeded)
        waitFor("hidden after accept") { task(id)?.hidden == true }
    }

    @Test
    fun dismissingTheNoticeDropsThePendingHide() = runBlocking {
        HiddenStore.resetForTest(File(home, "hidden-fresh2.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        val id = add("clip.mp4")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
        assertTrue(vm.uiState.value.hidden.encryptNoticeNeeded)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeDismissed)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        delay(300)
        assertFalse(task(id)!!.hidden)
    }

    private fun freshStore(name: String) {
        HiddenStore.resetForTest(File(home, name).absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
    }

    @Test
    fun hiddenAddDownloadOpensTheDialogAtOnceWhenNoNoticeIsNeeded() {
        vm.onEvent(DashboardUiEvent.OnHiddenAddDownload)
        assertTrue(vm.uiState.value.addHidden)
        waitFor("dialog") { vm.uiState.value.isAddDialogVisible }
        assertFalse(vm.uiState.value.hidden.encryptNoticeNeeded)
    }

    @Test
    fun hiddenAddDownloadWaitsForTheNoticeThenOpensTheDialog() {
        freshStore("hidden-add1.json")
        vm.onEvent(DashboardUiEvent.OnHiddenAddDownload)
        assertTrue(vm.uiState.value.hidden.encryptNoticeNeeded)
        assertFalse(vm.uiState.value.addHidden)
        assertFalse(vm.uiState.value.isAddDialogVisible)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        waitFor("dialog after accept") { vm.uiState.value.addHidden && vm.uiState.value.isAddDialogVisible }
        assertFalse(vm.uiState.value.hidden.encryptNoticeNeeded)
    }

    @Test
    fun dismissingTheNoticeDropsThePendingAddDialog() = runBlocking {
        freshStore("hidden-add2.json")
        vm.onEvent(DashboardUiEvent.OnHiddenAddDownload)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeDismissed)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        delay(300)
        assertFalse(vm.uiState.value.addHidden)
        assertFalse(vm.uiState.value.isAddDialogVisible)
    }

    @Test
    fun hiddenAddDownloadDoesNothingWithoutALock() {
        HiddenStore.resetForTest(File(home, "hidden-add3.json").absolutePath)
        vm.onEvent(DashboardUiEvent.OnHiddenAddDownload)
        vm.onEvent(DashboardUiEvent.OnAddHiddenToggled(true))
        assertFalse(vm.uiState.value.addHidden)
        assertFalse(vm.uiState.value.isAddDialogVisible)
    }

    @Test
    fun hidingWithLockAfterLocksTheAreaWhenDone() = runBlocking {
        val id = add("lock.bin")
        unlock()
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id), lockAfter = true))
        waitFor("hidden and locked") { task(id)?.hidden == true && !vm.uiState.value.hidden.unlocked }
        assertEquals(HiddenNotice.HIDDEN_DONE, vm.uiState.value.hidden.notice)
    }

    @Test
    fun hidingWithoutLockAfterKeepsTheAreaOpen() = runBlocking {
        val id = add("open.bin")
        unlock()
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
        waitFor("hidden") { task(id)?.hidden == true }
        delay(300)
        assertTrue(vm.uiState.value.hidden.unlocked)
    }

    @Test
    fun lockAfterWaitsForTheNoticeThenHidesAndLocks() = runBlocking {
        freshStore("hidden-add4.json")
        val id = add("clip.mp4")
        unlock()
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id), lockAfter = true))
        assertTrue(vm.uiState.value.hidden.encryptNoticeNeeded)
        assertTrue(vm.uiState.value.hidden.unlocked)
        vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        waitFor("hidden and locked") { task(id)?.hidden == true && !vm.uiState.value.hidden.unlocked }
    }

    @Test
    fun theDoneNoticeIsSetOnceAndNeverOverAFailure() = runBlocking {
        val a = add("a1.bin")
        val b = add("b1.bin")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(a)))
        waitFor("done") { vm.uiState.value.hidden.notice == HiddenNotice.HIDDEN_DONE }
        vm.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        assertNull(vm.uiState.value.hidden.notice)
        vm.reportHiddenMove(HiddenMoveReport(failed = 1))
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(b)))
        waitFor("hidden") { task(b)?.hidden == true }
        delay(300)
        assertEquals(HiddenNotice.MOVE_FAILED, vm.uiState.value.hidden.notice)
    }

    @Test
    fun aDownloadAddedHiddenSetsTheDoneNotice() = runBlocking {
        vm._uiState.value = vm._uiState.value.copy(
            inputUrl = "$base/added.bin", detectedEngine = EngineType.HTTP_DIRECT,
            destinationPath = work.absolutePath, fileNameOverride = "added.bin", addHidden = true
        )
        vm.addDownload()
        waitFor("done") { vm.uiState.value.hidden.notice == HiddenNotice.HIDDEN_DONE }
        assertTrue(repo.allTasksFlow.value.any { it.fileName == "added.bin" && it.hidden })
    }

    @Test
    fun aDownloadAddedPlainSetsNoNotice() = runBlocking {
        vm._uiState.value = vm._uiState.value.copy(
            inputUrl = "$base/plain.bin", detectedEngine = EngineType.HTTP_DIRECT,
            destinationPath = work.absolutePath, fileNameOverride = "plain.bin"
        )
        vm.addDownload()
        waitFor("added") { repo.allTasksFlow.value.any { it.fileName == "plain.bin" } }
        delay(300)
        assertNull(vm.uiState.value.hidden.notice)
    }

    @Test
    fun aPinThatCannotBeSavedSetsTheFailureNoticeAndNoLock() {
        File(home, "x").writeText("blocked")
        HiddenStore.resetForTest(File(home, "x/hidden-add5.json").absolutePath)
        vm.onEvent(DashboardUiEvent.OnHiddenSetPin("1234"))
        assertEquals(HiddenNotice.MOVE_FAILED, vm.uiState.value.hidden.notice)
        assertFalse(HiddenStore.hasLock())
        assertFalse(vm.uiState.value.hidden.unlocked)
    }

    @Test
    fun aPatternIsSetUnlockedAndChangedBackToAPin() {
        HiddenStore.resetForTest(File(home, "hidden-pattern.json").absolutePath)
        vm.onEvent(DashboardUiEvent.OnHiddenSetPin("12", HiddenLockType.PATTERN))
        assertEquals(HiddenError.INVALID_PIN, vm.uiState.value.hidden.error)
        assertFalse(HiddenStore.hasLock())
        vm.onEvent(DashboardUiEvent.OnHiddenSetPin("15963", HiddenLockType.PATTERN))
        assertEquals(HiddenLockType.PATTERN, vm.uiState.value.hidden.lockType)
        assertTrue(vm.uiState.value.hidden.unlocked)
        vm.onEvent(DashboardUiEvent.OnHiddenLock)
        vm.onEvent(DashboardUiEvent.OnHiddenUnlock("36951"))
        assertEquals(HiddenError.WRONG_PIN, vm.uiState.value.hidden.error)
        assertEquals(LocalizationManager.Strings.hiddenWrongPattern.replace("{n}", "4"), hiddenWrongPinText(vm.uiState.value.hidden.triesLeft))
        vm.onEvent(DashboardUiEvent.OnHiddenUnlock("15963"))
        assertTrue(vm.uiState.value.hidden.unlocked)
        vm.onEvent(DashboardUiEvent.OnHiddenChangePin("15963", "2468"))
        assertEquals(HiddenLockType.PIN, vm.uiState.value.hidden.lockType)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("2468"))
    }

    @Test
    fun aPlainFileIsNotHiddenAndSaysSo() = runBlocking {
        HiddenStore.resetForTest(File(home, "hidden-fresh3.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.mediaOnly = true
        val id = add("doc.bin")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
        assertFalse(vm.uiState.value.hidden.encryptNoticeNeeded)
        waitFor("notice") { vm.uiState.value.hidden.notice == HiddenNotice.NOT_MEDIA }
        assertFalse(task(id)?.hidden == true)
    }

    @Test
    fun theNoticeIsSetByAReportAndClearedOnceShown() {
        vm.reportHiddenMove(HiddenMoveReport(moved = 2))
        assertNull(vm.uiState.value.hidden.notice)
        vm.reportHiddenMove(HiddenMoveReport(copiesLeft = 1))
        assertEquals(HiddenNotice.COPY_LEFT, vm.uiState.value.hidden.notice)
        vm.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        assertNull(vm.uiState.value.hidden.notice)
        vm.reportHiddenMove(HiddenMoveReport(copiesLeft = 1, failed = 1))
        assertEquals(HiddenNotice.MOVE_FAILED, vm.uiState.value.hidden.notice)
        vm.reportHiddenMove(HiddenMoveReport(moved = 1))
        assertEquals(HiddenNotice.MOVE_FAILED, vm.uiState.value.hidden.notice)
        vm.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        assertNull(vm.uiState.value.hidden.notice)
    }

    @Test
    fun aFileThatCannotBeOpenedSetsTheOpenNotice() = runBlocking {
        val id = add("gone.bin")
        vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
        waitFor("hidden") { task(id)?.hidden == true && HiddenFiles.isPrivate(repo.pathResolver, task(id)!!.destinationPath) }
        unlock()
        File(HiddenFiles.dirFor(repo.pathResolver, id), "gone.bin").delete()
        vm.onEvent(DashboardUiEvent.OnHiddenOpenFile(id))
        waitFor("notice") { vm.uiState.value.hidden.notice == HiddenNotice.OPEN_FAILED }
        vm.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        assertNull(vm.uiState.value.hidden.notice)
    }

    @Test
    fun aHiddenVideoPlaysInTheAppPlayerAndLockingClosesItAndDeletesTheCopy() = runBlocking {
        val temp = File(home, "tmp-play").also { it.mkdirs() }
        com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.tempOverride = temp.absolutePath
        val requests = com.michelle.raouf.fetch_jet.feature.media_player.PlayerRequests
        val playback = com.michelle.raouf.fetch_jet.feature.media_player.HiddenPlayback
        try {
            val id = add("clip.mp4")
            vm.onEvent(DashboardUiEvent.OnHideTargets(listOf(id)))
            waitFor("hidden") { task(id)?.hidden == true && HiddenFiles.isPrivate(repo.pathResolver, task(id)!!.destinationPath) }
            assertNull(vm.hiddenQueueFor(id))
            vm.onEvent(DashboardUiEvent.OnHiddenPlayClicked(id))
            assertNull(requests.queue.value)
            unlock()
            val queue = vm.hiddenQueueFor(id)!!
            assertTrue(queue.current.hidden && queue.current.path.isEmpty())
            vm.onEvent(DashboardUiEvent.OnHiddenPlayClicked(id))
            assertEquals(listOf(id), requests.queue.value?.items?.map { it.taskId })
            val path = playback.prepare(id) {}!!
            assertTrue(File(path).readBytes().contentEquals(payload))
            vm.onEvent(DashboardUiEvent.OnHiddenLock)
            assertNull(requests.queue.value)
            assertFalse(File(path).exists())
            assertNull(playback.prepare(id) {})
        } finally {
            requests.close()
            com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.tempOverride = null
        }
    }

    @Test
    fun theNoticeSurvivesLockingTheScreen() {
        unlock()
        vm.reportHiddenMove(HiddenMoveReport(failed = 1))
        vm.onEvent(DashboardUiEvent.OnHiddenLock)
        assertEquals(HiddenNotice.MOVE_FAILED, vm.uiState.value.hidden.notice)
    }

    @Test
    fun theTextsMatchWhatTheUisBuildToday() {
        assertEquals("Moving files… 2 of 5", workingText("Moving files… {n} of {total}", 1, 5))
        assertEquals("Moving files… 5 of 5", workingText("Moving files… {n} of {total}", 9, 5))
        assertEquals("Too many tries. Try again in 30 seconds", lockedText("Too many tries. Try again in {n} seconds", 30))
        assertEquals("Wrong PIN. 3 tries left", wrongPinText("Wrong PIN. {n} tries left", 3))
        assertEquals("Opening 40%", openingText("Opening...", 0.4f))
        assertEquals("Opening 100%", openingText("Opening…", 3f))
    }
}
