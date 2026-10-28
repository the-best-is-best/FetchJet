package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry

sealed class BencodeElement {
    data class BInt(val value: Long) : BencodeElement()
    data class BString(val bytes: ByteArray) : BencodeElement() {
        fun asString(): String = bytes.decodeToString()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BString) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class BList(val list: List<BencodeElement>) : BencodeElement()
    data class BDict(val entries: Map<String, BencodeElement>) : BencodeElement()
}

class BencodeParser(private val bytes: ByteArray) {
    private var index = 0
    private var depth = 0

    fun parseNext(): BencodeElement {
        if (index >= bytes.size) throw IllegalArgumentException("Unexpected EOF at index $index")
        // A crafted torrent with thousands of nested lists would overflow the stack
        // (StackOverflowError is not an Exception, so nothing upstream would catch it).
        if (depth > MAX_DEPTH) throw IllegalArgumentException("Bencode nesting too deep")
        depth++
        try {
            return when (val byte = bytes[index].toInt().toChar()) {
                'i' -> parseInteger()
                'l' -> parseList()
                'd' -> parseDictionary()
                in '0'..'9' -> parseString()
                else -> throw IllegalArgumentException("Invalid Bencode prefix '$byte' at byte $index")
            }
        } finally {
            depth--
        }
    }

    private companion object {
        const val MAX_DEPTH = 64
    }

    private fun parseInteger(): BencodeElement.BInt {
        index++ // skip 'i'
        val start = index
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') {
            index++
        }
        if (index >= bytes.size) throw IllegalArgumentException("Unterminated integer at $start")
        val numStr = bytes.decodeToString(start, index)
        index++ // skip 'e'
        return BencodeElement.BInt(numStr.toLong())
    }

    private fun parseString(): BencodeElement.BString {
        val start = index
        while (index < bytes.size && bytes[index].toInt().toChar() != ':') {
            index++
        }
        if (index >= bytes.size) throw IllegalArgumentException("Invalid string length specifier at $start")
        val lengthStr = bytes.decodeToString(start, index)
        val length = lengthStr.toInt()
        index++ // skip ':'
        if (index + length > bytes.size) throw IllegalArgumentException("String length $length exceeds buffer size")
        val strBytes = bytes.copyOfRange(index, index + length)
        index += length
        return BencodeElement.BString(strBytes)
    }

    private fun parseList(): BencodeElement.BList {
        index++ // skip 'l'
        val list = mutableListOf<BencodeElement>()
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') {
            list.add(parseNext())
        }
        if (index >= bytes.size) throw IllegalArgumentException("Unterminated list")
        index++ // skip 'e'
        return BencodeElement.BList(list)
    }

    private fun parseDictionary(): BencodeElement.BDict {
        index++ // skip 'd'
        val map = mutableMapOf<String, BencodeElement>()
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') {
            val keyElem = parseNext()
            if (keyElem !is BencodeElement.BString) {
                throw IllegalArgumentException("Dictionary key must be string")
            }
            val key = keyElem.asString()
            val value = parseNext()
            map[key] = value
        }
        if (index >= bytes.size) throw IllegalArgumentException("Unterminated dictionary")
        index++ // skip 'e'
        return BencodeElement.BDict(map)
    }
}

data class ParsedTorrentInfo(
    val name: String,
    val files: List<TorrentFileEntry>,
    val totalSize: Long,
    val webSeeds: List<String>,
    val announce: String?
)

object TorrentMetadataParser {
    fun parse(bytes: ByteArray, fallbackBaseUrl: String? = null): ParsedTorrentInfo {
        val root = BencodeParser(bytes).parseNext() as? BencodeElement.BDict
            ?: throw IllegalArgumentException("Invalid torrent file: Root element must be a Bencode dictionary")

        val entries = root.entries
        val infoDict = entries["info"] as? BencodeElement.BDict
            ?: throw IllegalArgumentException("Invalid torrent file: Missing 'info' dictionary")

        val name =
            (infoDict.entries["name"] as? BencodeElement.BString)?.asString()
                ?.let { sanitizePathSegment(it) }?.ifBlank { null } ?: "Torrent_Download"

        val filesList = mutableListOf<TorrentFileEntry>()
        var calculatedTotalSize = 0L

        val filesElem = infoDict.entries["files"] as? BencodeElement.BList
        if (filesElem != null) {
            // Multi-file torrent
            filesElem.list.forEachIndexed { idx, fileItem ->
                val fileDict = fileItem as? BencodeElement.BDict ?: return@forEachIndexed
                val length = (fileDict.entries["length"] as? BencodeElement.BInt)?.value ?: 0L
                val pathElem = fileDict.entries["path"] as? BencodeElement.BList
                // Each segment comes from the torrent file (untrusted). Drop "." / "..", strip
                // path separators and control characters so a crafted torrent cannot write or
                // delete outside the destination folder.
                val pathSegments =
                    pathElem?.list?.mapNotNull { (it as? BencodeElement.BString)?.asString() }
                        ?.map { seg -> sanitizePathSegment(seg) }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                val relativePath = pathSegments.joinToString("/")
                if (relativePath.isNotBlank()) {
                    filesList.add(
                        TorrentFileEntry(
                            index = idx,
                            path = relativePath,
                            size = length,
                            isSelected = true
                        )
                    )
                    calculatedTotalSize += length
                }
            }
        } else {
            // Single-file torrent
            val length = (infoDict.entries["length"] as? BencodeElement.BInt)?.value ?: 0L
            filesList.add(
                TorrentFileEntry(
                    index = 0,
                    path = name,
                    size = length,
                    isSelected = true
                )
            )
            calculatedTotalSize = length
        }

        // Web seeds (url-list) - ignore unstable internal storage nodes like dn*.archive.org
        val webSeeds = mutableListOf<String>()
        when (val urlListElem = entries["url-list"]) {
            is BencodeElement.BString -> {
                val url = urlListElem.asString()
                if (url.isNotBlank() && !url.contains(".archive.org")) webSeeds.add(url)
            }

            is BencodeElement.BList -> {
                urlListElem.list.forEach { item ->
                    if (item is BencodeElement.BString) {
                        val url = item.asString()
                        if (url.isNotBlank() && !url.contains(".archive.org")) webSeeds.add(url)
                    }
                }
            }

            else -> {}
        }

        if (fallbackBaseUrl != null && fallbackBaseUrl.startsWith("http")) {
            val baseUrl = if (fallbackBaseUrl.endsWith(".torrent")) {
                fallbackBaseUrl.substringBeforeLast('/') + "/"
            } else {
                fallbackBaseUrl
            }
            if (!webSeeds.contains(baseUrl)) {
                webSeeds.add(baseUrl)
            }
        }

        val announce = (entries["announce"] as? BencodeElement.BString)?.asString()

        return ParsedTorrentInfo(
            name = name,
            files = filesList,
            totalSize = calculatedTotalSize,
            webSeeds = webSeeds,
            announce = announce
        )
    }
}

/** Makes one path segment safe to join under a destination directory. */
internal fun sanitizePathSegment(raw: String): String {
    val cleaned = raw
        .replace(Regex("[\\\\/\u0000-\u001f:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.', ' ')
    return if (cleaned == "." || cleaned == ".." || cleaned.isBlank()) "" else cleaned
}
