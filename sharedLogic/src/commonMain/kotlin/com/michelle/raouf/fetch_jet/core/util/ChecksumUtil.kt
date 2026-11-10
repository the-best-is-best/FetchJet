package com.michelle.raouf.fetch_jet.core.util

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import okio.HashingSource
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use

/** A checksum the user typed, split into an algorithm and the expected hex digest. */
data class ExpectedChecksum(val algorithm: String, val hex: String)

/**
 * Parses "sha256:ABC", "md5 ABC", or a bare hex string (64 chars = sha256, 32 = md5). Returns
 * null when the text is blank or not a checksum we can check.
 */
fun parseExpectedChecksum(raw: String?): ExpectedChecksum? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val algoPart = text.substringBefore(':', "").substringBefore('=', "")
        .substringBefore(' ', "").trim().lowercase()
    val hexPart = (if (text.any { it == ':' || it == '=' || it == ' ' })
        text.substringAfterLast(':').substringAfterLast('=').substringAfterLast(' ')
    else text).trim().lowercase()
    if (!hexPart.all { it in "0123456789abcdef" } || hexPart.isEmpty()) return null
    val algo = when {
        algoPart in setOf("sha256", "sha-256") -> "sha256"
        algoPart in setOf("md5") -> "md5"
        algoPart in setOf("sha1", "sha-1") -> "sha1"
        hexPart.length == 64 -> "sha256"
        hexPart.length == 32 -> "md5"
        hexPart.length == 40 -> "sha1"
        else -> return null
    }
    return ExpectedChecksum(algo, hexPart)
}

/** Streams the file and returns its lowercase hex digest for the given algorithm, or null. */
fun fileChecksumHex(path: String, algorithm: String): String? {
    val p = path.toPath()
    val fs = AppFileSystem
    if (!fs.exists(p)) return null
    return try {
        val source = when (algorithm.lowercase()) {
            "sha256" -> HashingSource.sha256(fs.source(p))
            "md5" -> HashingSource.md5(fs.source(p))
            "sha1" -> HashingSource.sha1(fs.source(p))
            else -> return null
        }
        source.use { hashing ->
            hashing.buffer().use { it.readAll(blackholeSink()) }
            hashing.hash.hex()
        }
    } catch (_: Exception) {
        null
    }
}

/** true = file matches, false = mismatch, null = cannot check (bad checksum text or missing file). */
fun verifyFileChecksum(path: String, expected: String?): Boolean? {
    val parsed = parseExpectedChecksum(expected) ?: return null
    val actual = fileChecksumHex(path, parsed.algorithm) ?: return null
    return actual.equals(parsed.hex, ignoreCase = true)
}
