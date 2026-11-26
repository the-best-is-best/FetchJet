package com.michelle.raouf.fetch_jet.feature.archive_extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalListingCheckTest {
    private fun file(path: String, size: Long = 10) = ExternalEntry(path, size, isDirectory = false, isLink = false)

    @Test
    fun safePaths() {
        assertTrue(ArchiveExtractor.isSafeEntryPath("a.txt"))
        assertTrue(ArchiveExtractor.isSafeEntryPath("inner\\b.txt"))
        assertTrue(ArchiveExtractor.isSafeEntryPath("x/..y/z"))
        assertFalse(ArchiveExtractor.isSafeEntryPath("/etc/passwd"))
        assertFalse(ArchiveExtractor.isSafeEntryPath("\\Windows\\x"))
        assertFalse(ArchiveExtractor.isSafeEntryPath("C:\\x"))
        assertFalse(ArchiveExtractor.isSafeEntryPath("a/../../b"))
        assertFalse(ArchiveExtractor.isSafeEntryPath("a\\..\\b"))
        assertFalse(ArchiveExtractor.isSafeEntryPath(""))
    }

    @Test
    fun listingIsCheckedBeforeExtracting() {
        assertEquals(20L, ArchiveExtractor.checkListing(listOf(file("a"), ExternalEntry("d", 0, true, false), file("d/b")), ExtractLimits()))
        assertFailsWith<ArchiveException> { ArchiveExtractor.checkListing(listOf(file("../a")), ExtractLimits()) }
        assertFailsWith<ArchiveException> { ArchiveExtractor.checkListing(listOf(ExternalEntry("l", 0, false, true)), ExtractLimits()) }
        assertFailsWith<ArchiveException> { ArchiveExtractor.checkListing(List(6) { file("f$it") }, ExtractLimits(maxFiles = 5)) }
        assertFailsWith<ArchiveException> { ArchiveExtractor.checkListing(listOf(file("a", 40), file("b", 40)), ExtractLimits(maxBytes = 50)) }
    }
}
