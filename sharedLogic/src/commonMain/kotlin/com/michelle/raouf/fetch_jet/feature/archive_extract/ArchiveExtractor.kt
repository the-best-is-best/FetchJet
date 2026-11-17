package com.michelle.raouf.fetch_jet.feature.archive_extract

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.markExecutable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.BufferedSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use

enum class ArchiveKind { ZIP, TAR, TAR_GZ, TAR_XZ, GZ, XZ }

class ExtractLimits(
    val maxFiles: Int = 100_000,
    val maxBytes: Long = 64L * 1024 * 1024 * 1024,
    val ratioGuardFrom: Long = 4L * 1024 * 1024 * 1024,
    val maxRatio: Long = 2000,
)

class ExtractResult(
    val files: Int,
    val bytes: Long,
    val skipped: List<String>,
    val destination: String,
)

object ArchiveExtractor {
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

    private val SUFFIXES = listOf(
        ".tar.gz" to ArchiveKind.TAR_GZ,
        ".tgz" to ArchiveKind.TAR_GZ,
        ".tar.xz" to ArchiveKind.TAR_XZ,
        ".txz" to ArchiveKind.TAR_XZ,
        ".zip" to ArchiveKind.ZIP,
        ".tar" to ArchiveKind.TAR,
        ".gz" to ArchiveKind.GZ,
        ".xz" to ArchiveKind.XZ,
    )

    fun kindOf(fileName: String): ArchiveKind? {
        val lower = fileName.lowercase()
        return SUFFIXES.firstOrNull { lower.endsWith(it.first) }?.second
    }

    fun baseName(fileName: String): String {
        val lower = fileName.lowercase()
        val suffix = SUFFIXES.firstOrNull { lower.endsWith(it.first) }?.first ?: return fileName
        return fileName.dropLast(suffix.length).ifBlank { fileName }
    }

    fun safeParts(name: String): List<String>? {
        val unified = name.replace('\\', '/')
        if (unified.isEmpty() || unified.startsWith("/") || unified.any { it.code == 0 }) return null
        if (unified.length >= 2 && unified[1] == ':' && unified[0].isLetter()) return null
        val parts = ArrayList<String>()
        for (part in unified.split('/')) {
            if (part.isEmpty() || part == ".") continue
            if (part == "..") return null
            val cleaned = part.map { if (it.code < 32 || it in "<>:\"|?*") '_' else it }.joinToString("").trimEnd(' ', '.')
            if (cleaned.isEmpty()) return null
            val clean = if (cleaned.substringBefore('.').uppercase() in RESERVED_NAMES) "_$cleaned" else cleaned
            parts.add(clean)
        }
        return parts.ifEmpty { null }
    }

    private fun uniquePath(parent: Path, name: String): Path {
        var candidate = parent / name
        if (!AppFileSystem.exists(candidate)) return candidate
        val cut = name.lastIndexOf('.')
        val stem = if (cut > 0) name.substring(0, cut) else name
        val ext = if (cut > 0) name.substring(cut) else ""
        var n = 1
        while (true) {
            candidate = parent / "$stem ($n)$ext"
            if (!AppFileSystem.exists(candidate)) return candidate
            n++
        }
    }

    private class CountingSource(private val upstream: Source, private val total: Long, private val report: (Float) -> Unit) : Source {
        private var read = 0L

        override fun read(sink: okio.Buffer, byteCount: Long): Long {
            val n = upstream.read(sink, byteCount)
            if (n > 0 && total > 0) {
                read += n
                report((read.toFloat() / total).coerceIn(0f, 1f))
            }
            return n
        }

        override fun timeout() = upstream.timeout()
        override fun close() = upstream.close()
    }

    private class Run(val limits: ExtractLimits, val archiveSize: Long, val root: Path, val onProgress: (Float) -> Unit) {
        var progressTotal = 0L
        var files = 0
        var bytes = 0L
        val skipped = ArrayList<String>()
        val made = HashSet<Path>()

        fun folder(path: Path) {
            if (made.add(path)) AppFileSystem.createDirectories(path)
        }

