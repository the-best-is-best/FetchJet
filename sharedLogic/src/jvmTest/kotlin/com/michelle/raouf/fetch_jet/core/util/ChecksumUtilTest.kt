package com.michelle.raouf.fetch_jet.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecksumUtilTest {

    // Known digests of the bytes "hello" (no trailing newline).
    private val sha256Hello = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    private val md5Hello = "5d41402abc4b2a76b9719d911017c592"

    private fun helloFile(): String =
        File.createTempFile("fjchk", ".bin").apply {
            writeBytes("hello".toByteArray())
            deleteOnExit()
        }.absolutePath

    @Test
    fun hashesFileWithSha256AndMd5() {
        val path = helloFile()
        assertEquals(sha256Hello, fileChecksumHex(path, "sha256"))
        assertEquals(md5Hello, fileChecksumHex(path, "md5"))
    }

    @Test
    fun missingFileReturnsNull() {
        assertNull(fileChecksumHex("no/such/file/here.bin", "sha256"))
    }

    @Test
    fun parsesPrefixedAndBareChecksums() {
        assertEquals(
            ExpectedChecksum("sha256", sha256Hello),
            parseExpectedChecksum("sha256:$sha256Hello")
        )
        assertEquals(ExpectedChecksum("md5", md5Hello), parseExpectedChecksum("MD5 = $md5Hello"))
        // Bare hex: algorithm picked by length (64 = sha256, 32 = md5).
        assertEquals(
            ExpectedChecksum("sha256", sha256Hello),
            parseExpectedChecksum(sha256Hello.uppercase())
        )
        assertEquals(ExpectedChecksum("md5", md5Hello), parseExpectedChecksum(md5Hello))
        assertNull(parseExpectedChecksum(""))
        assertNull(parseExpectedChecksum("   "))
        assertNull(parseExpectedChecksum("not-a-hash"))
    }

    @Test
    fun verifiesMatchAndMismatch() {
        val path = helloFile()
        assertTrue(verifyFileChecksum(path, "sha256:$sha256Hello") == true)
        assertTrue(verifyFileChecksum(path, sha256Hello) == true)
        assertTrue(verifyFileChecksum(path, md5Hello) == true)
        assertFalse(verifyFileChecksum(path, "sha256:${sha256Hello.dropLast(1)}0") == true)
        // No checksum / unparseable → null (cannot check).
        assertNull(verifyFileChecksum(path, ""))
        assertNull(verifyFileChecksum(path, "garbage"))
    }
}
