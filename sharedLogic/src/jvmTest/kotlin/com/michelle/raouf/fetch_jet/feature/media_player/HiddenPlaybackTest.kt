package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMove
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenPlaybackTest {
    private lateinit var home: File
    private lateinit var temp: File
    private var oldHome: String? = null
    private val tasks = HashMap<String, DownloadTask>()
    private val resolver get() = PathResolver()

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        temp = File(home, "tmp-play").also { it.mkdirs() }
        HiddenMedia.tempOverride = temp.absolutePath
        HiddenStore.resetForTest(File(home, "hidden-test.json").absolutePath)
        assertTrue(HiddenStore.setPin("1234"))
        HiddenStore.acceptNotice()
        PlayerProgress.resetForTest(File(home, "player_progress.json").absolutePath)
        tasks.clear()
        HiddenPlayback.attach(resolver) { tasks[it] }
    }

    @AfterTest
    fun tearDown() {
        HiddenPlayback.releaseAll()
        HiddenPlayback.attach(PathResolver()) { null }
        HiddenMedia.tempOverride = null
        PlayerProgress.resetForTest(null)
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun task(id: String, name: String, playlist: String = "", hidden: Boolean = true, status: DownloadStatus = DownloadStatus.COMPLETED) =
        DownloadTask(
            id = id,
            url = "http://x/$name",
            fileName = name,
            destinationPath = "/origin",
            engineType = EngineType.HTTP_DIRECT,
            status = status,
            playlistId = playlist,
            hidden = hidden
        )

    private fun hiddenEncrypted(id: String, name: String, bytes: ByteArray): DownloadTask {
        val plain = File(home, name).also { it.writeBytes(bytes) }
        assertEquals(HiddenMove.MOVED, HiddenMedia.hideFile(resolver, id, plain.absolutePath.toPath(), name))
        return task(id, name).also { tasks[id] = it }
    }

    @Test
    fun hiddenTasksAreNotPlayableWithoutTheFlag() {
        val t = task("1", "a.mp4")
        assertFalse(MediaPlayback.canPlay(t))
        assertTrue(MediaPlayback.canPlay(t, allowHidden = true))
        assertFalse(MediaPlayback.canPlay(task("2", "a.zip"), allowHidden = true))
        assertFalse(MediaPlayback.canPlay(task("3", "a.mp4", status = DownloadStatus.PAUSED), allowHidden = true))
        assertTrue(MediaPlayback.canPlay(task("4", "a.mp4", hidden = false)))
    }

    @Test
    fun aHiddenQueueHoldsHiddenItemsWithNoPathAndAHashedKey() {
        val all = listOf(task("1", "a.mp4", "pl"), task("2", "b.zip", "pl"), task("3", "c.mp3", "pl"), task("4", "d.mp4", "other"))
        val q = MediaPlayback.queueFor(all[2], all, allowHidden = true)
        assertEquals(listOf("1", "3"), q.items.map { it.taskId })
        assertEquals(1, q.index)
        assertTrue(q.items.all { it.hidden && it.path.isEmpty() })
        assertTrue(q.items.all { HiddenPlayback.needsPrepare(it) })
        assertTrue(q.groupKey.startsWith("h:"))
        assertFalse(q.groupKey.contains("pl"))
        assertEquals(2 + 64, q.groupKey.length)
        assertEquals(MediaPlayback.hiddenGroupKey("pl"), q.groupKey)

        val normal = MediaPlayback.queueFor(all[2], all)
        assertEquals(listOf("3"), normal.items.map { it.taskId })
        assertEquals("pl", normal.groupKey)
        assertFalse(HiddenPlayback.needsPrepare(PlayerItem("x", "x", "/d/x.mp4", true)))
    }

    @Test
    fun aHiddenGroupResumesFromTheItemRememberedUnderTheHashedKey() {
        val folder = "dir:/secret/My Folder"
        val all = listOf(task("1", "a.mp4"), task("2", "b.mp4"), task("3", "c.mp4"))
        assertNull(MediaPlayback.queueForGroup(folder, all))
        val q = assertNotNull(MediaPlayback.queueForGroup(folder, all, allowHidden = true))
        assertEquals(0, q.index)
        PlayerProgress.remember(q.groupKey, "3")
        assertEquals(2, MediaPlayback.queueForGroup(folder, all, allowHidden = true)!!.index)
        val saved = File(home, "player_progress.json").readText()
        assertFalse(saved.contains("secret"))
        assertFalse(saved.contains("My Folder"))
        assertTrue(saved.contains(MediaPlayback.hiddenGroupKey(folder)))
        assertEquals(MediaPlayback.hiddenGroupKey(folder), MediaPlayback.hiddenGroupKey(MediaPlayback.hiddenGroupKey(folder)))
        assertEquals("", MediaPlayback.hiddenGroupKey(""))
    }

    @Test
    fun prepareDecryptsOneCopyAtATimeAndReleaseDeletesIt() = runBlocking {
        val a = ByteArray(300_000) { (it % 247).toByte() }
        val b = ByteArray(50_000) { (it % 13).toByte() }
        hiddenEncrypted("ta", "first.mp4", a)
        hiddenEncrypted("tb", "second.mp3", b)

        val steps = ArrayList<Float>()
        val pa = assertNotNull(HiddenPlayback.prepare("ta") { steps.add(it) })
        assertContentEquals(a, File(pa).readBytes())
        assertEquals("first.mp4", File(pa).name)
        assertTrue(HiddenMedia.isPlayCopy(pa, resolver))
        assertEquals(1f, steps.last())
        assertEquals(pa, HiddenPlayback.prepare("ta") {})

        val pb = assertNotNull(HiddenPlayback.prepare("tb") {})
        assertContentEquals(b, File(pb).readBytes())
        assertFalse(File(pa).exists())
        assertNull(HiddenPlayback.copyOf("ta"))

        HiddenPlayback.release("tb")
        assertFalse(File(pb).exists())
        assertFalse(File(pb).parentFile.exists())
        assertTrue(File(HiddenFiles.dirFor(resolver, "tb")).listFiles()!!.any { it.name.endsWith(".fjh") })
    }

    @Test
    fun releaseAllDeletesTheCopyAndAPlainHiddenFileIsNeverDeleted() = runBlocking {
        val a = ByteArray(10_000) { it.toByte() }
        hiddenEncrypted("ta", "first.mp4", a)
        val pa = assertNotNull(HiddenPlayback.prepare("ta") {})
        HiddenPlayback.releaseAll()
        assertFalse(File(pa).exists())
        assertNull(HiddenPlayback.copyOf("ta"))

        val dir = File(HiddenFiles.prepare(resolver, "plain"))
        val plain = File(dir, "clip.mp4").also { it.writeBytes(a) }
        tasks["plain"] = task("plain", "clip.mp4")
        val pp = assertNotNull(HiddenPlayback.prepare("plain") {})
        assertEquals(plain.canonicalPath, File(pp).canonicalPath)
        HiddenPlayback.releaseAll()
        assertTrue(plain.exists())
    }

    @Test
    fun prepareRefusesUnknownOrNotHiddenTasks() = runBlocking {
        assertNull(HiddenPlayback.prepare("nope") {})
        tasks["open"] = task("open", "x.mp4", hidden = false)
        assertNull(HiddenPlayback.prepare("open") {})
    }

    @Test
    fun cancellingPrepareDeletesThePartialCopyAndRethrows() = runBlocking {
        val big = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        hiddenEncrypted("big", "big.mp4", big)
        val started = CompletableDeferred<Unit>()
        val job = async(Dispatchers.Default) {
            HiddenPlayback.prepare("big") { f ->
                if (f > 0f && !started.isCompleted) {
                    started.complete(Unit)
                    Thread.sleep(500)
                }
            }
        }
        withTimeout(30_000) { started.await() }
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
        val left = File(temp, HiddenMedia.PLAY_FOLDER).walk().filter { it.isFile }.toList()
        assertTrue(left.isEmpty(), "left: $left")
        assertNull(HiddenPlayback.copyOf("big"))
    }
}
