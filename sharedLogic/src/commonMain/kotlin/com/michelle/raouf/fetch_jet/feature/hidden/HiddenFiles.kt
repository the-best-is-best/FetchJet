package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.feature.http_downloader.copyFileInBlocks
import okio.Path
import okio.Path.Companion.toPath

enum class HiddenMove {
    MOVED,
    NOTHING_TO_MOVE,
    COPIED_SOURCE_KEPT,
    FAILED
}

internal fun normalizedDir(path: String): String = path.replace('\\', '/').trimEnd('/')

internal fun sameDirectory(a: String, b: String): Boolean = normalizedDir(a) == normalizedDir(b)

object HiddenFiles {
    const val FOLDER = "hidden_files"

    fun root(pathResolver: PathResolver): String = "${normalizedDir(pathResolver.getSettingsDirectory())}/$FOLDER"

    fun dirFor(pathResolver: PathResolver, taskId: String): String = "${root(pathResolver)}/$taskId"

    fun isPrivate(pathResolver: PathResolver, path: String): Boolean =
        path.isNotBlank() && normalizedDir(path).startsWith(root(pathResolver) + "/")

    fun prepare(pathResolver: PathResolver, taskId: String): String {
        val dir = dirFor(pathResolver, taskId)
        try {
            AppFileSystem.createDirectories(dir.toPath())
            val marker = "${root(pathResolver)}/.nomedia".toPath()
            if (!AppFileSystem.exists(marker)) AppFileSystem.write(marker) { }
        } catch (_: Exception) {
        }
        return dir
    }

    fun deleteDirFor(pathResolver: PathResolver, taskId: String) {
        try {
            val dir = dirFor(pathResolver, taskId).toPath()
            if (AppFileSystem.exists(dir)) AppFileSystem.deleteRecursively(dir)
        } catch (_: Exception) {
        }
    }

    fun move(from: Path, to: Path): HiddenMove {
        if (!AppFileSystem.exists(from)) return HiddenMove.NOTHING_TO_MOVE
        return try {
            to.parent?.let { AppFileSystem.createDirectories(it) }
            val renamed = runCatching { AppFileSystem.atomicMove(from, to) }.isSuccess
            if (renamed) return HiddenMove.MOVED
            val isDir = AppFileSystem.metadataOrNull(from)?.isDirectory == true
            try {
                if (isDir) copyTree(from, to) else copyFileInBlocks(from, to)
            } catch (e: Exception) {
                runCatching { if (AppFileSystem.exists(to)) AppFileSystem.deleteRecursively(to) }
                return HiddenMove.FAILED
            }
            val removed = runCatching {
                if (isDir) AppFileSystem.deleteRecursively(from) else AppFileSystem.delete(from)
            }.isSuccess && !AppFileSystem.exists(from)
            if (removed) HiddenMove.MOVED else HiddenMove.COPIED_SOURCE_KEPT
        } catch (_: Exception) {
            HiddenMove.FAILED
        }
    }

    private fun copyTree(from: Path, to: Path) {
        AppFileSystem.createDirectories(to)
        for (child in AppFileSystem.list(from)) {
            val target = to / child.name
            if (AppFileSystem.metadataOrNull(child)?.isDirectory == true) copyTree(child, target)
            else copyFileInBlocks(child, target)
        }
    }
}
