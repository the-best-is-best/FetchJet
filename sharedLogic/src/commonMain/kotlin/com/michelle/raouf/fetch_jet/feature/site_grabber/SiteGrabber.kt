package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.core.settings.FileCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class FetchedPage(val status: Int, val finalUrl: String, val contentType: String, val html: String)

data class FileProbe(val name: String?, val size: Long, val contentType: String)

interface PageFetcher {
    suspend fun page(url: String): FetchedPage
    suspend fun probe(url: String): FileProbe?
}

data class GrabbedFile(
    val url: String,
    val name: String,
    val folder: String,
    val size: Long,
    val page: String
)

data class GrabOptions(
    val depth: Int = 1,
    val maxPages: Int = 60,
    val maxFiles: Int = 3000,
    val probeLimit: Int = 60,
    val parallel: Int = 4
)

data class GrabProgress(val pagesRead: Int, val pagesLeft: Int, val files: Int, val title: String = "")

data class GrabResult(
    val files: List<GrabbedFile>,
    val pagesRead: Int,
    val refused: Int,
    val needScript: Int,
    val reachedLimit: Boolean,
    val title: String = ""
)

const val PAGE_GROUP = "PAGE"

class SiteGrabber(
    private val fetcher: PageFetcher,
    private val adapters: List<SiteAdapter> = SiteAdapters.all
) {

    suspend fun grab(
        startUrl: String,
        options: GrabOptions = GrabOptions(),
        onProgress: (GrabProgress) -> Unit = {},
        onFiles: (List<GrabbedFile>) -> Unit = {}
    ): GrabResult {
        adapters.firstOrNull { it.matches(startUrl) }?.let { return it.grab(startUrl, fetcher, onProgress, onFiles) }
        val depth = options.depth.coerceIn(1, 4)
        val found = LinkedHashMap<String, GrabbedFile>()
        val toProbe = LinkedHashMap<String, String>()
        val visited = HashSet<String>()
        visited.add(startUrl)
        var level = listOf(startUrl)
        var pagesRead = 0
        var refused = 0
        var needScript = 0
        var reachedLimit = false
        var title = ""
        val parallel = options.parallel.coerceIn(1, 8)
        val gate = Semaphore(parallel)
        val fresh = ArrayList<GrabbedFile>()
        val lock = Mutex()

        fun record(key: String, file: GrabbedFile) {
            if (key in found) return
            found[key] = file
            fresh.add(file)
        }

        fun flush() {
            if (fresh.isEmpty()) return
            val batch = fresh.toList()
            fresh.clear()
            onFiles(batch)
        }

        var current = 1
        while (current <= depth && level.isNotEmpty()) {
            val room = options.maxPages - pagesRead
            if (room <= 0) {
                reachedLimit = true
                break
            }
            val batch = if (level.size > room) {
                reachedLimit = true
                level.take(room)
            } else level
            val next = ArrayList<String>()
            for (chunk in batch.chunked(parallel)) {
                val fetched = coroutineScope {
                    chunk.map { url ->
                        async {
                            gate.withPermit {
                                val page = runCatching { fetcher.page(url) }.getOrNull()
                                url to page
                            }
                        }
                    }.awaitAll()
                }
                for ((url, page) in fetched) {
                    if (page == null || page.status !in 200..299 || isChallenge(page)) {
                        refused++
                        continue
                    }
                    pagesRead++
                    val type = page.contentType.lowercase()
                    if (type.isNotBlank() && !type.contains("html") && !type.contains("xml") && !type.contains("text")) {
                        val name = PageLinks.fileNameOf(page.finalUrl).ifBlank { "file" }
                        record(page.finalUrl, GrabbedFile(page.finalUrl, name, FileCategory.categoryFor(name), -1L, url))
                        continue
                    }
                    val scan = PageLinks.scan(page.html, page.finalUrl)
                    if (title.isEmpty() && url == startUrl) title = PageLinks.titleOf(page.html)
                    if (scan.needsScript) needScript++
                    for (file in scan.files) {
                        if (found.size >= options.maxFiles) {
                            reachedLimit = true
                            break
                        }
                        val name = PageLinks.fileNameOf(file)
                        if (name.isBlank() || !hasExtension(name)) {
                            toProbe.addOnce(file, page.finalUrl)
                        } else {
                            record(file, GrabbedFile(file, name, FileCategory.categoryFor(name), -1L, page.finalUrl))
                        }
                    }
                    for (file in scan.maybeFiles) toProbe.addOnce(file, page.finalUrl)
                    if (current < depth) {
                        for (link in scan.pages) {
                            if (PageLinks.sameSite(startUrl, link) && visited.add(link)) next.add(link)
                        }
                    }
                }
                flush()
                onProgress(GrabProgress(pagesRead, next.size, found.size + toProbe.size, title))
            }
            level = next
            current++
        }

        val probes = toProbe.entries.filter { it.key !in found }.take(options.probeLimit)
        if (probes.size < toProbe.size) reachedLimit = true
        coroutineScope {
            probes.map { entry ->
                async {
                    val probe = gate.withPermit { runCatching { fetcher.probe(entry.key) }.getOrNull() }
                    if (probe != null) {
                        val type = probe.contentType.lowercase()
                        if (!type.contains("text/html") && !type.contains("application/xhtml")) {
                            val name = probe.name?.takeIf { it.isNotBlank() } ?: PageLinks.fileNameOf(entry.key).ifBlank { "file" }
                            lock.withLock {
                                record(entry.key, GrabbedFile(entry.key, name, FileCategory.categoryFor(name), probe.size, entry.value))
                                flush()
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        onProgress(GrabProgress(pagesRead, 0, found.size, title))
        return GrabResult(found.values.toList(), pagesRead, refused, needScript, reachedLimit, title)
    }

    private fun hasExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "")
        return ext.length in 2..8 && ext.all { it.isLetterOrDigit() }
    }

    private fun isChallenge(page: FetchedPage): Boolean {
        val head = page.html.take(4000).lowercase()
        return "just a moment..." in head && "cloudflare" in page.html.take(20000).lowercase() ||
                "cf-browser-verification" in head || "attention required" in head && "cloudflare" in head ||
                "g-recaptcha" in head && page.html.length < 6000 || "px-captcha" in head
    }
}

private fun <K, V> MutableMap<K, V>.addOnce(key: K, value: V) {
    if (key !in this) this[key] = value
}
