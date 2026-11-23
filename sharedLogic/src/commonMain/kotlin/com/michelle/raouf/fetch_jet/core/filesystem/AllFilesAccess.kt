package com.michelle.raouf.fetch_jet.core.filesystem

import okio.Path.Companion.toPath

object AllFilesAccess {
    var needed: () -> Boolean = { false }
    var openSettings: () -> Unit = {}

    fun canRead(path: String): Boolean = try {
        val source = AppFileSystem.source(path.toPath())
        try {
            source.read(okio.Buffer(), 1)
        } finally {
            source.close()
        }
        true
    } catch (_: Throwable) {
        false
    }

    fun canWriteInto(folder: String): Boolean = try {
        val dir = folder.trim().toPath()
        AppFileSystem.createDirectories(dir)
        val probe = dir / ".fetchjet-write-test"
        AppFileSystem.write(probe) { writeUtf8("x") }
        AppFileSystem.delete(probe)
        true
    } catch (_: Throwable) {
        false
    }
}
