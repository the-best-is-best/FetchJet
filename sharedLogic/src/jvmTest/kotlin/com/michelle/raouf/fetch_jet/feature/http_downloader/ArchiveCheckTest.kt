package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveCheckTest {
    private val storedData = ByteArray(5000) { ('A' + it % 23).code.toByte() }
    private val packedData = ByteArray(40000) { ('a' + (it / 7) % 26).code.toByte() }
    private val noiseData = Random(7).nextBytes(3000)

    private fun javaCrc(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset): Long =
        CRC32().also { it.update(bytes, offset, count) }.value

    private fun zipBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val stored = ZipEntry("stored.txt")
            stored.method = ZipEntry.STORED
            stored.size = storedData.size.toLong()
            stored.compressedSize = storedData.size.toLong()
            stored.crc = javaCrc(storedData)
            zip.putNextEntry(stored)
            zip.write(storedData)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("folder/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("folder/packed.txt"))
            zip.write(packedData)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("noise.bin"))
            zip.write(noiseData)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun check(bytes: ByteArray, kind: String): ArchiveReport {
        val file = File.createTempFile("fjarchive", ".$kind").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        return FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle -> checkArchive(kind, handle) }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun covers(report: ArchiveReport, offset: Int): Boolean = report.damaged.any { offset.toLong() in it }

    private fun le32(value: Long): ByteArray = ByteArray(4) { (value ushr (8 * it)).toByte() }

    private fun le64(value: Long): ByteArray = ByteArray(8) { (value ushr (8 * it)).toByte() }

    @Test
    fun crc32MatchesJava() {
        assertEquals(javaCrc(packedData), Crc32().also { it.update(packedData) }.value)
        val split = Crc32()
        split.update(noiseData, 0, 1000)
        split.update(noiseData, 1000, 2000)
        assertEquals(javaCrc(noiseData), split.value)
        assertEquals(0L, Crc32().value)
    }

    @Test
    fun archiveKindComesFromTheExtension() {
        for (name in listOf("a.zip", "A.JAR", "x.apk", "book.epub", "r.docx", "s.xlsx", "p.pptx")) {
            assertEquals("zip", archiveKindOf(name), name)
        }
        assertEquals("7z", archiveKindOf("x.7z"))
        assertEquals("rar", archiveKindOf("x.part1.rar"))
        assertNull(archiveKindOf("x.iso"))
        assertNull(archiveKindOf("zip"))
    }

    @Test
    fun goodZipPasses() {
        val report = check(zipBytes(), "zip")
        assertFalse(report.isDamaged, report.problem.orEmpty())
        assertEquals(4, report.entries)
        assertEquals(4, report.crcChecked)
        assertTrue(report.damaged.isEmpty())
    }

    @Test
    fun emptyZipPasses() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).close()
        assertFalse(check(out.toByteArray(), "zip").isDamaged)
    }

    @Test
    fun flippedByteInAStoredEntryIsFound() {
        val bytes = zipBytes()
        val at = indexOf(bytes, storedData.copyOfRange(0, 64)) + 2500
        bytes[at] = (bytes[at].toInt() xor 0x55).toByte()
        val report = check(bytes, "zip")
        assertTrue(report.isDamaged)
        assertTrue(covers(report, at), report.damaged.toString())
        val noiseAt = indexOf(bytes, noiseData.copyOfRange(0, 64))
        assertFalse(covers(report, noiseAt), report.damaged.toString())
    }

    @Test
    fun flippedByteInADeflatedEntryIsFound() {
        val bytes = zipBytes()
        val header = indexOf(bytes, "folder/packed.txt".toByteArray())
        val at = header + "folder/packed.txt".length + 40
        bytes[at] = (bytes[at].toInt() xor 0xFF).toByte()
        val report = check(bytes, "zip")
        assertTrue(report.isDamaged)
        assertTrue(covers(report, at), report.damaged.toString())
        assertFalse(covers(report, indexOf(bytes, storedData.copyOfRange(0, 64))), report.damaged.toString())
    }

    @Test
    fun brokenLocalHeaderIsFound() {
        val bytes = zipBytes()
        val name = indexOf(bytes, "noise.bin".toByteArray())
        val header = name - 30
        bytes[header] = 0
        val report = check(bytes, "zip")
        assertTrue(report.isDamaged)
        assertTrue(covers(report, header), report.damaged.toString())
    }

    @Test
    fun truncatedZipIsDamaged() {
        val bytes = zipBytes()
        val report = check(bytes.copyOf(bytes.size - 40), "zip")
        assertTrue(report.isDamaged)
        val cut = check(bytes.copyOf(bytes.size / 2), "zip")
        assertTrue(cut.isDamaged)
    }

    @Test
    fun brokenCentralDirectoryIsDamaged() {
        val bytes = zipBytes()
        var directory = -1
        for (i in bytes.size - 4 downTo 0) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte()) {
                directory = i
                break
            }
        }
        assertTrue(directory > 0)
        bytes[directory] = 0
        val report = check(bytes, "zip")
        assertTrue(report.isDamaged)
        assertTrue(covers(report, directory), report.damaged.toString())
    }

    private fun sevenZip(header: ByteArray): ByteArray {
        val tail = le64(0) + le64(header.size.toLong()) + le32(javaCrc(header))
        val signature = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)
        return signature + le32(javaCrc(tail)) + tail + header
    }

    @Test
    fun sevenZipHeadersAreChecked() {
        val header = Random(3).nextBytes(200)
        val good = sevenZip(header)
        assertFalse(check(good, "7z").isDamaged, check(good, "7z").problem.orEmpty())

        val badSignature = good.copyOf().also { it[1] = 0 }
        assertTrue(check(badSignature, "7z").isDamaged)

        val badStart = good.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        val startReport = check(badStart, "7z")
        assertTrue(startReport.isDamaged)
        assertTrue(covers(startReport, 20))

        val badHeader = good.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }
        val headerReport = check(badHeader, "7z")
        assertTrue(headerReport.isDamaged)
        assertTrue(covers(headerReport, 100))
        assertFalse(covers(headerReport, 10))

        assertTrue(check(good.copyOf(good.size - 50), "7z").isDamaged)
        assertTrue(check(good.copyOf(10), "7z").isDamaged)
    }

    @Test
    fun rar4MainHeaderIsChecked() {
        val signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        val main = byteArrayOf(0x11, 0x22, 0x73, 0, 0, 13, 0, 0, 0, 0, 0, 0, 0)
        val good = signature + main + ByteArray(64)
        assertFalse(check(good, "rar").isDamaged, check(good, "rar").problem.orEmpty())
        val wrongType = good.copyOf().also { it[9] = 0x74 }
        assertTrue(check(wrongType, "rar").isDamaged)
        val wrongSignature = good.copyOf().also { it[0] = 0 }
        assertTrue(check(wrongSignature, "rar").isDamaged)
        assertTrue(check(signature, "rar").isDamaged)
    }

    @Test
    fun rar5MainHeaderIsChecked() {
        val signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        val header = byteArrayOf(1, 0, 0, 0, 0)
        val sized = byteArrayOf(header.size.toByte()) + header
        val good = signature + le32(javaCrc(sized)) + sized + ByteArray(64)
        assertFalse(check(good, "rar").isDamaged, check(good, "rar").problem.orEmpty())
        val flipped = good.copyOf().also { it[14] = 9 }
        val report = check(flipped, "rar")
        assertTrue(report.isDamaged)
        assertTrue(covers(report, 14))
        val cut = good.copyOf(13)
        assertTrue(check(cut, "rar").isDamaged)
    }

    private fun tempParts(): Pair<File, (Int) -> Path> {
        val dir = File.createTempFile("fjparts", "").apply { delete(); mkdirs(); deleteOnExit() }
        val partPath: (Int) -> Path = { index -> File(dir, "f.bin.part$index").toOkioPath() }
        return dir to partPath
    }

    @Test
    fun onlyTheDamagedTailOfAPieceIsFetchedAgain(): Unit = runBlocking {
        val (dir, partPath) = tempParts()
        val first = ByteArray(10) { it.toByte() }
        val second = ByteArray(10) { (10 + it).toByte() }
        File(dir, "f.bin.part0").writeBytes(first)
        File(dir, "f.bin.part1").writeBytes(second.copyOf().also { it[4] = 99 })
        val pieces = listOf(ChunkLayout.Piece(0, 0, 9, 10), ChunkLayout.Piece(1, 10, 19, 10))
        val asked = ArrayList<Pair<Int, Long>>()
        val fixed = refetchDamagedRanges(listOf(13L..15L), pieces, partPath) { piece ->
            asked.add(piece.index to piece.downloaded)
            val part = File(dir, "f.bin.part${piece.index}")
            part.appendBytes(second.copyOfRange(piece.downloaded.toInt(), 10))
            piece.downloaded = 10
        }
        assertTrue(fixed)
        assertEquals(listOf(1 to 3L), asked)
        assertContentEquals(first, File(dir, "f.bin.part0").readBytes())
        assertContentEquals(second, File(dir, "f.bin.part1").readBytes())
        assertFalse(File(dir, "f.bin.part1.bak").exists())
        dir.deleteRecursively()
    }

    @Test
    fun aFailedRefetchPutsTheOldPartBack(): Unit = runBlocking {
        val (dir, partPath) = tempParts()
        val old = ByteArray(10) { 7 }
        File(dir, "f.bin.part0").writeBytes(old)
        val pieces = listOf(ChunkLayout.Piece(0, 0, 9, 10))
        val fixed = refetchDamagedRanges(listOf(0L..3L), pieces, partPath) { throw RangeNotHonouredException("no ranges") }
        assertFalse(fixed)
        assertContentEquals(old, File(dir, "f.bin.part0").readBytes())
        assertEquals(10L, pieces[0].downloaded)
        assertFalse(File(dir, "f.bin.part0.bak").exists())
        dir.deleteRecursively()
    }

    @Test
    fun rangesOutsideEveryPieceFetchNothing(): Unit = runBlocking {
        val (dir, partPath) = tempParts()
        val pieces = listOf(ChunkLayout.Piece(0, 0, 9, 10))
        assertFalse(refetchDamagedRanges(listOf(50L..60L), pieces, partPath) { })
        dir.deleteRecursively()
    }
}
