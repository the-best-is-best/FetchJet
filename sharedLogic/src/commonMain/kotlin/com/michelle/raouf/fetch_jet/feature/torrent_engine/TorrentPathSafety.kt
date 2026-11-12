package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry

const val TORRENT_ESCAPES_FOLDER_ERROR =
    "This torrent tries to write files outside its folder. It was stopped."

enum class TorrentPathRisk { FINE, UNSAFE_NAME, ESCAPES_ROOT }

data class TorrentPathCheck(val normalized: String, val risk: TorrentPathRisk)

private const val MAX_NAME_BYTES = 255
private const val UNSAFE_NAME_CHARS = "<>:\"|?*"

private val RESERVED_DEVICE_NAMES: Set<String> = buildSet {
    addAll(listOf("con", "prn", "aux", "nul", "clock$", "conin$", "conout$"))
    for (digit in "0123456789¹²³") {
        add("com$digit")
        add("lpt$digit")
    }
}

private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
private val DRIVE_ROOT = Regex("^[A-Za-z]:(/|$)")

private class WalkedPath(val segments: List<String>, val climbsOut: Boolean)

private fun walkSegments(unified: String): WalkedPath {
    val kept = ArrayList<String>()
    var climbsOut = false
    for (segment in unified.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (kept.isEmpty()) climbsOut = true else kept.removeAt(kept.lastIndex)
            else -> kept.add(segment)
        }
    }
    return WalkedPath(kept, climbsOut)
}

fun isReservedDeviceName(segment: String): Boolean =
    segment.substringBefore('.').trimEnd(' ').lowercase() in RESERVED_DEVICE_NAMES

fun isUnsafeFileName(segment: String): Boolean {
    if (segment.isEmpty()) return true
    if (segment.endsWith('.') || segment.endsWith(' ')) return true
    for (c in segment) {
        val code = c.code
        if (code < 0x20 || code == 0x7f || code in 0x80..0x9f) return true
        if (UNSAFE_NAME_CHARS.indexOf(c) >= 0) return true
    }
    if (isReservedDeviceName(segment)) return true
    return segment.encodeToByteArray().size > MAX_NAME_BYTES
}

fun checkTorrentPath(rawPath: String): TorrentPathCheck {
    val unified = rawPath.replace('\\', '/')
    val walked = walkSegments(unified)
    val normalized = walked.segments.joinToString("/")
    val risk = when {
        walked.climbsOut || unified.startsWith("/") || DRIVE_PREFIX.containsMatchIn(unified) ->
            TorrentPathRisk.ESCAPES_ROOT

        walked.segments.isEmpty() || walked.segments.any { isUnsafeFileName(it) } ->
            TorrentPathRisk.UNSAFE_NAME

        else -> TorrentPathRisk.FINE
    }
    return TorrentPathCheck(normalized, risk)
}

fun worstTorrentPathRisk(paths: Iterable<String>): TorrentPathRisk {
    var worst = TorrentPathRisk.FINE
    for (path in paths) {
        val risk = checkTorrentPath(path).risk
        if (risk == TorrentPathRisk.ESCAPES_ROOT) return risk
        if (risk == TorrentPathRisk.UNSAFE_NAME) worst = risk
    }
    return worst
}

fun List<TorrentFileEntry>.worstPathRisk(): TorrentPathRisk = worstTorrentPathRisk(map { it.path })

fun List<TorrentFileEntry>.unsafelyNamed(): List<TorrentFileEntry> =
    filter { checkTorrentPath(it.path).risk == TorrentPathRisk.UNSAFE_NAME }

internal fun declaredPathEscapes(elements: List<String>): Boolean {
    val unified = elements.joinToString("/").replace('\\', '/')
    return unified.startsWith("/") || DRIVE_ROOT.containsMatchIn(unified) || walkSegments(unified).climbsOut
}

fun torrentDeclaresEscapingPaths(torrentBytes: ByteArray): Boolean {
    val info = try {
        val root = BencodeParser(torrentBytes).parseNext() as? BencodeElement.BDict ?: return false
        root.entries["info"] as? BencodeElement.BDict ?: return false
    } catch (_: Exception) {
        return false
    }
    for (key in listOf("name", "name.utf-8")) {
        val name = (info.entries[key] as? BencodeElement.BString)?.asString() ?: continue
        if (declaredPathEscapes(listOf(name))) return true
    }
    val files = info.entries["files"] as? BencodeElement.BList
    if (files != null) {
        for (item in files.list) {
            val file = item as? BencodeElement.BDict ?: continue
            for (key in listOf("path", "path.utf-8")) {
                val path = file.entries[key] as? BencodeElement.BList ?: continue
                val elements = path.list.mapNotNull { (it as? BencodeElement.BString)?.asString() }
                if (declaredPathEscapes(elements)) return true
            }
        }
    }
    val tree = info.entries["file tree"] as? BencodeElement.BDict
    return tree != null && fileTreeEscapes(tree, emptyList())
}

private fun fileTreeEscapes(node: BencodeElement.BDict, parents: List<String>): Boolean {
    for ((name, child) in node.entries) {
        if (name.isEmpty()) continue
        val path = parents + name
        if (declaredPathEscapes(path)) return true
        if (child is BencodeElement.BDict && fileTreeEscapes(child, path)) return true
    }
    return false
}
