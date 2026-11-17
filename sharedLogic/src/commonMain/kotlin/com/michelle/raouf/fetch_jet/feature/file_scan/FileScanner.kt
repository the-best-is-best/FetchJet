package com.michelle.raouf.fetch_jet.feature.file_scan

enum class ScanOutcome(val code: String) {
    CLEAN("clean"),
    THREAT("threat"),
    BLOCKED("blocked"),
    FAILED("failed"),
    NO_SCANNER("none");

    companion object {
        fun of(code: String?): ScanOutcome? = entries.firstOrNull { it.code == code }
    }
}

class ScanResult(val outcome: ScanOutcome, val detail: String = "")

class ScanEvent(val name: String, val outcome: ScanOutcome?, val detail: String = "", val started: Boolean = false)

expect object FileScanner {
    val supported: Boolean
    val hasSystemScanner: Boolean
    suspend fun scan(path: String, sourceUrl: String, command: String): ScanResult
    fun markFromInternet(path: String, sourceUrl: String, pageUrl: String?)
}

object ScanCommand {
    const val FILE_TOKEN = "[File]"

    fun build(command: String, file: String): List<String> {
        val parts = split(command)
        if (parts.isEmpty()) return emptyList()
        var used = false
        val out = parts.map { part ->
            if (part.contains(FILE_TOKEN)) {
                used = true
                part.replace(FILE_TOKEN, file)
            } else part
        }
        return if (used) out else out + file
    }

    fun split(command: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var any = false
        for (c in command.trim()) {
            when {
                c == '"' -> { quoted = !quoted; any = true }
                c.isWhitespace() && !quoted -> {
                    if (any) out.add(current.toString())
                    current.clear(); any = false
                }
                else -> { current.append(c); any = true }
            }
        }
        if (any) out.add(current.toString())
        return out
    }
}

const val ANTIVIRUS_BLOCKED_ERROR = "The antivirus blocked this file: it found a threat in it"

class AntivirusBlockedException : okio.IOException(ANTIVIRUS_BLOCKED_ERROR)
