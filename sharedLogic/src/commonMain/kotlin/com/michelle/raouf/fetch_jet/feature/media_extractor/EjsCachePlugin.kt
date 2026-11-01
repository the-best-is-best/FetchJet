package com.michelle.raouf.fetch_jet.feature.media_extractor

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

const val FETCHJET_EJS_CACHE_PLUGIN_PY =
    """from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP

EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True
"""

fun ensureEjsCachePluginDir(pluginDirsRoot: String): String? = try {
    val root = pluginDirsRoot.trimEnd('/', '\\').toPath()
    val file = root / "fetchjet" / "yt_dlp_plugins" / "extractor" / "fetchjet_ejs_cache.py"
    val fs = FileSystem.SYSTEM
    val current = if (fs.exists(file)) fs.read(file) { readUtf8() } else null
    if (current != FETCHJET_EJS_CACHE_PLUGIN_PY) {
        file.parent?.let { fs.createDirectories(it) }
        fs.write(file) { writeUtf8(FETCHJET_EJS_CACHE_PLUGIN_PY) }
    }
    root.toString()
} catch (_: Throwable) {
    null
}

fun pruneEjsPlayerCache(cacheDir: String, keep: Int = 2) {
    try {
        val dir = cacheDir.trimEnd('/', '\\').toPath() / "challenge-solver"
        val fs = FileSystem.SYSTEM
        if (!fs.exists(dir)) return
        fs.list(dir)
            .filter { it.name.startsWith("player") }
            .sortedByDescending { fs.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
            .drop(keep)
            .forEach { fs.delete(it) }
    } catch (_: Throwable) {
    }
}
