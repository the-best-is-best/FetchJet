package com.michelle.raouf.fetch_jet.feature.archive_extract

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveExtractorTest {
    private val work: File = Files.createTempDirectory("fj-extract").toFile()

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private fun resource(name: String): ByteArray =
        ArchiveExtractorTest::class.java.getResourceAsStream("/archives/$name")!!.use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun copyOf(name: String, as_: String = name): File {
        val file = File(work, as_)
        file.writeBytes(resource(name))
        return file
    }

    private fun extract(file: File, limits: ExtractLimits = ExtractLimits()) =
        runBlocking { ArchiveExtractor.extract(file.toOkioPath(), limits) }

    private fun zip(name: String, entries: List<Triple<String, ByteArray, Int>>): File {
        val file = File(work, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entryName, data, method) in entries) {
                val entry = JavaZipEntry(entryName)
                if (method == JavaZipEntry.STORED) {
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = CRC32().also { it.update(data) }.value
                }
                entry.method = method
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }
        }
        return file
    }

    private fun lorem(size: Int): ByteArray {
        val words = listOf("alpha", "beta", "gamma", "delta", "kappa", "omega", "sigma", "theta")
        val random = java.util.Random(11)
        val builder = StringBuilder()
        while (builder.length < size) builder.append(words[random.nextInt(words.size)]).append(' ')
        return builder.toString().toByteArray()
    }

    @Test
    fun kindsAndNames() {
        assertEquals(ArchiveKind.ZIP, ArchiveExtractor.kindOf("a.ZIP"))
        assertEquals(ArchiveKind.TAR_GZ, ArchiveExtractor.kindOf("a.tar.gz"))
        assertEquals(ArchiveKind.TAR_GZ, ArchiveExtractor.kindOf("a.tgz"))
        assertEquals(ArchiveKind.TAR_XZ, ArchiveExtractor.kindOf("a.tar.xz"))
        assertEquals(ArchiveKind.GZ, ArchiveExtractor.kindOf("a.txt.gz"))
        assertEquals(ArchiveKind.RAR, ArchiveExtractor.kindOf("a.rar"))
        assertEquals(ArchiveKind.SEVEN_Z, ArchiveExtractor.kindOf("a.7z"))
        assertEquals(ArchiveKind.SEVEN_Z, ArchiveExtractor.kindOf("big.7z.001"))
        assertNull(ArchiveExtractor.kindOf("big.7z.002"))
        assertEquals(ArchiveKind.RAR, ArchiveExtractor.kindOf("movie.part01.rar"))
        assertNull(ArchiveExtractor.kindOf("movie.part02.rar"))
        assertEquals(ArchiveKind.SPLIT_ZIP, ArchiveExtractor.kindOf("set.zip.001"))
        assertEquals("big", ArchiveExtractor.baseName("big.7z.003"))
        assertEquals("movie", ArchiveExtractor.baseName("movie.part2.rar"))
        assertEquals(ArchiveExtractor.volumeSet("movie.part1.rar"), ArchiveExtractor.volumeSet("Movie.part12.rar"))
        assertEquals(12, ArchiveExtractor.volumeNumber("movie.part12.rar"))
        assertNull(ArchiveExtractor.volumeSet("a.zip"))
        assertNull(ArchiveExtractor.kindOf("a.doc"))
        assertEquals("a", ArchiveExtractor.baseName("a.tar.gz"))
        assertEquals("data.txt", ArchiveExtractor.baseName("data.txt.xz"))
    }

    @Test
    fun zipWithSeveralThingsGoesIntoAFolderNamedAfterTheArchive() {
        val a = lorem(5000)
        val b = ByteArray(70000) { (it * 31).toByte() }
        val file = zip(
            "bundle.zip",
            listOf(
                Triple("one.txt", a, JavaZipEntry.DEFLATED),
                Triple("dir/two.bin", b, JavaZipEntry.DEFLATED),
                Triple("dir/stored.bin", b, JavaZipEntry.STORED),
                Triple("empty.txt", ByteArray(0), JavaZipEntry.DEFLATED),
                Triple("folder/", ByteArray(0), JavaZipEntry.STORED),
            ),
        )
        val result = extract(file)
        val folder = File(work, "bundle")
        assertEquals(folder.absolutePath, File(result.destination).absolutePath)
        assertEquals(4, result.files)
        assertContentEquals(a, File(folder, "one.txt").readBytes())
        assertContentEquals(b, File(folder, "dir/two.bin").readBytes())
        assertContentEquals(b, File(folder, "dir/stored.bin").readBytes())
        assertEquals(0, File(folder, "empty.txt").length())
        assertTrue(File(folder, "folder").isDirectory)
        assertFalse(work.listFiles()!!.any { it.name.contains("fetchjet-extract") })
    }

    @Test
    fun theChosenPlaceIsUsed() {
        val single = zip("one-top.zip", listOf(Triple("proj/a.txt", lorem(100), 8)))
        val always = runBlocking { ArchiveExtractor.extract(single.toOkioPath(), place = ExtractPlace.ALWAYS_FOLDER) }
        assertEquals(File(work, "one-top").absolutePath, File(always.destination).absolutePath)
        assertTrue(File(work, "one-top/proj/a.txt").isFile)

        val loose = zip("loose.zip", listOf(Triple("x.txt", lorem(50), 8), Triple("y.txt", lorem(50), 8)))
        val next = runBlocking { ArchiveExtractor.extract(loose.toOkioPath(), place = ExtractPlace.NEXT_TO_ARCHIVE) }
        assertEquals(work.absolutePath, File(next.destination).absolutePath)
        assertTrue(File(work, "x.txt").isFile && File(work, "y.txt").isFile)
        assertEquals(setOf("x.txt", "y.txt"), next.items.map { File(it).name }.toSet())

        val again = runBlocking { ArchiveExtractor.extract(loose.toOkioPath(), place = ExtractPlace.NEXT_TO_ARCHIVE) }
        assertEquals(setOf("x (1).txt", "y (1).txt"), again.items.map { File(it).name }.toSet())
        assertFalse(work.listFiles()!!.any { it.name.contains("fetchjet-extract") })
    }

    @Test
    fun zipWithOneTopFolderIsPlacedNextToTheArchive() {
        val file = zip("single.zip", listOf(Triple("proj/a.txt", lorem(100), 8), Triple("proj/b/c.txt", lorem(100), 8)))
        val result = extract(file)
        assertEquals(File(work, "proj").absolutePath, File(result.destination).absolutePath)
        assertTrue(File(work, "proj/b/c.txt").isFile)
    }

    @Test
    fun anExistingNameIsKeptAndTheNewOneIsNumbered() {
        val file = zip("bundle.zip", listOf(Triple("one.txt", lorem(10), 8), Triple("two.txt", lorem(10), 8)))
        extract(file)
        val second = extract(file)
        assertEquals(File(work, "bundle (1)").absolutePath, File(second.destination).absolutePath)
        assertTrue(File(work, "bundle/one.txt").isFile)
        assertTrue(File(work, "bundle (1)/one.txt").isFile)
    }

    @Test
    fun zipSlipEntriesAreSkippedAndNothingLeavesTheFolder() {
        val file = zip(
            "evil.zip",
            listOf(
                Triple("../evil.txt", lorem(10), 8),
                Triple("/abs.txt", lorem(10), 8),
                Triple("a/../../up.txt", lorem(10), 8),
                Triple("C:\\win.txt", lorem(10), 8),
                Triple("..\\back.txt", lorem(10), 8),
                Triple("good.txt", lorem(10), 8),
                Triple("good2.txt", lorem(10), 8),
            ),
        )
        val result = extract(file)
        assertEquals(2, result.files)
        assertEquals(5, result.skipped.size)
        assertFalse(File(work, "evil.txt").exists())
        assertFalse(File(work.parentFile, "evil.txt").exists())
        assertFalse(File(work.parentFile, "up.txt").exists())
        assertTrue(File(work, "evil/good.txt").isFile)
    }

    @Test
    fun aZipBombIsStopped() {
        val zeros = ByteArray(20 * 1024 * 1024)
        val file = zip("bomb.zip", listOf(Triple("zeros.bin", zeros, 8), Triple("zeros2.bin", zeros, 8)))
        val failure = assertFailsWith<ArchiveException> {
            extract(file, ExtractLimits(ratioGuardFrom = 1024 * 1024, maxRatio = 100))
        }
        assertTrue(failure.message!!.contains("bomb"))
        assertEquals(listOf("bomb.zip"), work.list()!!.toList())
    }

    @Test
    fun theFileCountAndSizeLimitsApply() {
        val many = (1..20).map { Triple("f$it.txt", lorem(10), 8) }
        val file = zip("many.zip", many)
        assertFailsWith<ArchiveException> { extract(file, ExtractLimits(maxFiles = 5)) }
        assertFailsWith<ArchiveException> { extract(file, ExtractLimits(maxBytes = 50)) }
        assertEquals(listOf("many.zip"), work.list()!!.toList())
    }

    @Test
    fun aDamagedZipIsReportedAndLeavesNothing() {
        val file = zip("bad.zip", listOf(Triple("a.txt", lorem(20000), 8), Triple("b.txt", lorem(100), 8)))
        val bytes = file.readBytes()
        bytes[60] = (bytes[60] + 1).toByte()
        file.writeBytes(bytes)
        assertFailsWith<ArchiveException> { extract(file) }
        assertEquals(listOf("bad.zip"), work.list()!!.toList())
    }

    @Test
    fun aPasswordZipIsRefusedClearly() {
        val file = zip("pw.zip", listOf(Triple("a.txt", lorem(100), 8), Triple("b.txt", lorem(100), 8)))
        val bytes = file.readBytes()
        var at = 0
        while (at + 46 < bytes.size) {
            if (bytes[at].toInt() == 0x50 && bytes[at + 1].toInt() == 0x4b && bytes[at + 2].toInt() == 1 && bytes[at + 3].toInt() == 2) {
                bytes[at + 8] = (bytes[at + 8].toInt() or 1).toByte()
            }
            at++
        }
        file.writeBytes(bytes)
        val failure = assertFailsWith<ArchiveException> { extract(file) }
        assertTrue(failure.message!!.contains("password"))
    }

    @Test
    fun gzipOfOneFile() {
        val text = resource("text.txt.gz")
        val file = File(work, "text.txt.gz").also { it.writeBytes(text) }
        val result = extract(file)
        val expected = resource("text.sha256").decodeToString().split(' ')
        val out = File(result.destination)
        assertEquals("text.txt", out.name)
        assertEquals(expected[1].toLong(), out.length())
        assertEquals(expected[0], sha256(out.readBytes()))
    }

    @Test
    fun gzipWithTwoMembersAndABadChecksum() {
        val a = lorem(3000)
        val b = lorem(4000)
        val joined = ByteArrayOutputStream()
        for (part in listOf(a, b)) GZIPOutputStream(joined).use { it.write(part) }
        val file = File(work, "two.txt.gz").also { it.writeBytes(joined.toByteArray()) }
        assertContentEquals(a + b, File(extract(file).destination).readBytes())
        val bytes = joined.toByteArray()
        bytes[bytes.size - 20] = (bytes[bytes.size - 20] + 1).toByte()
        val bad = File(work, "bad.txt.gz").also { it.writeBytes(bytes) }
        assertFailsWith<ArchiveException> { extract(bad) }
    }

    @Test
    fun xzDecodesLikeThePythonOne() {
        val expected = resource("text.sha256").decodeToString().split(' ')
        for (name in listOf("text.txt.xz", "text-p0.txt.xz", "text-crc32.txt.xz", "text-nocheck.txt.xz")) {
            val file = copyOf(name, "x-$name")
            val out = File(extract(file).destination)
            assertEquals(expected[1].toLong(), out.length(), name)
            assertEquals(expected[0], sha256(out.readBytes()), name)
            out.delete()
        }
    }

    @Test
    fun xzWithTwoStreams() {
        val file = copyOf("concat.txt.xz")
        val out = File(extract(file).destination)
        assertEquals(resource("concat.sha256").decodeToString().trim(), sha256(out.readBytes()))
    }

    @Test
    fun aDamagedXzIsDetected() {
        val bytes = resource("text-crc32.txt.xz")
        bytes[5000] = (bytes[5000] + 1).toByte()
        val file = File(work, "damaged.txt.xz").also { it.writeBytes(bytes) }
        assertFailsWith<ArchiveException> { extract(file) }
        val cut = File(work, "cut.txt.xz").also { it.writeBytes(resource("text.txt.xz").copyOf(70000)) }
        assertFailsWith<ArchiveException> { extract(cut) }
    }

    @Test
    fun tarTarGzTarXz() {
        for (name in listOf("tree.tar", "tree.tar.gz", "tree.tar.xz")) {
            val dir = File(work, name.replace('.', '_')).also { it.mkdirs() }
            val file = File(dir, name).also { it.writeBytes(resource(name)) }
            val result = extract(file)
            val top = File(result.destination)
            assertEquals("proj", top.name, name)
            assertEquals("hello tar\n", File(top, "readme.txt").readText(), name)
            assertEquals(300000L, File(top, "data/big.bin").length(), name)
            assertEquals("long name\n", File(top, "long".repeat(40) + "/name.txt").readText(), name)
            assertEquals("unicode", File(top, "é.txt").readText(), name)
            assertFalse(File(top, "link").exists(), name)
            assertTrue(result.skipped.any { it.endsWith("link") }, name)
        }
    }

    @Test
    fun notAnArchiveAtAll() {
        val file = File(work, "plain.zip").also { it.writeText("this is not a zip file at all, just text".repeat(5)) }
        assertFailsWith<ArchiveException> { extract(file) }
        val gz = File(work, "plain.txt.gz").also { it.writeText("not gzip") }
        assertFailsWith<ArchiveException> { extract(gz) }
        val xz = File(work, "plain.txt.xz").also { it.writeText("not xz at all, not xz at all") }
        assertFailsWith<ArchiveException> { extract(xz) }
    }

    private fun extractResource(name: String): File {
        val dir = File(work, name.replace('.', '_')).also { it.mkdirs() }
        val file = File(dir, name).also { it.writeBytes(resource(name)) }
        return File(extract(file).destination)
    }

    @Test
    fun zip64AndStreamedZips() {
        val z = extractResource("zip64.zip")
        assertEquals(54000L, File(z, "big64.txt").length())
        assertEquals("small", File(z, "small.txt").readText())
        val d = extractResource("datadesc.zip")
        assertEquals(30000L, File(d, "stream1.txt").length().takeIf { File(d, "stream1.txt").exists() } ?: File(d, "a/stream1.txt").length())
    }

    @Test
    fun namesInArabicJapaneseAndCp437() {
        val folder = extractResource("names.zip")
        assertEquals("arabic name", File(folder, "دراسات/العلاقات.txt").readText())
        assertEquals("cp437 name", File(folder, "café.txt").readText())
        assertEquals("japanese", File(folder, "日本語/ファイル.txt").readText())
    }

    @Test
    fun storedEmptyFilesAndDeepFolders() {
        val folder = extractResource("mixed.zip")
        assertContentEquals(ByteArray(256 * 500) { (it % 256).toByte() }, File(folder, "stored.bin").readBytes())
        assertEquals(0L, File(folder, "empty.txt").length())
        assertTrue(File(folder, "emptydir").isDirectory)
        assertEquals("deep", File(folder, "x/y/z/deep.txt").readText())
    }

    @Test
    fun aZipWithOnlyFoldersAndAnEmptyZip() {
        val dir = File(work, "dirs").also { it.mkdirs() }
        val only = File(dir, "dirsonly.zip").also { it.writeBytes(resource("dirsonly.zip")) }
        val result = extract(only)
        assertTrue(File(result.destination).isDirectory)
        val empty = File(dir, "empty.zip").also { it.writeBytes(resource("empty.zip")) }
        assertFailsWith<ArchiveException> { extract(empty) }
    }

    @Test
    fun gnuLongNames() {
        val folder = extractResource("gnu.tar")
        assertEquals("gnu long", File(folder, "g".repeat(150) + "/long.txt").readText())
        assertEquals("short", File(folder, "short.txt").readText())
    }

    @Test
    fun damagedFilesNeverHangOrCrash() {
        val names = listOf("tree.tar.gz", "tree.tar.xz", "tree.tar", "text.txt.gz", "text-crc32.txt.xz", "zip64.zip", "datadesc.zip", "names.zip", "mixed.zip", "gnu.tar", "concat.txt.xz")
        val random = java.util.Random(42)
        val unexpected = ArrayList<String>()
        var tries = 0
        for (name in names) {
            val original = resource(name)
            for (round in 0 until 80) {
                val bytes = original.copyOf(if (round % 5 == 4) random.nextInt(original.size.coerceAtLeast(2)) + 1 else original.size)
                repeat(1 + random.nextInt(3)) { if (bytes.isNotEmpty()) bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte() }
                val dir = File(work, "fuzz-$tries").also { it.mkdirs() }
                tries++
                val file = File(dir, name).also { it.writeBytes(bytes) }
                try {
                    runBlocking { kotlinx.coroutines.withTimeout(20_000) { ArchiveExtractor.extract(file.toOkioPath()) } }
                } catch (_: ArchiveException) {
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    unexpected.add("$name round $round: TIMEOUT")
                } catch (e: Throwable) {
                    unexpected.add("$name round $round: ${e::class.simpleName}: ${e.message}")
                }
                assertFalse(dir.list()!!.any { it.contains("fetchjet-extract") }, "a work folder was left for $name round $round")
            }
        }
        assertEquals(emptyList<String>(), unexpected.distinct().take(12))
    }

    @Test
    fun bigArchiveSpeed() {
        val chunk = lorem(1 shl 20)
        val file = File(work, "big.zip")
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            out.putNextEntry(JavaZipEntry("big.txt"))
            repeat(120) { out.write(chunk) }
            out.closeEntry()
        }
        val started = System.nanoTime()
        val result = extract(file)
        val seconds = (System.nanoTime() - started) / 1e9
        val megabytes = result.bytes / 1048576.0
        println("big zip: %.0f MB in %.1f s = %.0f MB/s".format(megabytes, seconds, megabytes / seconds))
        assertEquals(120L * chunk.size, result.bytes)
        assertTrue(megabytes / seconds > 8.0, "too slow: %.1f MB/s".format(megabytes / seconds))
    }
}
