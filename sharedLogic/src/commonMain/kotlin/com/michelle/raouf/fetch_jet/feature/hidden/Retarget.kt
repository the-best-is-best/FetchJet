package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.feature.media_extractor.freePathNextTo
import okio.Path
import okio.Path.Companion.toPath

internal fun retargetDir(currentDir: String?, planned: Path): Path {
    val dir = currentDir.orEmpty()
    if (dir.isBlank() || sameDirectory(dir, planned.parent?.toString().orEmpty())) return planned
    return dir.trimEnd('/', '\\').toPath() / planned.name
}

internal fun retargetFinal(currentDir: String?, planned: Path): Path {
    val moved = retargetDir(currentDir, planned)
    if (moved == planned) return planned
    return if (AppFileSystem.exists(moved)) freePathNextTo(moved) else moved
}
