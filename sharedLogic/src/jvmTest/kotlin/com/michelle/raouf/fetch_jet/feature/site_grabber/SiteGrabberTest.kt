package com.michelle.raouf.fetch_jet.feature.site_grabber

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteGrabberTest {
    private val page = """
        <html><head>
        <base href="https://example.com/gallery/">
        <meta property="og:image" content="/covers/main.jpg">
        <meta property="og:title" content="not a file">
        </head><body>
        <a href="report.pdf">Report</a>
        <a href='../files/archive.zip'>Archive</a>
        <a href=notes.txt>Notes</a>
        <a href="/download.php?id=5">Get it</a>
        <a href="page2.html">More</a>
        <a href="https://other.org/page.html">Elsewhere</a>
        <a href="javascript:void(0)">x</a>
        <a href="mailto:a@b.c">mail</a>
        <img src="img/one.png" srcset="img/one-small.png 320w, img/one-big.png 1280w">
        <img data-src="lazy/two.webp">
        <video src="clips/movie.mp4" poster="clips/poster.jpg"></video>
        <div data-url="https://cdn.example.com/x/data.csv"></div>
        <p>plain text link https://example.com/text/inline.docx, and more</p>
        <a href="a.zip?x=1&amp;y=2">Entity</a>
        </body></html>
    """.trimIndent()

    @Test
    fun readsFilesFromEveryPlaceAPageCanHoldThem() {
        val scan = PageLinks.scan(page, "https://example.com/start")
        val files = scan.files.toSet()
        assertTrue("https://example.com/gallery/report.pdf" in files)
        assertTrue("https://example.com/files/archive.zip" in files)
        assertTrue("https://example.com/gallery/notes.txt" in files)
        assertTrue("https://example.com/covers/main.jpg" in files)
        assertTrue("https://example.com/gallery/img/one-big.png" in files)
        assertTrue("https://example.com/gallery/img/one-small.png" in files)
        assertTrue("https://example.com/gallery/lazy/two.webp" in files)
        assertTrue("https://example.com/gallery/clips/movie.mp4" in files)
        assertTrue("https://example.com/gallery/clips/poster.jpg" in files)
        assertTrue("https://cdn.example.com/x/data.csv" in files)
        assertTrue("https://example.com/text/inline.docx" in files)
        assertTrue("https://example.com/gallery/a.zip?x=1&y=2" in files)
        assertEquals(listOf("https://example.com/download.php?id=5"), scan.maybeFiles)
        assertTrue("https://example.com/gallery/page2.html" in scan.pages)
        assertTrue("https://other.org/page.html" in scan.pages)
        assertTrue(scan.pages.none { it.startsWith("javascript") || it.startsWith("mailto") })
    }

    @Test
    fun aPageBuiltByScriptIsNamed() {
        val spa = "<html><body><div id=\"root\"></div><script src=\"a.js\"></script><script src=\"b.js\"></script></body></html>"
        assertTrue(PageLinks.scan(spa, "https://example.com/").needsScript)
        assertFalse(PageLinks.scan(page, "https://example.com/").needsScript)
    }

    @Test
    fun namesAndSites() {
        assertEquals("a b.pdf", PageLinks.fileNameOf("https://x.com/dir/a%20b.pdf?x=1"))
        assertTrue(PageLinks.sameSite("https://www.example.com/a", "https://cdn.example.com/b"))
        assertFalse(PageLinks.sameSite("https://example.com/a", "https://example.org/b"))
        assertEquals("https://a.com/x/y.png", PageLinks.resolve("https://a.com/x/z/", "../y.png"))
    }

    @Test
    fun theKindsOfLinksEzooAdded() {
        val html = """<html><head><base href="https://cdn.example.com/files/">
            <meta property="og:video" content="https://v.example.com/clip.mp4">
            <meta name="twitter:image" content="https://v.example.com/card.png">
            <meta name="description" content="a page about things"></head>
            <picture><source srcset="pic-small.webp 480w, pic-big.webp 1600w"><img srcset="small.jpg 1x, big.jpg 2x"></picture>
            <video src=movie.webm></video>
            <a href=report.pdf>Report <a data-download-url="/get/zip9.zip">x</a>
            <button data-file="setup.exe">Get</button> <span data-video-src='/v/talk.mp4'>t</span>
            <div data-original-url="img/full.tiff"></div>
            <form action="send.php"></form> <input value="12">"""
        val scan = PageLinks.scan(html, "https://www.example.com/page")
        val files = scan.files.toSet()
        for (want in listOf(
            "https://cdn.example.com/files/report.pdf",
            "https://cdn.example.com/files/big.jpg",
            "https://cdn.example.com/files/small.jpg",
            "https://cdn.example.com/files/pic-small.webp",
            "https://cdn.example.com/files/pic-big.webp",
            "https://cdn.example.com/files/movie.webm",
            "https://cdn.example.com/get/zip9.zip",
            "https://cdn.example.com/files/setup.exe",
            "https://cdn.example.com/v/talk.mp4",
            "https://cdn.example.com/files/img/full.tiff",
            "https://v.example.com/clip.mp4",
            "https://v.example.com/card.png"
        )) assertTrue(want in files, "$want missing in $files")
        val all = scan.files + scan.pages + scan.maybeFiles
        assertTrue(all.none { it.contains("about") || it.endsWith("/12") || it.endsWith("send.php") }, "$all")
    }

    @Test
    fun aRefusedPageIsCountedNotRead() = runBlocking {
        val fake = Fake(mapOf(start to "<a href='a.zip'>a</a>"), refused = setOf(start))
        val result = SiteGrabber(fake).grab(start)
        assertEquals(1, result.refused)
        assertEquals(0, result.pagesRead)
    }

    @Test
    fun aStartPageWhoseLinksAreMadeByScriptsIsCounted() = runBlocking {
        val spa = "<html><body><div id='app'></div><a href='/'>home</a><script src='a.js'></script><script src='b.js'></script><script>boot()</script></body></html>"
        val result = SiteGrabber(Fake(mapOf(start to spa))).grab(start)
        assertEquals(1, result.needScript)
    }

    @Test
    fun anAddressWithNoFileNameOnTheLastLevelIsAsked() = runBlocking {
        val fake = Fake(
            mapOf(start to "<a href='/media/123'>v</a><a href='/about'>a</a><a href='/p.html'>p</a><a href='https://other.org/thing'>o</a><a href='a.zip'>z</a>"),
            probes = mapOf(
                "https://example.com/media/123" to FileProbe("talk.mp4", 9_000L, "video/mp4"),
                "https://example.com/about" to FileProbe(null, 100L, "text/html; charset=utf-8")
            )
        )
        val result = SiteGrabber(fake).grab(start, GrabOptions(depth = 1))
        assertEquals(setOf("a.zip", "talk.mp4"), result.files.map { it.name }.toSet())
        assertEquals("Video", result.files.first { it.name == "talk.mp4" }.folder)
        assertTrue("https://example.com/media/123" in fake.probed)
        assertFalse("https://example.com/p.html" in fake.probed)
        assertFalse("https://other.org/thing" in fake.probed)
        assertFalse(result.reachedLimit)
    }

    @Test
    fun guessedAddressesDoNotFillTheLimitOrTheCount() = runBlocking {
        val many = (1..80).joinToString("") { "<a href='/post/$it'>p</a>" }
        val fake = Fake(mapOf(start to many))
        val counts = ArrayList<Int>()
        val result = SiteGrabber(fake).grab(start, GrabOptions(depth = 1), onProgress = { counts.add(it.files) })
        assertFalse(result.reachedLimit)
        assertTrue(fake.probed.size <= 30, "${fake.probed.size}")
        assertTrue(counts.all { it == 0 }, "$counts")
    }

    private class Fake(
        val pages: Map<String, String>,
        val refused: Set<String> = emptySet(),
        val probes: Map<String, FileProbe> = emptyMap()
    ) : PageFetcher {
        val asked = ArrayList<String>()
        val probed = ArrayList<String>()
        override suspend fun page(url: String): FetchedPage {
            asked.add(url)
            if (url in refused) return FetchedPage(403, url, "text/html", "")
            val html = pages[url] ?: return FetchedPage(404, url, "text/html", "")
            return FetchedPage(200, url, "text/html; charset=utf-8", html)
        }

        override suspend fun probe(url: String): FileProbe? {
            synchronized(probed) { probed.add(url) }
            return probes[url]
        }
    }

    private val start = "https://example.com/start"

    @Test
    fun oneLevelReadsOnlyTheStartPage() = runBlocking {
        val fake = Fake(
            mapOf(
                start to "<a href='a.zip'>a</a><a href='/p2'>p</a>",
                "https://example.com/p2" to "<a href='b.zip'>b</a>"
            )
        )
        val result = SiteGrabber(fake).grab(start, GrabOptions(depth = 1))
        assertEquals(listOf(start), fake.asked)
        assertEquals(listOf("a.zip"), result.files.map { it.name })
        assertEquals("Archives", result.files.single().folder)
        assertEquals(1, result.pagesRead)
    }

    @Test
    fun twoLevelsFollowSameSitePagesAndCountRefusedOnes() = runBlocking {
        val fake = Fake(
            mapOf(
                start to "<a href='a.zip'>a</a><a href='/p2'>p</a><a href='/p3'>p</a><a href='https://other.org/x'>o</a>",
                "https://example.com/p2" to "<a href='b.pdf'>b</a><a href='/p4'>deeper</a>"
            ),
            refused = setOf("https://example.com/p3")
        )
        val result = SiteGrabber(fake).grab(start, GrabOptions(depth = 2))
        assertEquals(setOf("a.zip", "b.pdf"), result.files.map { it.name }.toSet())
        assertEquals(1, result.refused)
        assertEquals(2, result.pagesRead)
        assertFalse("https://example.com/p4" in fake.asked)
        assertFalse("https://other.org/x" in fake.asked)
    }

    @Test
    fun aLinkWithNoFileNameIsAskedFromTheServer() = runBlocking {
        val fake = Fake(
            mapOf(start to "<a href='/download.php?id=5'>get</a><a href='/download.php?id=6'>get</a><img src='/img?id=9'>"),
            probes = mapOf(
                "https://example.com/download.php?id=5" to FileProbe("manual.pdf", 2_000_000L, "application/pdf"),
                "https://example.com/download.php?id=6" to FileProbe(null, 10L, "text/html"),
                "https://example.com/img?id=9" to FileProbe("photo.jpg", 4096L, "image/jpeg")
            )
        )
        val result = SiteGrabber(fake).grab(start)
        val byName = result.files.associateBy { it.name }
        assertEquals(setOf("manual.pdf", "photo.jpg"), byName.keys)
        assertEquals(2_000_000L, byName.getValue("manual.pdf").size)
        assertEquals("Documents", byName.getValue("manual.pdf").folder)
        assertEquals("Images", byName.getValue("photo.jpg").folder)
    }

    @Test
    fun theFileLimitAndPageLimitAreReported() = runBlocking {
        val many = (1..50).joinToString("") { "<a href='f$it.zip'>f</a>" }
        val result = SiteGrabber(Fake(mapOf(start to many))).grab(start, GrabOptions(maxFiles = 10))
        assertEquals(10, result.files.size)
        assertTrue(result.reachedLimit)
    }

    @Test
    fun aChallengePageIsRefusedNotRead() = runBlocking {
        val challenge = "<html><title>Just a moment...</title><body>Checking... cloudflare</body></html>"
        val result = SiteGrabber(Fake(mapOf(start to challenge))).grab(start)
        assertEquals(1, result.refused)
        assertEquals(0, result.pagesRead)
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun aPageIsNamedByItsTitleOrItsPathNotItsHost() {
        assertEquals("Release 1.5.0", PageLinks.nameOf("https://github.com/the-best-is-best/FetchJet/releases/tag/v1.5.0", "Release 1.5.0 · the-best-is-best/FetchJet"))
        assertEquals("the-best-is-best", PageLinks.nameOf("https://github.com/the-best-is-best", "GitHub - the-best-is-best"))
        assertEquals("hello", PageLinks.nameOf("https://ftp.gnu.org/gnu/hello/", "Index of /gnu/hello"))
        assertEquals("example.com", PageLinks.nameOf("https://example.com/", ""))
        assertEquals("page3", PageLinks.nameOf("http://127.0.0.1:18200/site/page3.html", ""))
    }

    @Test
    fun theTitleOfThePageNamesTheGroup() = runBlocking {
        assertEquals("Index of /gnu/hello", PageLinks.titleOf("<html><head><TITLE>  Index of /gnu/hello  </TITLE></head></html>"))
        assertEquals("A & B", PageLinks.titleOf("<title>A &amp; B</title>"))
        assertEquals("", PageLinks.titleOf("<html></html>"))
        val result = SiteGrabber(Fake(mapOf(start to "<title>My files</title><a href='a.zip'>a</a>"))).grab(start)
        assertEquals("My files", result.title)
    }

    private class Scripted(val handler: (String) -> FetchedPage?, val probes: Map<String, FileProbe> = emptyMap(), val hold: CompletableDeferred<Unit>? = null) : PageFetcher {
        val asked = ArrayList<String>()
        override suspend fun page(url: String): FetchedPage {
            asked.add(url)
            return handler(url) ?: FetchedPage(404, url, "text/html", "")
        }

        override suspend fun probe(url: String): FileProbe? {
            hold?.await()
            return probes[url]
        }
    }

    private val repo = "https://github.com/the-best-is-best/FetchJet"
    private val fragment = GITHUB_RELEASE_FRAGMENT + "<a href=\"/the-best-is-best/FetchJet/issues\">Issues</a><a href=\"/the-best-is-best/FetchJet/releases/tag/v1.4.1\">Older</a>"

    @Test
    fun aGitHubReleasePageReadsOnlyItsAssetsFragment() = runBlocking {
        val fake = Scripted({ url -> if (url == "$repo/releases/expanded_assets/v1.5.0") FetchedPage(200, url, "text/html", fragment) else null })
        val result = SiteGrabber(fake).grab("$repo/releases/tag/v1.5.0", GrabOptions(depth = 3))
        assertEquals(listOf("$repo/releases/expanded_assets/v1.5.0"), fake.asked)
        val byName = result.files.associateBy { it.name }
        assertEquals(
            setOf("FetchJet-1.5.0-arm64.dmg", "FetchJet-1.5.0-arm64.dmg.sig", "FetchJet-1.5.0-setup.exe", "FetchJet-1.5.0.zip", "FetchJet-1.5.0.tar.gz"),
            byName.keys
        )
        assertEquals("$repo/releases/download/v1.5.0/FetchJet-1.5.0-arm64.dmg", byName.getValue("FetchJet-1.5.0-arm64.dmg").url)
        assertEquals("$repo/archive/refs/tags/v1.5.0.zip", byName.getValue("FetchJet-1.5.0.zip").url)
        assertEquals((28.5 * 1024 * 1024).toLong(), byName.getValue("FetchJet-1.5.0-arm64.dmg").size)
        assertEquals(96L, byName.getValue("FetchJet-1.5.0-arm64.dmg.sig").size)
        assertEquals(-1L, byName.getValue("FetchJet-1.5.0.zip").size)
        assertEquals("Programs", byName.getValue("FetchJet-1.5.0-setup.exe").folder)
        assertEquals("FetchJet v1.5.0", result.title)
        assertEquals(1, result.pagesRead)
    }

    @Test
    fun latestResolvesTheTagFromTheRedirect() = runBlocking {
        val fake = Scripted({ url ->
            when (url) {
                "$repo/releases/latest" -> FetchedPage(200, "$repo/releases/tag/v1.5.0", "text/html", "<a href='/the-best-is-best/FetchJet/issues'>x</a>")
                "$repo/releases/expanded_assets/v1.5.0" -> FetchedPage(200, url, "text/html", fragment)
                else -> null
            }
        })
        val result = SiteGrabber(fake).grab("$repo/releases/latest")
        assertEquals(listOf("$repo/releases/latest", "$repo/releases/expanded_assets/v1.5.0"), fake.asked)
        assertEquals(5, result.files.size)
    }

    @Test
    fun theReleaseListReadsAtMostTenReleases() = runBlocking {
        val tags = (1..14).map { "v1.$it" }
        val list = tags.joinToString("") { "<a href=\"/the-best-is-best/FetchJet/releases/tag/$it\">$it</a>" } + "<a href=\"/the-best-is-best/FetchJet/issues\">i</a>"
        val fake = Scripted({ url ->
            when {
                url == "$repo/releases" -> FetchedPage(200, url, "text/html", list)
                url.startsWith("$repo/releases/expanded_assets/") -> {
                    val tag = url.substringAfterLast('/')
                    FetchedPage(200, url, "text/html", "<a href=\"/the-best-is-best/FetchJet/releases/download/$tag/app-$tag.apk\">x</a>")
                }
                else -> null
            }
        })
        val result = SiteGrabber(fake).grab("$repo/releases")
        assertEquals(11, fake.asked.size)
        assertEquals(10, result.files.size)
        assertTrue(result.reachedLimit)
        assertEquals("app-v1.1.apk", result.files.first().name)
    }

    @Test
    fun aBareGitHubRepoPageIsNotAnAdapterPage() {
        assertTrue(GitHubReleasesAdapter.matches("$repo/releases"))
        assertTrue(GitHubReleasesAdapter.matches("$repo/releases/"))
        assertTrue(GitHubReleasesAdapter.matches("$repo/releases/latest"))
        assertTrue(GitHubReleasesAdapter.matches("$repo/releases/tag/v1.5.0?expanded=true"))
        assertFalse(GitHubReleasesAdapter.matches(repo))
        assertFalse(GitHubReleasesAdapter.matches("$repo/issues"))
        assertFalse(GitHubReleasesAdapter.matches("https://example.com/a/b/releases"))
    }

    @Test
    fun filesAreDeliveredWhileTheCrawlIsStillRunning() = runBlocking {
        val hold = CompletableDeferred<Unit>()
        val fake = Scripted(
            { url -> if (url == start) FetchedPage(200, url, "text/html", "<a href='a.zip'>a</a><a href='/download.php?id=5'>g</a>") else null },
            probes = mapOf("https://example.com/download.php?id=5" to FileProbe("late.pdf", 5L, "application/pdf")),
            hold = hold
        )
        val delivered = ArrayList<String>()
        val job = async { SiteGrabber(fake).grab(start, onFiles = { batch -> synchronized(delivered) { delivered.addAll(batch.map { it.name }) } }) }
        withTimeout(5000) { while (synchronized(delivered) { delivered.isEmpty() }) yield() }
        assertFalse(job.isCompleted)
        assertEquals(listOf("a.zip"), synchronized(delivered) { delivered.toList() })
        hold.complete(Unit)
        val result = job.await()
        assertEquals(listOf("a.zip", "late.pdf"), synchronized(delivered) { delivered.toList() })
        assertEquals(setOf("a.zip", "late.pdf"), result.files.map { it.name }.toSet())
    }

    @Test
    fun stoppingKeepsTheFilesFoundSoFar() = runBlocking {
        val hold = CompletableDeferred<Unit>()
        val fake = Scripted(
            { url -> if (url == start) FetchedPage(200, url, "text/html", "<a href='a.zip'>a</a><a href='b.pdf'>b</a><a href='/download.php?id=5'>g</a>") else null },
            hold = hold
        )
        val kept = ArrayList<GrabbedFile>()
        val job = async { SiteGrabber(fake).grab(start, onFiles = { batch -> synchronized(kept) { kept.addAll(batch) } }) }
        withTimeout(5000) { while (synchronized(kept) { kept.isEmpty() }) yield() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("a.zip", "b.pdf"), synchronized(kept) { kept.map { it.name } })
    }
}
