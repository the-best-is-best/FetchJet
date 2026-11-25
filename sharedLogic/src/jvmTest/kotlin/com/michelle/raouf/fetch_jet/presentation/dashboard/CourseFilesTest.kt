package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.fileTag
import com.michelle.raouf.fetch_jet.feature.media_extractor.isFile
import com.michelle.raouf.fetch_jet.feature.media_extractor.parseExternalPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.shownName
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseFilesTest {
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
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun course(): Map<String, Any?> = mapOf(
        "title" to "My Course",
        "entries" to listOf(
            mapOf("title" to "001 - Intro", "url" to "$base/intro.mp4"),
            mapOf("title" to "001 - slides.pdf", "url" to "$base/a?sig=1", "fileName" to "001 - slides.pdf"),
            mapOf("title" to "001 - code.zip", "url" to "$base/b?sig=2", "fileName" to "001 - code.zip"),
            mapOf("title" to "002 - Cheat sheet.pdf", "url" to "$base/c?sig=3", "fileName" to "002 - Cheat sheet.pdf"),
            mapOf("title" to "003 - notes", "url" to "$base/d?sig=4", "fileName" to "003 - notes"),
        )
    )

    @Test
    fun theBrowsersFileEntriesKeepTheirNamesAndOrder() {
        val list = assertNotNull(parseExternalPlaylist(course()))
        assertTrue(list.fromBrowser)
        assertEquals(
            listOf("001 - Intro.mp4", "001 - slides.pdf", "001 - code.zip", "002 - Cheat sheet.pdf", "003 - notes"),
            list.entries.map { it.shownName(list.fromBrowser) }
        )
        assertEquals(listOf(false, true, true, true, true), list.entries.map { it.isFile })
        assertEquals("PDF", list.entries[1].fileTag())
        assertEquals("ZIP", list.entries[2].fileTag())
        assertEquals(LocalizationManager.Strings.linkTagFile, list.entries[4].fileTag())
    }

    @Test
    fun courseFilesAreQueuedAsPlainDownloadsInTheCourseFolder() {
        val list = assertNotNull(parseExternalPlaylist(course()))
        vm.addPlaylist(vm.uiState.value.copy(destinationPath = work.absolutePath), list)
        runBlocking { withTimeout(30_000) { while (repo.allTasksFlow.value.size < 5) delay(50) } }
        val tasks = repo.allTasksFlow.value
        val names = tasks.map { it.fileName }.toSet()
        assertEquals(setOf("001 - Intro.mp4", "001 - slides.pdf", "001 - code.zip", "002 - Cheat sheet.pdf", "003 - notes"), names)
        tasks.forEach { t ->
            assertTrue(t.destinationPath.trimEnd('/', '\\').endsWith("My Course"), t.destinationPath)
            assertEquals(EngineType.HTTP_DIRECT, t.engineType, t.fileName)
        }
        assertFalse(tasks.any { it.fileName.endsWith(".pdf.mp4") || it.fileName == "003 - notes.mp4" })
    }
}
