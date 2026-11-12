package com.michelle.raouf.fetch_jet.feature.http_downloader

import okio.ByteString.Companion.decodeBase64

internal data class ServerChecksum(val algorithm: String, val hex: String, val header: String)

private val DIGEST_LIST_HEADERS = listOf("Repr-Digest", "Digest", "Content-Digest")

private val HEX_HEADERS = listOf(
    "X-Checksum-Sha256" to "sha256",
    "X-Checksum-Sha1" to "sha1",
    "X-Checksum-Md5" to "md5"
)

private fun digestBytes(algorithm: String): Int = when (algorithm) {
    "sha512" -> 64
    "sha256" -> 32
    "sha1" -> 20
    "md5" -> 16
    else -> 0
}

private fun digestStrength(algorithm: String): Int = when (algorithm) {
    "sha512" -> 4
    "sha256" -> 3
    "sha1" -> 2
    "md5" -> 1
    else -> 0
}

private fun digestAlgorithm(name: String): String? = when (name.trim().lowercase()) {
    "sha-512", "sha512" -> "sha512"
    "sha-256", "sha256" -> "sha256"
    "sha", "sha-1", "sha1" -> "sha1"
    "md5" -> "md5"
    else -> null
}

private fun base64Checksum(algorithm: String, value: String, header: String): ServerChecksum? {
    val text = value.trim().trim('"').trim(':').trim()
    if (text.isEmpty()) return null
    val bytes = text.decodeBase64() ?: return null
    if (bytes.size != digestBytes(algorithm)) return null
    return ServerChecksum(algorithm, bytes.hex(), header)
}

private fun hexChecksum(algorithm: String, value: String, header: String): ServerChecksum? {
    val text = value.trim().trim('"').lowercase()
    if (text.length != digestBytes(algorithm) * 2) return null
    if (!text.all { it in "0123456789abcdef" }) return null
    return ServerChecksum(algorithm, text, header)
}

internal fun parseDigestList(header: String, value: String?): List<ServerChecksum> {
    if (value.isNullOrBlank()) return emptyList()
    return value.split(',').mapNotNull { item ->
        val name = item.substringBefore('=', "")
        val algorithm = digestAlgorithm(name) ?: return@mapNotNull null
        base64Checksum(algorithm, item.substringAfter('=', "").substringBefore(';'), header)
    }
}

internal fun serverChecksumFrom(header: (String) -> String?): ServerChecksum? {
    val found = ArrayList<ServerChecksum>()
    for (name in DIGEST_LIST_HEADERS) found += parseDigestList(name, header(name))
    header("x-amz-checksum-sha256")?.let { base64Checksum("sha256", it, "x-amz-checksum-sha256") }?.let { found += it }
    for ((name, algorithm) in HEX_HEADERS) {
        header(name)?.let { hexChecksum(algorithm, it, name) }?.let { found += it }
    }
    header("Content-MD5")?.let { base64Checksum("md5", it, "Content-MD5") }?.let { found += it }
    found += parseDigestList("x-goog-hash", header("x-goog-hash"))
    return found.maxByOrNull { digestStrength(it.algorithm) }
}
