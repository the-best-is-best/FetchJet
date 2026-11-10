package com.michelle.raouf.fetch_jet.platform.desktop

import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

object DesktopFileSystem : FileSystem() {

    private fun native(path: Path): String = path.toString()

    override fun canonicalize(path: Path): Path {
        val full = DesktopOs.fullPath(native(path))
        if (!DesktopOs.exists(full)) throw FileNotFoundException("no such file: $path")
        return full.toPath()
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        val info = DesktopOs.fileInfo(native(path)) ?: return null
        return FileMetadata(
            isRegularFile = !info.isDirectory,
            isDirectory = info.isDirectory,
            size = info.size,
            lastModifiedAtMillis = info.lastWriteEpochMs,
        )
    }

    override fun list(dir: Path): List<Path> = listOrNull(dir)
        ?: if (DesktopOs.exists(native(dir))) throw IOException("failed to list $dir")
        else throw FileNotFoundException("no such file: $dir")

    override fun listOrNull(dir: Path): List<Path>? {
        if (!DesktopOs.isDirectory(native(dir))) return null
        return DesktopOs.listEntries(native(dir))?.map { dir / it.name }?.sorted()
    }

    override fun openReadOnly(file: Path): FileHandle = DesktopFileHandle(open(file, write = false, OsOpen.MUST_EXIST), false)

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        require(!mustCreate || !mustExist) { "Cannot require mustCreate and mustExist at the same time." }
        val mode = when {
            mustCreate -> OsOpen.MUST_CREATE
            mustExist -> OsOpen.MUST_EXIST
            else -> OsOpen.OPEN_OR_CREATE
        }
        return DesktopFileHandle(open(file, write = true, mode), true)
    }

    override fun source(file: Path): Source {
        val handle = openReadOnly(file)
        return handle.source().also { handle.close() }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        val handle = DesktopFileHandle(open(file, write = true, if (mustCreate) OsOpen.MUST_CREATE else OsOpen.TRUNCATE), true)
        return handle.sink().also { handle.close() }
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        val handle = openReadWrite(file, mustCreate = false, mustExist = mustExist)
        return handle.appendingSink().also { handle.close() }
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        val info = DesktopOs.fileInfo(native(dir))
        if (info != null) {
            if (mustCreate || !info.isDirectory) throw IOException("$dir already exists.")
            return
        }
        if (!DesktopOs.createDirectories(native(dir))) throw IOException("failed to create directory: $dir")
    }

    override fun atomicMove(source: Path, target: Path) {
        if (!DesktopOs.exists(native(source))) throw FileNotFoundException("no such file: $source")
        if (!DesktopOs.move(native(source), native(target), replace = true)) {
            throw IOException("failed to move $source to $target")
        }
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val info = DesktopOs.fileInfo(native(path))
        if (info == null) {
            if (mustExist) throw FileNotFoundException("no such file: $path")
            return
        }
        val ok = if (info.isDirectory) {
            if (DesktopOs.listEntries(native(path))?.isNotEmpty() == true) throw IOException("failed to delete $path: not empty")
            DesktopOs.deleteRecursively(native(path))
        } else {
            DesktopOs.deleteFile(native(path))
        }
        if (!ok) throw IOException("failed to delete $path")
    }

    override fun createSymlink(source: Path, target: Path) {
        throw IOException("unsupported")
    }

    override fun toString(): String = "DesktopFileSystem"

    private fun open(file: Path, write: Boolean, mode: OsOpen): OsFile =
        DesktopOs.openFile(native(file), write, mode) ?: when {
            mode == OsOpen.MUST_CREATE && DesktopOs.exists(native(file)) -> throw IOException("$file already exists.")
            mode == OsOpen.MUST_EXIST && !DesktopOs.exists(native(file)) -> throw FileNotFoundException("no such file: $file")
            else -> throw IOException("failed to open $file")
        }
}

private class DesktopFileHandle(private val file: OsFile, readWrite: Boolean) : FileHandle(readWrite) {
    override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int {
        val read = file.read(fileOffset, array, arrayOffset, byteCount)
        if (read < 0) throw IOException("read failed")
        return if (read == 0 && byteCount > 0) -1 else read
    }

    override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) {
        if (!file.write(fileOffset, array, arrayOffset, byteCount)) throw IOException("write failed")
    }

    override fun protectedFlush() = Unit

    override fun protectedResize(size: Long) {
        if (!file.resize(size)) throw IOException("resize failed")
    }

    override fun protectedSize(): Long {
        val size = file.size
        if (size < 0) throw IOException("size failed")
        return size
    }

    override fun protectedClose() {
        file.close()
    }
}
