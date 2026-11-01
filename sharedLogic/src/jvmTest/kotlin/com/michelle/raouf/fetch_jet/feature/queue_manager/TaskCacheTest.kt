package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskCacheTest {

    private val oldFormat =
        """[{"id":"task_1","url":"https://example.com/a \"b\".zip","fileName":"a.zip","destinationPath":"C:\\Users\\x\\Downloads","engineType":"HTTP_DIRECT","status":"ERROR","totalBytes":1048576,"downloadedBytes":512,"progress":0.5,"createdAtEpochMs":1793556675831,"formatId":"","originalUrl":"","clipStart":"","clipEnd":"","currentStepText":"","errorMessage":"HTTP 403: \"denied\"","disableUpload":false,"clipByCutting":false,"serverSupportsResume":"true","maxDownloadSpeedBytesPerSec":0,"maxUploadSpeedBytesPerSec":0,"hdrCookie":"a=1; b=2","hdrReferer":"https://example.com/","hdrUserAgent":"UA","torrentFiles":"","webSeeds":"","scheduledAtMs":0,"maxConnections":8},{"id":"task_2","url":"magnet:?xt=urn:btih:abc","fileName":"Sintel","destinationPath":"/sdcard/Download","engineType":"BITTORRENT","status":"DOWNLOADING","totalBytes":0,"downloadedBytes":0,"progress":0.0,"createdAtEpochMs":1,"formatId":"","originalUrl":"","clipStart":"","clipEnd":"","currentStepText":"","disableUpload":true,"clipByCutting":false,"serverSupportsResume":"","maxDownloadSpeedBytesPerSec":0,"maxUploadSpeedBytesPerSec":0,"hdrCookie":"","hdrReferer":"","hdrUserAgent":"","torrentFiles":"0:1:0;1:0:1","webSeeds":"https://a/ https://b/","scheduledAtMs":0,"maxConnections":0},{"id":"task_3","totalBytes":"not a number"},{"id":"task_4","url":"https://youtu.be/x","engineType":"MEDIA_STREAM","status":"QUEUED","formatId":"137+140","originalUrl":"https://www.youtube.com/watch?v=x","futureField":{"a":1}}]"""

    @Test
    fun readsTheFormatWrittenBy110() {
        var broken = 0
        val tasks = TaskCache.decode(oldFormat) { broken++ }.map { it.toTask("/default") }
        assertEquals(1, broken)
        assertEquals(listOf("task_1", "task_2", "task_4"), tasks.map { it.id })

        val failed = tasks[0]
        assertEquals("https://example.com/a \"b\".zip", failed.url)
        assertEquals("C:\\Users\\x\\Downloads", failed.destinationPath)
        assertEquals(DownloadStatus.ERROR, failed.status)
        assertEquals("HTTP 403: \"denied\"", failed.errorMessage)
        assertEquals(true, failed.serverSupportsResume)
        assertEquals("a=1; b=2", failed.requestHeaders["Cookie"])
        assertEquals(8, failed.maxConnections)
        assertEquals(0.5f, failed.progress)

        val torrent = tasks[1]
        assertEquals(EngineType.BITTORRENT, torrent.engineType)
        assertEquals(DownloadStatus.PAUSED, torrent.status)
        assertNull(torrent.serverSupportsResume)
        assertNull(torrent.errorMessage)
        assertEquals(listOf(true, false), torrent.torrentFiles.map { it.isSelected })
        assertEquals(1, torrent.torrentFiles[1].priority)
        assertEquals(listOf("https://a/", "https://b/"), torrent.webSeedUrls)

        val media = tasks[2]
        assertEquals("/default", media.destinationPath)
        assertEquals(DownloadStatus.QUEUED, media.status)
        assertEquals("137+140", media.formatId)
    }

    @Test
    fun writeThenReadKeepsEveryField() {
        val tasks = TaskCache.decode(oldFormat).map { it.toTask("/default") }
        val again = TaskCache.decode(TaskCache.encode(tasks)).map { it.toTask("/default") }
        assertEquals(tasks, again)
    }

    @Test
    fun readsARealCacheFile() {
        val path = System.getenv("FETCHJET_TASKS_FILE") ?: return
        val text = File(path).readText()
        var broken = 0
        val tasks = TaskCache.decode(text) { broken++ }
        assertEquals(0, broken)
        assertEquals(Regex("\"id\":").findAll(text).count(), tasks.size)
        val restored = tasks.map { it.toTask("/default") }
        assertEquals(
            restored,
            TaskCache.decode(TaskCache.encode(restored)).map { it.toTask("/default") })
        println("Real cache: ${tasks.size} tasks read")
    }
}