        suspend fun write(parts: List<String>, source: Source, declared: Long, expectedCrc: Long?, executable: Boolean = false) {
            if (files >= limits.maxFiles) throw ArchiveException("the archive holds too many files")
            var target = root
            for (i in 0 until parts.size - 1) target = target / parts[i]
            folder(target)
            val file = target / parts.last()
            val crc = Crc32()
            var written = 0L
            val chunk = ByteArray(65536)
            val input = source.buffer()
            var complete = false
            try {
                AppFileSystem.sink(file).buffer().use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(chunk)
                        if (read < 0) break
                        written += read
                        bytes += read
                        if (declared >= 0 && written > declared) throw ArchiveException("the archive is damaged (a file is larger than it says)")
                        if (bytes > limits.maxBytes) throw ArchiveException("the archive is larger than the limit")
                        if (bytes > limits.ratioGuardFrom && archiveSize > 0 && bytes / archiveSize > limits.maxRatio) {
                            throw ArchiveException("the archive expands too much (it looks like a zip bomb)")
                        }
                        crc.update(chunk, 0, read)
                        out.write(chunk, 0, read)
                        if (progressTotal > 0) onProgress((bytes.toFloat() / progressTotal).coerceIn(0f, 1f))
                    }
                }
                if (declared >= 0 && written != declared) throw ArchiveException("the archive is damaged (a file is smaller than it says)")
                if (expectedCrc != null && crc.result() != expectedCrc) throw ArchiveException("the file ${parts.last()} is damaged (checksum mismatch)")
                complete = true
                if (executable) runCatching { markExecutable(file.toString()) }
            } finally {
                input.close()
                if (!complete) try {
                    AppFileSystem.delete(file)
                } catch (_: Throwable) {
                }
            }
            files++
        }
    }

    suspend fun extract(
        archive: Path,
        limits: ExtractLimits = ExtractLimits(),
        onProgress: (Float) -> Unit = {},
    ): ExtractResult {
        val name = archive.name
        val kind = kindOf(name) ?: throw ArchiveException("this kind of file cannot be extracted")
        val parent = archive.parent ?: throw ArchiveException("the archive has no folder")
        val base = baseName(name)
        val work = uniquePath(parent, ".$base.fetchjet-extract")
        AppFileSystem.createDirectories(work)
        val size = AppFileSystem.metadata(archive).size ?: 0L
        val run = Run(limits, size, work, onProgress)
        try {
            when (kind) {
                ArchiveKind.ZIP -> extractZip(archive, run)
                ArchiveKind.TAR -> extractTar(counted(archive, size, onProgress).buffer(), run)
                ArchiveKind.TAR_GZ -> extractTar(GzipSource(counted(archive, size, onProgress).buffer()).buffer(), run)
                ArchiveKind.TAR_XZ -> extractTar(XzSource(counted(archive, size, onProgress).buffer()).buffer(), run)
                ArchiveKind.GZ -> single(GzipSource(counted(archive, size, onProgress).buffer()), base, run)
                ArchiveKind.XZ -> single(XzSource(counted(archive, size, onProgress).buffer()), base, run)
            }
            if (run.files == 0 && run.made.isEmpty()) throw ArchiveException("the archive is empty")
            val top = AppFileSystem.list(work)
            val destination = if (top.size == 1) {
                uniquePath(parent, top[0].name).also { AppFileSystem.atomicMove(top[0], it) }
            } else {
                uniquePath(parent, base).also { AppFileSystem.atomicMove(work, it) }
            }
            try {
                AppFileSystem.deleteRecursively(work, mustExist = false)
            } catch (_: Throwable) {
            }
            return ExtractResult(run.files, run.bytes, run.skipped, destination.toString())
        } catch (e: Throwable) {
            try {
                AppFileSystem.deleteRecursively(work, mustExist = false)
            } catch (_: Throwable) {
            }
            throw when (e) {
                is ArchiveException, is kotlinx.coroutines.CancellationException -> e
                is okio.EOFException -> ArchiveException("the archive ends too early")
                else -> ArchiveException(e.message ?: "extraction failed")
            }
        }
    }

    private fun counted(archive: Path, size: Long, onProgress: (Float) -> Unit): Source =
        CountingSource(AppFileSystem.source(archive), size, onProgress)

    private suspend fun single(source: Source, base: String, run: Run) {
        val parts = safeParts(base) ?: listOf("file")
        run.write(parts, source, -1, null)
    }

    private suspend fun extractTar(source: BufferedSource, run: Run) {
        source.use {
            val reader = TarReader(source)
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = reader.next() ?: break
                val parts = safeParts(entry.name)
                if (parts == null) {
                    run.skipped.add(entry.name)
                    continue
                }
                if (entry.isDirectory) {
                    var dir = run.root
                    for (part in parts) dir = dir / part
                    run.folder(dir)
                } else if (entry.isFile) {
                    val sink = okio.Buffer()
                    val piece = object : Source {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long {
                            val n = reader.copyTo(sink, minOf(byteCount, 65536L))
                            return if (n == 0L) -1 else n
                        }

                        override fun timeout() = source.timeout()
                        override fun close() {}
                    }
                    run.write(parts, piece, entry.size, null, entry.executable)
                    sink.clear()
                } else {
                    run.skipped.add(entry.name)
                }
            }
        }
    }

    private suspend fun extractZip(archive: Path, run: Run) {
        AppFileSystem.openReadOnly(archive).use { handle ->
            val reader = ZipReader(handle)
            val entries = reader.entries()
            run.progressTotal = entries.filter { !it.isDirectory && !it.isLink }.sumOf { it.size }
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                if (entry.encrypted) throw ArchiveException("this zip file has a password")
                val parts = safeParts(entry.name)
                if (parts == null || entry.isLink) {
                    run.skipped.add(entry.name)
                    continue
                }
                if (entry.isDirectory) {
                    var dir = run.root
                    for (part in parts) dir = dir / part
                    run.folder(dir)
                    continue
                }
                run.write(parts, reader.open(entry), entry.size, entry.crc, entry.executable)
            }
        }
    }
}
