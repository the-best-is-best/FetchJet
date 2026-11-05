package com.michelle.raouf.fetch_jet.core.settings

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * Small on/off behaviours of the app (settings folder, behavior.properties), read anywhere
 * through the flows. Kept apart from [AppSettings] so adding one does not touch every screen.
 */
object BehaviorSettings {
    private val file get() = "${PathResolver().getSettingsDirectory()}/behavior.properties".toPath()

    private val _monitorClipboard =
        MutableStateFlow(read("monitorClipboard")?.toBooleanStrictOrNull() ?: false)

    /** Watch the clipboard for links to files / magnets and offer to download them. */
    val monitorClipboard: StateFlow<Boolean> = _monitorClipboard.asStateFlow()

    private val _clipboardVideoPages =
        MutableStateFlow(read("clipboardVideoPages")?.toBooleanStrictOrNull() ?: false)

    /** Also offer video pages (YouTube, Facebook, TikTok…) copied to the clipboard, not only files. */
    val clipboardVideoPages: StateFlow<Boolean> = _clipboardVideoPages.asStateFlow()

    private val _sortIntoFolders =
        MutableStateFlow(read("sortIntoFolders")?.toBooleanStrictOrNull() ?: false)

    /** Save into Video / Music / Archives / Documents / Programs / Images sub-folders by file type. */
    val sortIntoFolders: StateFlow<Boolean> = _sortIntoFolders.asStateFlow()

    fun setMonitorClipboard(on: Boolean) {
        _monitorClipboard.value = on; write("monitorClipboard", on.toString())
    }

    fun setClipboardVideoPages(on: Boolean) {
        _clipboardVideoPages.value = on; write("clipboardVideoPages", on.toString())
    }

    fun setSortIntoFolders(on: Boolean) {
        _sortIntoFolders.value = on; write("sortIntoFolders", on.toString())
    }

    private val _connections =
        MutableStateFlow(read("connections")?.toIntOrNull()?.coerceIn(0, 32) ?: 0)

    /** Connections per direct download; 0 = automatic (by file size). */
    val connections: StateFlow<Int> = _connections.asStateFlow()

    fun setConnections(n: Int) {
        val v = n.coerceIn(0, 32); _connections.value = v; write("connections", v.toString())
    }

    private fun read(key: String): String? = try {
        if (!FileSystem.SYSTEM.exists(file)) null
        else FileSystem.SYSTEM.read(file) { readUtf8() }.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()
    } catch (_: Throwable) {
        null
    }

    private fun write(key: String, value: String) {
        try {
            val lines = if (FileSystem.SYSTEM.exists(file))
                FileSystem.SYSTEM.read(file) { readUtf8() }.lineSequence()
                    .filter { it.isNotBlank() && !it.trim().startsWith("$key=") }.toMutableList()
            else mutableListOf("# FetchJet behaviour settings")
            lines.add("$key=$value")
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) { writeUtf8(lines.joinToString("\n") + "\n") }
        } catch (_: Throwable) {
        }
    }
}

