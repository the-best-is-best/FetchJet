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

enum class ArchiveKind(val external: Boolean = false) { ZIP, TAR, TAR_GZ, TAR_XZ, GZ, XZ, SEVEN_Z(true), RAR(true), SPLIT_ZIP(true) }

enum class ExtractPlace { SMART, ALWAYS_FOLDER, NEXT_TO_ARCHIVE }

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
    val items: List<String> = listOf(destination),
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
        ".7z" to ArchiveKind.SEVEN_Z,
        ".rar" to ArchiveKind.RAR,
    )

    private val VOLUME = Regex("""^(.*)(\.(7z|zip)\.(\d{3})|\.part(\d+)\.rar)$""", RegexOption.IGNORE_CASE)

    const val MISSING_PART = "a part of this split archive is missing"

    fun isVolume(fileName: String): Boolean = VOLUME.matches(fileName)

    fun volumeNumber(fileName: String): Int? {
        val m = VOLUME.matchEntire(fileName) ?: return null
        return (m.groupValues[4].ifEmpty { m.groupValues[5] }).toIntOrNull()
    }

    fun volumeSet(fileName: String): String? {
        val m = VOLUME.matchEntire(fileName) ?: return null
        val kind = if (m.groupValues[3].isNotEmpty()) m.groupValues[3].lowercase() else "rar"
        return m.groupValues[1].lowercase() + "|" + kind
    }

    fun kindOf(fileName: String): ArchiveKind? {
        val m = VOLUME.matchEntire(fileName)
        if (m != null) {
            if (volumeNumber(fileName) != 1) return null
            return when {
                m.groupValues[3].equals("7z", true) -> ArchiveKind.SEVEN_Z
                m.groupValues[3].equals("zip", true) -> ArchiveKind.SPLIT_ZIP
                else -> ArchiveKind.RAR
            }
        }
        val lower = fileName.lowercase()
        return SUFFIXES.firstOrNull { lower.endsWith(it.first) }?.second
    }

    fun baseName(fileName: String): String {
        VOLUME.matchEntire(fileName)?.let { return it.groupValues[1].ifBlank { fileName } }
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

        fun reset() {
            files = 0
            bytes = 0L
            skipped.clear()
            made.clear()
        }

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
        password: String? = null,
        place: ExtractPlace = ExtractPlace.SMART,
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
                ArchiveKind.SEVEN_Z, ArchiveKind.RAR, ArchiveKind.SPLIT_ZIP -> external(archive, work, password, run)
                ArchiveKind.ZIP -> try {
                    if (password != null && ExternalArchiver.available()) external(archive, work, password, run) else extractZip(archive, run)
                } catch (e: ArchivePasswordException) {
                    if (!ExternalArchiver.available()) throw ArchiveException("this zip file has a password: install 7-Zip from 7-zip.org to open it")
                    AppFileSystem.deleteRecursively(work, mustExist = false)
                    AppFileSystem.createDirectories(work)
                    run.reset()
                    external(archive, work, password, run)
                }
                ArchiveKind.TAR -> extractTar(counted(archive, size, onProgress).buffer(), run)
                ArchiveKind.TAR_GZ -> extractTar(GzipSource(counted(archive, size, onProgress).buffer()).buffer(), run)
                ArchiveKind.TAR_XZ -> extractTar(XzSource(counted(archive, size, onProgress).buffer()).buffer(), run)
                ArchiveKind.GZ -> single(GzipSource(counted(archive, size, onProgress).buffer()), base, run)
                ArchiveKind.XZ -> single(XzSource(counted(archive, size, onProgress).buffer()), base, run)
            }
            if (run.files == 0 && run.made.isEmpty()) throw ArchiveException("the archive is empty")
            val top = AppFileSystem.list(work)
            val moved = ArrayList<Path>()
            val destination = when {
                place == ExtractPlace.NEXT_TO_ARCHIVE -> {
                    for (item in top) moved += uniquePath(parent, item.name).also { AppFileSystem.atomicMove(item, it) }
                    parent
                }

                top.size == 1 && place == ExtractPlace.SMART ->
                    uniquePath(parent, top[0].name).also { AppFileSystem.atomicMove(top[0], it); moved += it }

                else -> uniquePath(parent, base).also { AppFileSystem.atomicMove(work, it); moved += it }
            }
            try {
                AppFileSystem.deleteRecursively(work, mustExist = false)
            } catch (_: Throwable) {
            }
            return ExtractResult(run.files, run.bytes, run.skipped, destination.toString(), moved.map { it.toString() })
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

    internal fun checkListing(entries: List<ExternalEntry>, limits: ExtractLimits): Long {
        var files = 0
        var bytes = 0L
        for (entry in entries) {
            if (entry.isLink) throw ArchiveException("the archive holds a link (${entry.path.take(120)}): it is not extracted")
            if (!isSafeEntryPath(entry.path)) throw ArchiveException("the archive holds an unsafe path (${entry.path.take(120)}): it is not extracted")
            if (entry.isDirectory) continue
            files++
            bytes += entry.size.coerceAtLeast(0L)
            if (files > limits.maxFiles) throw ArchiveException("the archive holds too many files")
            if (bytes > limits.maxBytes) throw ArchiveException("the archive is larger than the limit")
        }
        return bytes
    }

    internal fun isSafeEntryPath(name: String): Boolean {
        val unified = name.replace('\\', '/')
        if (unified.isEmpty() || unified.startsWith("/") || unified.any { it.code == 0 }) return false
        if (unified.length >= 2 && unified[1] == ':' && unified[0].isLetter()) return false
        return unified.split('/').none { it == ".." }
    }

    private suspend fun external(archive: Path, work: Path, password: String?, run: Run) {
        checkListing(listWithExternalArchiver(archive.toString(), password), run.limits)
        ExternalArchiver.extract(archive.toString(), work.toString(), password, run.onProgress)
        for (path in AppFileSystem.listRecursively(work).toList()) {
            val meta = AppFileSystem.metadataOrNull(path) ?: continue
            if (meta.symlinkTarget != null) {
                run.skipped.add(path.name)
                AppFileSystem.delete(path, mustExist = false)
                continue
            }
            if (meta.isDirectory) {
                run.made.add(path)
                continue
            }
            run.files++
            run.bytes += meta.size ?: 0L
            if (run.files > run.limits.maxFiles) throw ArchiveException("the archive holds too many files")
            if (run.bytes > run.limits.maxBytes) throw ArchiveException("the archive is larger than the limit")
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
                if (entry.encrypted) throw ArchivePasswordException(wrong = false)
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
