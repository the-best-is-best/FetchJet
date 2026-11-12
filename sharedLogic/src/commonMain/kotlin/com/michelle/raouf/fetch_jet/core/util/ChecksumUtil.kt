package com.michelle.raouf.fetch_jet.core.util

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import okio.Buffer
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
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
        algoPart in setOf("sha512", "sha-512") -> "sha512"
        algoPart in setOf("sha256", "sha-256") -> "sha256"
        algoPart in setOf("md5") -> "md5"
        algoPart in setOf("sha1", "sha-1") -> "sha1"
        hexPart.length == 128 -> "sha512"
        hexPart.length == 64 -> "sha256"
        hexPart.length == 32 -> "md5"
        hexPart.length == 40 -> "sha1"
        else -> return null
    }
    return ExpectedChecksum(algo, hexPart)
}

private fun hashingSource(algorithm: String, source: Source): HashingSource? =
    when (algorithm.lowercase()) {
        "sha512" -> HashingSource.sha512(source)
        "sha256" -> HashingSource.sha256(source)
        "md5" -> HashingSource.md5(source)
        "sha1" -> HashingSource.sha1(source)
        else -> null
    }

fun isKnownChecksumAlgorithm(algorithm: String): Boolean =
    algorithm.lowercase() in setOf("sha512", "sha256", "sha1", "md5")

fun hashFileHex(path: Path, algorithm: String, onBytes: (Long) -> Unit = {}): String? {
    if (!isKnownChecksumAlgorithm(algorithm)) return null
    val hashing = hashingSource(algorithm, AppFileSystem.source(path)) ?: return null
    return hashing.use { source ->
        val scratch = Buffer()
        var done = 0L
        while (true) {
            val read = source.read(scratch, 256L * 1024)
            if (read == -1L) break
            scratch.clear()
            done += read
            onBytes(done)
        }
        source.hash.hex()
    }
}

/** Streams the file and returns its lowercase hex digest for the given algorithm, or null. */
fun fileChecksumHex(path: String, algorithm: String): String? {
    val p = path.toPath()
    if (!AppFileSystem.exists(p)) return null
    return try {
        hashFileHex(p, algorithm)
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
