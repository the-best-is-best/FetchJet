package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.derived.DerivedItems
import okio.Path

object SubtitleShift {
    private val timeLine = Regex("""^(\s*)((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})(\s*-->\s*)((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})(.*)$""")

    fun appliesTo(task: DownloadTask): Boolean = DerivedItems.isPromotedClip(task)

    fun shiftFile(path: Path, task: DownloadTask) {
        if (!appliesTo(task)) return
        runCatching {
            val text = AppFileSystem.read(path) { readUtf8() }
            val shifted = shift(text, path.name.substringAfterLast('.', "").lowercase(), task.cutStartMs, task.cutEndMs - task.cutStartMs)
            AppFileSystem.write(path) { writeUtf8(shifted) }
        }
    }

    fun shift(text: String, extension: String, offsetMs: Long, lengthMs: Long): String {
        val body = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return when (extension) {
            "ass", "ssa" -> shiftAss(body, offsetMs, lengthMs)
            else -> shiftSrtOrVtt(body, offsetMs, lengthMs, extension == "srt")
        }
    }

    private fun window(startMs: Long, endMs: Long, offsetMs: Long, lengthMs: Long): Pair<Long, Long>? {
        val start = (startMs - offsetMs).coerceAtLeast(0L)
        var end = endMs - offsetMs
        if (lengthMs > 0L) end = end.coerceAtMost(lengthMs)
        return if (end <= start) null else start to end
    }

    private fun shiftSrtOrVtt(body: String, offsetMs: Long, lengthMs: Long, renumber: Boolean): String {
        val blocks = body.split(Regex("\n{2,}"))
        val out = mutableListOf<String>()
        var number = 1
        for (block in blocks) {
            val lines = block.split('\n').toMutableList()
            val at = lines.indexOfFirst { timeLine.matches(it) }
            if (at < 0) {
                if (block.isNotBlank()) out.add(block)
                continue
            }
            val m = timeLine.matchEntire(lines[at]) ?: continue
            val start = parseClock(m.groupValues[2]) ?: continue
            val end = parseClock(m.groupValues[4]) ?: continue
            val (s, e) = window(start, end, offsetMs, lengthMs) ?: continue
            val comma = m.groupValues[2].contains(',')
            lines[at] = m.groupValues[1] + clock(s, comma) + m.groupValues[3] + clock(e, comma) + m.groupValues[5]
            if (renumber && at == 1 && lines[0].trim().all { it.isDigit() } && lines[0].isNotBlank()) {
                lines[0] = number.toString()
            }
            number++
            out.add(lines.joinToString("\n"))
        }
        return out.joinToString("\n\n") + "\n"
    }

    private fun shiftAss(body: String, offsetMs: Long, lengthMs: Long): String {
        var format = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        val out = mutableListOf<String>()
        for (line in body.split('\n')) {
            val t = line.trim()
            if (t.startsWith("Format:", ignoreCase = true) && t.contains("Text", ignoreCase = true)) {
                format = t.substringAfter(':').split(',').map { it.trim().lowercase() }
                out.add(line)
                continue
            }
            if (!t.startsWith("Dialogue:", ignoreCase = true)) {
                out.add(line)
                continue
            }
            val head = line.substringBefore(':') + ":"
            val parts = line.substringAfter(':').split(',', limit = format.size).toMutableList()
            val si = format.indexOf("start")
            val ei = format.indexOf("end")
            if (parts.size < format.size || si < 0 || ei < 0) {
                out.add(line)
                continue
            }
            val start = parseClock(parts[si].trim())
            val end = parseClock(parts[ei].trim())
            if (start == null || end == null) {
                out.add(line)
                continue
            }
            val (s, e) = window(start, end, offsetMs, lengthMs) ?: continue
            parts[si] = assClock(s)
            parts[ei] = assClock(e)
            out.add(head + parts.joinToString(","))
        }
        return out.joinToString("\n")
    }

    fun parseClock(text: String): Long? {
        val parts = text.trim().replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val secPart = parts.last()
        val sec = secPart.substringBefore('.').toLongOrNull() ?: return null
        val fraction = secPart.substringAfter('.', "")
        val milli = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLongOrNull() ?: return null
        val min = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return ((hours * 60 + min) * 60 + sec) * 1000L + milli
    }

    private fun pad(v: Long, n: Int = 2) = v.toString().padStart(n, '0')

    private fun clock(ms: Long, comma: Boolean): String {
        val h = ms / 3_600_000L
        val m = (ms % 3_600_000L) / 60_000L
        val s = (ms % 60_000L) / 1000L
        return "${pad(h)}:${pad(m)}:${pad(s)}${if (comma) ',' else '.'}${pad(ms % 1000L, 3)}"
    }

    private fun assClock(ms: Long): String {
        val h = ms / 3_600_000L
        val m = (ms % 3_600_000L) / 60_000L
        val s = (ms % 60_000L) / 1000L
        return "$h:${pad(m)}:${pad(s)}.${pad((ms % 1000L) / 10L)}"
    }
}