/** Which sub-folder a download goes to when "Sort into folders" is on. */
object FileCategory {
    private val video =
        setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "ts", "3gp")
    private val music = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma", "aiff")
    private val archives =
        setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "img", "cab")
    private val documents = setOf(
        "pdf",
        "epub",
        "mobi",
        "doc",
        "docx",
        "xls",
        "xlsx",
        "ppt",
        "pptx",
        "odt",
        "ods",
        "odp",
        "txt",
        "rtf",
        "csv",
        "md"
    )
    private val programs = setOf(
        "exe",
        "msi",
        "msix",
        "appx",
        "apk",
        "aab",
        "dmg",
        "pkg",
        "deb",
        "rpm",
        "appimage",
        "jar",
        "ipa",
        "xpi",
        "crx"
    )
    private val images =
        setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "avif", "tiff", "psd")

    /** Every folder name [folderFor] can return. */
    val allFolders =
        listOf("Video", "Music", "Archives", "Documents", "Programs", "Images", "Torrents", "Other")

    /**
     * [path] without any trailing type folder(s): "…\Downloads\Video\Archives" → "…\Downloads".
     * Stops at a folder in [keep] (the system's Downloads / Documents / Desktop): on iOS the
     * download folder is the app's "Documents", which is not a type folder there.
     */
    fun stripTypeFolders(path: String, keep: Collection<String> = emptyList()): String {
        val kept = keep.map { it.trimEnd('/', '\\') }.filter { it.isNotEmpty() }.toSet()
        var p = path.trimEnd('/', '\\')
        while (true) {
            if (p in kept) return p
            val hit =
                allFolders.firstOrNull { p.endsWith("/$it") || p.endsWith("\\$it") } ?: return p
            p = p.dropLast(hit.length + 1).trimEnd('/', '\\')
        }
    }

    /**
     * Folder name for [fileName] (null = keep the chosen folder). [isMedia] = a video/audio
     * download whose final extension may not be known yet; [isTorrent] = a torrent.
     */
    fun folderFor(fileName: String, isMedia: Boolean = false, isTorrent: Boolean = false): String? {
        if (isTorrent) return "Torrents"
        val ext = fileName.substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 2..8 && it.all { c -> c.isLetterOrDigit() } }
        return when {
            ext in video -> "Video"
            ext in music -> "Music"
            ext in archives -> "Archives"
            ext in documents -> "Documents"
            ext in programs -> "Programs"
            ext in images -> "Images"
            isMedia -> "Video"
            else -> null
        }
    }
}

/** True when [text] is one http(s) link to a file worth offering to download, or a magnet link. */
object ClipboardLinks {
    private val fileExts = setOf(
        "zip",
        "rar",
        "7z",
        "tar",
        "gz",
        "tgz",
        "bz2",
        "xz",
        "iso",
        "img",
        "exe",
        "msi",
        "msix",
        "apk",
        "dmg",
        "pkg",
        "deb",
        "rpm",
        "appimage",
        "mp4",
        "mkv",
        "avi",
        "mov",
        "wmv",
        "flv",
        "webm",
        "m4v",
        "mp3",
        "wav",
        "flac",
        "aac",
        "m4a",
        "ogg",
        "opus",
        "pdf",
        "epub",
        "doc",
        "docx",
        "xls",
        "xlsx",
        "ppt",
        "pptx",
        "m3u8",
        "mpd",
        "bin",
        "torrent",
        "jar",
        "ipa"
    )

    private val videoHosts = listOf(
        "youtube.com",
        "youtu.be",
        "vimeo.com",
        "tiktok.com",
        "instagram.com",
        "facebook.com",
        "fb.watch",
        "twitter.com",
        "x.com",
        "twitch.tv",
        "dailymotion.com",
        "soundcloud.com",
        "reddit.com",
        "rumble.com",
        "bilibili.com",
        "streamable.com",
        "ok.ru",
        "vk.com",
        "odysee.com",
        "9gag.com",
        "kick.com",
        "mixcloud.com"
    )

    fun downloadableUrl(text: String?, videoPages: Boolean = false): String? {
        val t = text?.trim() ?: return null
        if (t.isEmpty() || t.length > 4096 || t.any { it.isWhitespace() }) return null
        if (t.startsWith("magnet:?", ignoreCase = true) && t.contains(
                "xt=urn:btih:",
                ignoreCase = true
            )
        ) return t
        if (!(t.startsWith("http://", true) || t.startsWith("https://", true))) return null
        val path = t.substringAfter("://").substringBefore('?').substringBefore('#')
        if (!path.contains('/')) return null
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in fileExts) return t
        if (videoPages) {
            val host = path.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
            // a video page, not the site's front page
            if (videoHosts.any { host == it || host.endsWith(".$it") } && path.substringAfter('/').length > 3) return t
        }
        return null
    }
}
