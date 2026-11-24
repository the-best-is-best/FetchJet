package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueHiddenTest {
    private lateinit var home: File
    private lateinit var work: File
    private lateinit var server: HttpServer
    private lateinit var repo: QueueManagerRepository
    private lateinit var storeFile: File
    private val repos = ArrayList<QueueManagerRepository>()
    private var oldHome: String? = null
    private var gate = CountDownLatch(0)
    private val payload = ByteArray(300_000) { (it % 251).toByte() }

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        work = File(home, "Work").also { it.mkdirs() }
        storeFile = File(home, "hidden-test.json")
        HiddenStore.resetForTest(storeFile.absolutePath)
        com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.mediaOnly = false
        assertTrue(HiddenStore.setPin("1234"))
        HiddenStore.acceptNotice()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { ex ->
            val slow = ex.requestURI.path.startsWith("/slow")
            ex.responseHeaders.add("Content-Type", "application/octet-stream")
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { out ->
                if (slow) {
                    out.write(payload, 0, 1000)
                    out.flush()
                    gate.await()
                    out.write(payload, 1000, payload.size - 1000)
                } else out.write(payload)
            }
        }
        server.start()
        repo = QueueManagerRepository()
        repos.add(repo)
    }

    @AfterTest
    fun tearDown() {
        gate.countDown()
        repos.forEach {
            it.scope.cancel()
            (it.httpEngine as? com.michelle.raouf.fetch_jet.feature.http_downloader.HttpChunkDownloader)?.scope?.cancel()
        }
        server.stop(0)
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun waitFor(what: String, check: () -> Boolean) = runBlocking {
        withTimeout(30_000) { while (!check()) delay(50) }
    }

    private fun task(id: String): DownloadTask? = repo.allTasksFlow.value.firstOrNull { it.id == id }

    private suspend fun add(name: String, path: String = "/f.bin", hidden: Boolean = false, playlist: String = "", dir: File = work): DownloadTask =
        repo.enqueueTask(
            url = base + path,
            customDestinationPath = dir.absolutePath,
            customFileName = name,
            forcedEngineType = EngineType.HTTP_DIRECT,
            sortByType = false,
            hidden = hidden,
            playlistId = playlist
        ).getOrThrow()

    private fun finished(id: String) {
        waitFor("completed") { task(id)?.status == DownloadStatus.COMPLETED }
    }

    private fun privateFile(id: String, name: String) = File(HiddenFiles.dirFor(repo.pathResolver, id), name)

    @Test
    fun hidingAFinishedFileMovesItAndShowingBringsItBack() = runBlocking {
        val id = add("a.bin").id
        finished(id)
        assertTrue(File(work, "a.bin").exists())

        val report = repo.hideTasks(listOf(id))
        assertEquals(1, report.moved)
        assertEquals(0, report.issues)
        waitFor("hidden") { task(id)?.hidden == true }
        assertFalse(File(work, "a.bin").exists())
        assertTrue(privateFile(id, "a.bin").exists())
        assertEquals(payload.size.toLong(), privateFile(id, "a.bin").length())
        assertTrue(HiddenFiles.isPrivate(repo.pathResolver, task(id)!!.destinationPath))
        assertEquals(work.absolutePath, HiddenStore.originOf(id))
        waitFor("not visible") { repo.visibleTasksFlow.value.none { it.id == id } }
        assertNotNull(repo.allTasksFlow.value.firstOrNull { it.id == id })

        val back = repo.unhideTasks(listOf(id))
        assertEquals(0, back.issues)
        assertTrue(File(work, "a.bin").exists())
        assertFalse(privateFile(id, "a.bin").exists())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
        waitFor("shown") { task(id)?.hidden == false }
        assertEquals(work.absolutePath, task(id)!!.destinationPath)
        assertNull(HiddenStore.originOf(id))
    }

    @Test
    fun aNameTakenMeanwhileGetsTheUsualSuffixOnUnhide() = runBlocking {
        val id = add("a.bin").id
        finished(id)
        repo.hideTasks(listOf(id))
        File(work, "a.bin").writeText("someone else")
        repo.unhideTasks(listOf(id))
        assertEquals("someone else", File(work, "a.bin").readText())
        assertEquals(payload.size.toLong(), File(work, "a (1).bin").length())
        waitFor("renamed") { task(id)?.fileName == "a (1).bin" && task(id)?.hidden == false }
    }

    @Test
    fun aRunningDownloadHiddenFinishesInsideThePrivateFolder() = runBlocking {
        gate = CountDownLatch(1)
        val id = add("s.bin", path = "/slow").id
        waitFor("running") { task(id)?.status.let { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.CONNECTING } }
        repo.hideTasks(listOf(id))
        waitFor("hidden") { task(id)?.hidden == true }
        gate.countDown()
        finished(id)
        assertFalse(File(work, "s.bin").exists())
        assertTrue(privateFile(id, "s.bin").exists())
        assertEquals(payload.size.toLong(), privateFile(id, "s.bin").length())
        assertEquals("s.bin", task(id)!!.fileName)
    }

    @Test
    fun aRunningDownloadShownAgainFinishesInItsOwnFolder() = runBlocking {
        val id = add("h.bin", hidden = true).id
        gate = CountDownLatch(1)
        val slow = add("r.bin", path = "/slow", hidden = true).id
        waitFor("running") { task(slow)?.status.let { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.CONNECTING } }
        repo.unhideTasks(listOf(slow))
        gate.countDown()
        finished(slow)
        finished(id)
        assertTrue(File(work, "r.bin").exists())
        assertFalse(privateFile(slow, "r.bin").exists())
        assertTrue(privateFile(id, "h.bin").exists())
    }

    @Test
    fun newDownloadsOfAHiddenPlaylistFolderOrAskedHiddenStartPrivate() = runBlocking {
        val asked = add("asked.bin", hidden = true).id
        HiddenStore.hidePlaylist("p1")
        val inList = add("list.bin", playlist = "p1").id
        val other = File(home, "Other").also { it.mkdirs() }
        HiddenStore.setOrigin("seed", other.absolutePath)
        HiddenStore.hide(listOf("seed"))
        HiddenStore.hideFolder(other.absolutePath)
        val inFolder = add("folder.bin", dir = other).id
        val plain = add("plain.bin").id
        for (id in listOf(asked, inList, inFolder, plain)) finished(id)
        assertTrue(privateFile(asked, "asked.bin").exists())
        assertTrue(privateFile(inList, "list.bin").exists())
        assertTrue(privateFile(inFolder, "folder.bin").exists())
        assertTrue(File(work, "plain.bin").exists())
        assertFalse(File(work, "asked.bin").exists())
        assertFalse(File(work, "list.bin").exists())
        assertFalse(File(other, "folder.bin").exists())
        assertEquals(other.absolutePath, HiddenStore.originOf(inFolder))
        assertEquals(work.absolutePath, HiddenStore.originOf(asked))
        waitFor("visible list") { repo.visibleTasksFlow.value.map { it.id } == listOf(plain) }
    }

    @Test
    fun withoutALockNothingStartsHidden() = runBlocking {
        HiddenStore.resetForTest(File(home, "nolock.json").absolutePath)
        val id = add("a.bin", hidden = true).id
        finished(id)
        assertTrue(File(work, "a.bin").exists())
        assertEquals(false, task(id)?.hidden)
    }

    @Test
    fun aHiddenTaskRemovedWithoutDeletingKeepsItsFileInTheOriginalFolder() = runBlocking {
        val id = add("k.bin", hidden = true).id
        finished(id)
        assertTrue(privateFile(id, "k.bin").exists())
        repo.removeTask(id, deleteFile = false)
        assertTrue(File(work, "k.bin").exists())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
        assertNull(HiddenStore.originOf(id))
        assertFalse(HiddenStore.state.value.ids.contains(id))
    }

    @Test
    fun aHiddenTaskDeletedWithItsFileLeavesNothingBehind() = runBlocking {
        val id = add("d.bin", hidden = true).id
        finished(id)
        repo.removeTask(id, deleteFile = true)
        assertFalse(File(work, "d.bin").exists())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
        assertNull(HiddenStore.originOf(id))
    }

    @Test
    fun theHiddenStateSurvivesARestartOfTheQueue() = runBlocking {
        val id = add("r.bin", hidden = true).id
        finished(id)
        val file = File(File(home, ".fetch_jet"), "tasks_cache.json")
        waitFor("saved") { file.exists() && file.readText().contains(id) }
        delay(1500)

        HiddenStore.resetForTest(storeFile.absolutePath)
        val again = QueueManagerRepository()
        repos.add(again)
        waitFor("restored") { again.allTasksFlow.value.any { it.id == id } }
        val restored = assertNotNull(again.allTasksFlow.value.firstOrNull { it.id == id })
        assertTrue(restored.hidden)
        assertTrue(HiddenFiles.isPrivate(again.pathResolver, restored.destinationPath))
        assertTrue(File(restored.destinationPath, "r.bin").exists())
        assertTrue(again.allTasksFlow.value.none { !it.hidden })
        assertEquals(work.absolutePath, HiddenStore.originOf(id))
    }

    @Test
    fun hidingAFolderGroupStoresTheRuleAndShowingTheLastTaskEndsIt() = runBlocking {
        val a = add("one.bin").id
        val b = add("two.bin").id
        finished(a)
        finished(b)
        repo.hideTasks(listOf(a, b))
        HiddenStore.hideFolder(work.absolutePath)
        assertTrue(folderHidden(work.absolutePath))
        repo.unhideTasks(listOf(a))
        assertTrue(folderHidden(work.absolutePath))
        repo.unhideTasks(listOf(b))
        assertFalse(folderHidden(work.absolutePath))
        assertTrue(File(work, "one.bin").exists() && File(work, "two.bin").exists())
    }

    private fun privateFiles(id: String): List<File> =
        File(HiddenFiles.dirFor(repo.pathResolver, id)).listFiles()?.toList().orEmpty()

    private fun storedFile(id: String): File? = privateFiles(id).singleOrNull { it.name.endsWith(".fjh") }

    private fun containsRun(haystack: ByteArray, needle: ByteArray): Boolean {
        val h = String(haystack, Charsets.ISO_8859_1)
        return h.contains(String(needle, Charsets.ISO_8859_1))
    }

    private fun assertEncryptedAndHidden(id: String, name: String) {
        val stored = assertNotNull(storedFile(id), "no .fjh file")
        assertTrue(Regex("[0-9a-f]{32}\\.fjh").matches(stored.name), stored.name)
        assertEquals(1, privateFiles(id).size, privateFiles(id).map { it.name }.toString())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id), name).exists())
        assertFalse(File(work, name).exists())
        assertFalse(containsRun(stored.readBytes(), payload.copyOfRange(1000, 1064)))
        val json = storeFile.readText()
        assertFalse(json.contains(name), "the real name is in hidden.json in the clear")
        assertTrue(HiddenStore.hasEncrypted(id))
        assertEquals(name, HiddenStore.encryptedInfo(id)!!.name)
    }

    @Test
    fun hidingAFinishedVideoAndAudioEncryptsThemAndShowingRestoresTheBytes() = runBlocking {
        for (name in listOf("v.mp4", "m.mp3")) {
            val id = add(name).id
            finished(id)
            val report = repo.hideTasks(listOf(id))
            assertEquals(1, report.moved)
            assertEquals(0, report.issues)
            waitFor("hidden") { task(id)?.hidden == true }
            assertEncryptedAndHidden(id, name)
            assertEquals(name, task(id)!!.fileName)

            val back = repo.unhideTasks(listOf(id))
            assertEquals(0, back.issues)
            assertContentEquals(payload, File(work, name).readBytes())
            assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
            assertFalse(HiddenStore.hasEncrypted(id))
            waitFor("shown") { task(id)?.hidden == false }
        }
    }

    @Test
    fun aRunningVideoHiddenFinishesEncrypted() = runBlocking {
        gate = CountDownLatch(1)
        val id = add("s.mkv", path = "/slow").id
        waitFor("running") { task(id)?.status.let { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.CONNECTING } }
        repo.hideTasks(listOf(id))
        waitFor("hidden") { task(id)?.hidden == true }
        gate.countDown()
        finished(id)
        waitFor("encrypted") { storedFile(id) != null && !File(HiddenFiles.dirFor(repo.pathResolver, id), "s.mkv").exists() }
        assertEncryptedAndHidden(id, "s.mkv")
        assertEquals(0, repo.unhideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(work, "s.mkv").readBytes())
    }

    @Test
    fun aVideoAddedHiddenIsEncryptedWhenItFinishes() = runBlocking {
        val id = add("n.webm", hidden = true).id
        finished(id)
        waitFor("encrypted") { storedFile(id) != null && !File(HiddenFiles.dirFor(repo.pathResolver, id), "n.webm").exists() }
        assertEncryptedAndHidden(id, "n.webm")
        assertEquals(0, repo.unhideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(work, "n.webm").readBytes())
    }

    @Test
    fun onlyVideoAndAudioCanBeHidden() = runBlocking {
        com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.mediaOnly = true
        val zip = add("z.zip").id
        val video = add("v.mkv").id
        finished(zip)
        finished(video)
        val report = repo.hideTasks(listOf(zip, video))
        assertEquals(1, report.moved)
        assertEquals(1, report.skipped)
        assertEquals(false, task(zip)?.hidden)
        assertTrue(File(work, "z.zip").exists())
        val only = repo.hideTasks(listOf(zip))
        assertEquals(0, only.moved)
        assertEquals(1, only.skipped)
    }

    @Test
    fun otherTypesAreMovedNotEncrypted() = runBlocking {
        val id = add("z.zip").id
        finished(id)
        assertEquals(0, repo.hideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(HiddenFiles.dirFor(repo.pathResolver, id), "z.zip").readBytes())
        assertNull(storedFile(id))
        assertFalse(HiddenStore.hasEncrypted(id))
        assertEquals(0, repo.unhideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(work, "z.zip").readBytes())
    }

    @Test
    fun withoutTheNoticeAVideoIsOnlyMovedAndIsEncryptedOnceItIsAccepted() = runBlocking {
        HiddenStore.resetForTest(File(home, "fresh-hidden.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        assertFalse(HiddenStore.noticeAccepted())
        val id = add("late.mp4").id
        finished(id)
        assertEquals(0, repo.hideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(HiddenFiles.dirFor(repo.pathResolver, id), "late.mp4").readBytes())
        assertNull(storedFile(id))
        HiddenStore.acceptNotice()
        repo.encryptHiddenPending()
        assertNotNull(storedFile(id))
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id), "late.mp4").exists())
        assertEquals(0, repo.unhideTasks(listOf(id)).issues)
        assertContentEquals(payload, File(work, "late.mp4").readBytes())
    }

    @Test
    fun theNoticeBlocksOnlyWhatMayBeAudioOrVideo() {
        fun t(id: String, name: String, hidden: Boolean = false, engine: EngineType = EngineType.HTTP_DIRECT) = DownloadTask(
            id = id, url = "http://x/$id", fileName = name, destinationPath = "/tmp", engineType = engine, hidden = hidden
        )
        val tasks = listOf(
            t("a", "a.mp4"), t("b", "b.zip"), t("c", ""), t("d", "d.mp3", hidden = true),
            t("e", "e.mp4", engine = EngineType.BITTORRENT), t("f", "f.pdf", engine = EngineType.MEDIA_STREAM)
        )
        val need = { ids: List<String>, accepted: Boolean -> com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.noticeRequired(tasks, ids, accepted) }
        assertTrue(need(listOf("a"), false))
        assertFalse(need(listOf("a"), true))
        assertFalse(need(listOf("b"), false))
        assertTrue(need(listOf("c"), false))
        assertFalse(need(listOf("d"), false))
        assertFalse(need(listOf("e"), false))
        assertTrue(need(listOf("f"), false))
        assertTrue(need(listOf("b", "a"), false))
    }

    @Test
    fun aDamagedEncryptedFileIsKeptAndReportedOnUnhide() = runBlocking {
        val id = add("bad.mp4").id
        finished(id)
        repo.hideTasks(listOf(id))
        val stored = assertNotNull(storedFile(id))
        val bytes = stored.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        stored.writeBytes(bytes)
        val report = repo.unhideTasks(listOf(id))
        assertEquals(1, report.failed)
        assertTrue(stored.exists())
        assertFalse(File(work, "bad.mp4").exists())
        assertTrue(work.listFiles()!!.none { it.name.endsWith(".fjpart") })
        assertTrue(HiddenStore.hasEncrypted(id))
        assertEquals(true, task(id)?.hidden)
    }

    @Test
    fun openPlayableGivesADecryptedCopyUnderTheRealNameAndCleanupRemovesIt() = runBlocking {
        val temp = File(home, "tmp-play").also { it.mkdirs() }
        com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.tempOverride = temp.absolutePath
        try {
            val id = add("play.mp4").id
            finished(id)
            repo.hideTasks(listOf(id))
            val steps = ArrayList<Float>()
            val path = assertNotNull(
                com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.openPlayable(id, "play.mp4", { steps.add(it) }, repo.pathResolver)
            )
            val copy = File(path)
            assertEquals("play.mp4", copy.name)
            assertTrue(copy.absolutePath.replace('\\', '/').contains("/hidden_play/"))
            assertContentEquals(payload, copy.readBytes())
            assertTrue(steps.isNotEmpty() && steps.last() == 1f)
            assertTrue(storedFile(id) != null)

            val zip = add("p.zip").id
            finished(zip)
            repo.hideTasks(listOf(zip))
            val plainPath = assertNotNull(
                com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.openPlayable(zip, "p.zip", pathResolver = repo.pathResolver)
            )
            assertTrue(HiddenFiles.isPrivate(repo.pathResolver, plainPath))

            com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.cleanup(repo.pathResolver)
            assertFalse(copy.exists())
            assertFalse(File(temp, "hidden_play").exists())
            assertTrue(File(plainPath).exists())
        } finally {
            com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia.tempOverride = null
        }
    }

    @Test
    fun removingAnEncryptedTaskKeepingTheFileDecryptsItIntoItsFolder() = runBlocking {
        val id = add("keep.mp4").id
        finished(id)
        repo.hideTasks(listOf(id))
        repo.removeTask(id, deleteFile = false)
        assertContentEquals(payload, File(work, "keep.mp4").readBytes())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
        assertFalse(HiddenStore.hasEncrypted(id))
    }

    @Test
    fun removingAnEncryptedTaskWithItsFileLeavesNothingBehind() = runBlocking {
        val id = add("gone.mp4").id
        finished(id)
        repo.hideTasks(listOf(id))
        repo.removeTask(id, deleteFile = true)
        assertFalse(File(work, "gone.mp4").exists())
        assertFalse(File(HiddenFiles.dirFor(repo.pathResolver, id)).exists())
        assertFalse(HiddenStore.hasEncrypted(id))
    }

    private fun folderHidden(folder: String) = HiddenStore.folderKeyOf(folder) in HiddenStore.state.value.folders

    private fun breakStore(): File {
        val blocker = File(home, "blocker.txt").also { it.writeText("x") }
        HiddenStore.fileOverride = File(blocker, "sub/hidden.json").absolutePath
        return blocker
    }

    private fun fixStore() {
        HiddenStore.fileOverride = storeFile.absolutePath
    }

    @Test
    fun aFailedStoreWriteLeavesTheFileInPlaceAndReportsIt() = runBlocking {
        val id = add("w.bin").id
        finished(id)
        breakStore()
        val report = repo.hideTasks(listOf(id))
        fixStore()
        assertEquals(1, report.failed)
        assertTrue(File(work, "w.bin").exists())
        assertFalse(HiddenStore.state.value.ids.contains(id))
        assertNull(HiddenStore.originOf(id))
        assertEquals(false, task(id)?.hidden)
    }

    @Test
    fun hidingCancelledWhileWaitingForTheLockClearsTheProgressAndTheOwnedIds() = runBlocking {
        val id = add("c.bin").id
        finished(id)
        repo.hideLock.lock()
        try {
            val job = async(Dispatchers.Default) { repo.hideTasks(listOf(id)) }
            waitFor("progress begun") { HiddenWork.state.value.active && id in HiddenWork.owned.value }
            job.cancel()
            job.join()
            assertFalse(HiddenWork.state.value.active)
            assertFalse(id in HiddenWork.owned.value)
        } finally {
            repo.hideLock.unlock()
        }
    }

    @Test
    fun theProgressStaysWhileOtherWorkRunsAfterACancelledOneFinishes() {
        HiddenWork.begin(3)
        HiddenWork.step()
        HiddenWork.finish(1)
        assertEquals(HiddenWorkState(done = 1, total = 2), HiddenWork.state.value.copy(fraction = 0f))
        HiddenWork.finish(1)
        assertFalse(HiddenWork.state.value.active)
    }

    @Test
    fun aDownloadAskedHiddenStaysVisibleWhenTheStoreCannotSave() = runBlocking {
        breakStore()
        val id = add("v.bin", hidden = true).id
        fixStore()
        finished(id)
        assertTrue(File(work, "v.bin").exists())
        assertEquals(false, task(id)?.hidden)
        assertFalse(HiddenStore.state.value.ids.contains(id))
        assertFalse(HiddenFiles.isPrivate(repo.pathResolver, task(id)!!.destinationPath))
    }

    @Test
    fun aDownloadThatFinishesWhileItIsBeingShownEndsInItsOwnFolder() = runBlocking {
        gate = CountDownLatch(1)
        val id = add("m.bin", path = "/slow", hidden = true).id
        waitFor("running") { task(id)?.status.let { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.CONNECTING } }
        val showing = async(Dispatchers.Default) { repo.unhideTasks(listOf(id)) }
        gate.countDown()
        showing.await()
        finished(id)
        waitFor("moved home") { File(work, "m.bin").exists() && !privateFile(id, "m.bin").exists() }
        waitFor("shown") { task(id)?.hidden == false }
        assertNull(HiddenStore.originOf(id))
    }

    @Test
    fun showingATaskThatWasDeletedMeanwhileDoesNotFailTheOthers() = runBlocking {
        val gone = add("x.bin", hidden = true).id
        val kept = add("y.bin", hidden = true).id
        finished(gone)
        finished(kept)
        repo.removeTask(gone, deleteFile = true)
        val report = repo.unhideTasks(listOf(gone, kept))
        assertEquals(0, report.failed)
        assertTrue(File(work, "y.bin").exists())
        waitFor("shown") { task(kept)?.hidden == false }
        assertFalse(HiddenStore.state.value.ids.contains(gone))
        assertNull(HiddenStore.originOf(gone))
    }
}

private val HiddenMoveReport.issues: Int get() = copiesLeft + failed
