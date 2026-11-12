package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentPathSafetyTest {
    private fun risk(path: String) = checkTorrentPath(path).risk

    private fun bstr(text: String) = "${text.encodeToByteArray().size}:$text"

    private fun multiFileTorrent(name: String, vararg paths: List<String>): ByteArray {
        val files = paths.joinToString("") { p ->
            "d6:lengthi1e4:pathl" + p.joinToString("") { bstr(it) } + "ee"
        }
        return "d4:infod5:filesl${files}e4:name${bstr(name)}12:piece lengthi16384eee".encodeToByteArray()
    }

    @Test
    fun plainPathsAreFine() {
        assertEquals(TorrentPathRisk.FINE, risk("movie.mkv"))
        assertEquals(TorrentPathRisk.FINE, risk("Show/Season 1/episode 01.mkv"))
        assertEquals(TorrentPathRisk.FINE, risk(".hidden/.config"))
        assertEquals(TorrentPathRisk.FINE, risk("console.log"))
        assertEquals(TorrentPathRisk.FINE, risk("com10.txt"))
        assertEquals(TorrentPathRisk.FINE, risk("فيلم/الحلقة 1.mp4"))
    }

    @Test
    fun pathsAreNormalised() {
        assertEquals("a/b/c.txt", checkTorrentPath("a//b/./c.txt").normalized)
        assertEquals("b", checkTorrentPath("a/../b").normalized)
        assertEquals(TorrentPathRisk.FINE, risk("a/../b"))
        assertEquals(TorrentPathRisk.FINE, risk("a/./b/"))
    }

    @Test
    fun mixedSeparatorsAreOnePath() {
        val check = checkTorrentPath("a\\b/c\\d.txt")
        assertEquals("a/b/c/d.txt", check.normalized)
        assertEquals(TorrentPathRisk.FINE, check.risk)
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("a\\..\\../b"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("a/..\\..\\b"))
    }

    @Test
    fun climbingOutEscapes() {
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("a/../../b"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk(".."))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("../x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("a/b/../../../x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("./../x"))
    }

    @Test
    fun absolutePathsEscape() {
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("/etc/passwd"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("\\Windows\\x"))
    }

    @Test
    fun driveLettersAndUncEscape() {
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("C:\\x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("c:/x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("C:x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("\\\\server\\share\\x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("//server/share/x"))
        assertEquals(TorrentPathRisk.ESCAPES_ROOT, risk("\\\\?\\C:\\x"))
    }

    @Test
    fun reservedDeviceNamesAreUnsafe() {
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("CON"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("con.txt"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("aux/x"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("dir/NUL.tar.gz"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("COM1"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("lpt9.log"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("prn .txt"))
        assertTrue(isReservedDeviceName("Con"))
        assertFalse(isReservedDeviceName("contact.txt"))
        assertFalse(isReservedDeviceName("my.con"))
    }

    @Test
    fun trailingDotOrSpaceIsUnsafe() {
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("name."))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("name "))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("dir./file.txt"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("dir /file.txt"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("..."))
    }

    @Test
    fun controlCharactersAreUnsafe() {
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("a\u0000b"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("line\nbreak.txt"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("tab\there"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("del\u007f"))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("c1\u0085x"))
    }

    @Test
    fun reservedCharactersAreUnsafe() {
        for (c in "<>:\"|?*") {
            assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("dir/na${c}me.txt"), "character $c")
        }
    }

    @Test
    fun longNamesAreUnsafe() {
        assertEquals(TorrentPathRisk.FINE, risk("a".repeat(255)))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("a".repeat(256)))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("dir/" + "a".repeat(300)))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("ف".repeat(150)))
        assertEquals(TorrentPathRisk.FINE, risk("a".repeat(200) + "/" + "b".repeat(200)))
    }

    @Test
    fun emptyPathHasNoSafeName() {
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk(""))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("."))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, risk("a/.."))
    }

    @Test
    fun theWorstFileDecidesForTheTorrent() {
        assertEquals(TorrentPathRisk.FINE, worstTorrentPathRisk(listOf("a/b.txt", "c.txt")))
        assertEquals(TorrentPathRisk.UNSAFE_NAME, worstTorrentPathRisk(listOf("a/b.txt", "CON")))
        assertEquals(
            TorrentPathRisk.ESCAPES_ROOT,
            worstTorrentPathRisk(listOf("a/b.txt", "CON", "a/../../b"))
        )
        val files = listOf(
            TorrentFileEntry(index = 0, path = "ok/file.bin", size = 1),
            TorrentFileEntry(index = 1, path = "ok/aux.bin", size = 1)
        )
        assertEquals(TorrentPathRisk.UNSAFE_NAME, files.worstPathRisk())
        assertEquals(listOf(1), files.unsafelyNamed().map { it.index })
    }

    @Test
    fun declaredPathsOfATorrentFile() {
        assertFalse(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("a", "b.txt"))))
        assertFalse(torrentDeclaresEscapingPaths(multiFileTorrent("X: The Movie", listOf("A: b.mkv"))))
        assertFalse(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("a", "..", "b.txt"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("..", "b.txt"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("a", "..", "..", "b"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("a/../../b"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("ok"), listOf("/etc", "passwd"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("C:", "x"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("pack", listOf("\\\\server\\share\\x"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("..", listOf("b.txt"))))
        assertTrue(torrentDeclaresEscapingPaths(multiFileTorrent("../../x", listOf("b.txt"))))
    }

    @Test
    fun declaredPathsOfAV2FileTree() {
        val leaf = "d0:d6:lengthi1eee"
        fun tree(body: String) =
            "d4:infod9:file treed${body}e4:name4:pack12:piece lengthi16384eee".encodeToByteArray()
        assertFalse(torrentDeclaresEscapingPaths(tree("3:dird5:a.txt${leaf}e")))
        assertTrue(torrentDeclaresEscapingPaths(tree("2:..d5:a.txt${leaf}e")))
        assertTrue(torrentDeclaresEscapingPaths(tree("3:dird2:..d2:..d1:x${leaf}eee")))
    }

    @Test
    fun brokenTorrentBytesAreLeftToTheEngine() {
        assertFalse(torrentDeclaresEscapingPaths(ByteArray(0)))
        assertFalse(torrentDeclaresEscapingPaths("not a torrent".encodeToByteArray()))
        assertFalse(torrentDeclaresEscapingPaths("d4:infoi1ee".encodeToByteArray()))
    }
}
