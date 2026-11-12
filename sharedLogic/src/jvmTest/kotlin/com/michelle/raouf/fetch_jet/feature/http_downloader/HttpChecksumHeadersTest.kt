package com.michelle.raouf.fetch_jet.feature.http_downloader

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpChecksumHeadersTest {
    private val data = "hello".toByteArray()

    private fun raw(algorithm: String): ByteArray = MessageDigest.getInstance(algorithm).digest(data)

    private fun hex(algorithm: String): String = raw(algorithm).joinToString("") { "%02x".format(it) }

    private fun b64(algorithm: String): String = Base64.getEncoder().encodeToString(raw(algorithm))

    private fun pick(vararg headers: Pair<String, String>): ServerChecksum? {
        val map = headers.associate { it.first.lowercase() to it.second }
        return serverChecksumFrom { name -> map[name.lowercase()] }
    }

    @Test
    fun digestSha256Base64() {
        val found = pick("Digest" to "sha-256=${b64("SHA-256")}")
        assertEquals(ServerChecksum("sha256", hex("SHA-256"), "Digest"), found)
    }

    @Test
    fun digestSha512Md5AndSha() {
        assertEquals("sha512", pick("Digest" to "SHA-512=${b64("SHA-512")}")?.algorithm)
        assertEquals(hex("SHA-512"), pick("Digest" to "SHA-512=${b64("SHA-512")}")?.hex)
        assertEquals(ServerChecksum("md5", hex("MD5"), "Digest"), pick("Digest" to "MD5=${b64("MD5")}"))
        assertEquals(ServerChecksum("sha1", hex("SHA-1"), "Digest"), pick("Digest" to "SHA=${b64("SHA-1")}"))
    }

    @Test
    fun digestListPrefersTheStrongest() {
        val found = pick("Digest" to "md5=${b64("MD5")}, sha-256=${b64("SHA-256")},unixsum=12345")
        assertEquals("sha256", found?.algorithm)
        assertEquals(hex("SHA-256"), found?.hex)
    }

    @Test
    fun reprDigestStructuredForm() {
        val found = pick("Repr-Digest" to "sha-256=:${b64("SHA-256")}:")
        assertEquals(ServerChecksum("sha256", hex("SHA-256"), "Repr-Digest"), found)
        val both = pick("Repr-Digest" to "sha-512=:${b64("SHA-512")}:, sha-256=:${b64("SHA-256")}:")
        assertEquals("sha512", both?.algorithm)
    }

    @Test
    fun contentDigestStructuredForm() {
        val found = pick("Content-Digest" to "sha-256=:${b64("SHA-256")}:")
        assertEquals(ServerChecksum("sha256", hex("SHA-256"), "Content-Digest"), found)
    }

    @Test
    fun contentMd5() {
        assertEquals(ServerChecksum("md5", hex("MD5"), "Content-MD5"), pick("Content-MD5" to b64("MD5")))
    }

    @Test
    fun xChecksumHexHeaders() {
        assertEquals(
            ServerChecksum("sha256", hex("SHA-256"), "X-Checksum-Sha256"),
            pick("X-Checksum-Sha256" to hex("SHA-256").uppercase())
        )
        assertEquals(ServerChecksum("sha1", hex("SHA-1"), "X-Checksum-Sha1"), pick("X-Checksum-Sha1" to hex("SHA-1")))
        assertEquals(ServerChecksum("md5", hex("MD5"), "X-Checksum-Md5"), pick("X-Checksum-Md5" to hex("MD5")))
        val all = pick(
            "X-Checksum-Md5" to hex("MD5"),
            "X-Checksum-Sha1" to hex("SHA-1"),
            "X-Checksum-Sha256" to hex("SHA-256")
        )
        assertEquals("sha256", all?.algorithm)
    }

    @Test
    fun googHashUsesMd5AndIgnoresCrc32c() {
        val found = pick("x-goog-hash" to "crc32c=n03x6A==,md5=${b64("MD5")}")
        assertEquals(ServerChecksum("md5", hex("MD5"), "x-goog-hash"), found)
        assertNull(pick("x-goog-hash" to "crc32c=n03x6A=="))
    }

    @Test
    fun amzChecksumSha256() {
        val found = pick("x-amz-checksum-sha256" to b64("SHA-256"))
        assertEquals(ServerChecksum("sha256", hex("SHA-256"), "x-amz-checksum-sha256"), found)
        assertNull(pick("x-amz-checksum-sha256" to b64("SHA-256") + "-3"))
    }

    @Test
    fun etagIsNotAChecksum() {
        assertNull(pick("ETag" to "\"${hex("MD5")}\""))
    }

    @Test
    fun wrongLengthOrBrokenValuesAreIgnored() {
        assertNull(pick("Digest" to "sha-256=${b64("MD5")}"))
        assertNull(pick("Content-MD5" to "not base64 !!"))
        assertNull(pick("X-Checksum-Sha256" to "abc"))
        assertNull(pick("Digest" to ""))
    }

    @Test
    fun userChecksumWinsOverTheServer() {
        val server = ServerChecksum("sha256", hex("SHA-256"), "Digest")
        val user = pickExpectedDigest("md5:${hex("MD5")}", server)
        assertEquals("md5", user?.algorithm)
        assertEquals(false, user?.fromServer)
        val fromServer = pickExpectedDigest(null, server)
        assertEquals("sha256", fromServer?.algorithm)
        assertEquals(true, fromServer?.fromServer)
        assertNull(pickExpectedDigest("", null))
    }
}
