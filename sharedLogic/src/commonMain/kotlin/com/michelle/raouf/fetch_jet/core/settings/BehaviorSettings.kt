package com.michelle.raouf.fetch_jet.core.settings

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Path.Companion.toPath

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

    private val _groupByFolder =
        MutableStateFlow(read("groupByFolder")?.toBooleanStrictOrNull() ?: false)

    /** The downloads list shows each destination folder as one row (playlists stay their own rows). */
    val groupByFolder: StateFlow<Boolean> = _groupByFolder.asStateFlow()

    fun setGroupByFolder(on: Boolean) {
        _groupByFolder.value = on; write("groupByFolder", on.toString())
    }

    private val _desktopPlayer = MutableStateFlow(read("desktopPlayer")?.takeIf { it in DESKTOP_PLAYER_MODES } ?: "ask")

    val desktopPlayer: StateFlow<String> = _desktopPlayer.asStateFlow()

    val DESKTOP_PLAYER_MODES: List<String> get() = listOf("ask", "on", "off")

    fun setDesktopPlayer(mode: String) {
        if (mode !in DESKTOP_PLAYER_MODES) return
        _desktopPlayer.value = mode; write("desktopPlayer", mode)
    }

    private val _videoPlaysEverywhere =
        MutableStateFlow(read("videoPlaysEverywhere")?.toBooleanStrictOrNull() ?: false)

    /**
     * Videos in H.264 + AAC, which every player opens. Off (default): the smaller AV1 / VP9 +
     * Opus of the same quality; either way the other one is used when the first is missing.
     */
    val videoPlaysEverywhere: StateFlow<Boolean> = _videoPlaysEverywhere.asStateFlow()

    fun setVideoPlaysEverywhere(on: Boolean) {
        _videoPlaysEverywhere.value = on; write("videoPlaysEverywhere", on.toString())
    }

    private val _extractArchives =
        MutableStateFlow(read("extractArchives")?.toBooleanStrictOrNull() ?: false)

    /** Open zip / tar / gz / xz files next to the archive when their download finishes. */
    val extractArchives: StateFlow<Boolean> = _extractArchives.asStateFlow()

    fun setExtractArchives(on: Boolean) {
        _extractArchives.value = on; write("extractArchives", on.toString())
    }

    private val _extractPlace = MutableStateFlow(
        read("extractPlace")?.let { v -> com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace.entries.firstOrNull { it.name == v } }
            ?: com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace.SMART
    )

    val extractPlace: StateFlow<com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace> = _extractPlace.asStateFlow()

    fun setExtractPlace(place: com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace) {
        _extractPlace.value = place; write("extractPlace", place.name)
    }

    private val _deleteToTrash =
        MutableStateFlow(read("deleteToTrash")?.toBooleanStrictOrNull() ?: true)

    val deleteToTrash: StateFlow<Boolean> = _deleteToTrash.asStateFlow()

    fun setDeleteToTrash(on: Boolean) {
        _deleteToTrash.value = on; write("deleteToTrash", on.toString())
    }

    private val _trashArchiveAfterExtract =
        MutableStateFlow(read("trashArchiveAfterExtract")?.toBooleanStrictOrNull() ?: false)

    val trashArchiveAfterExtract: StateFlow<Boolean> = _trashArchiveAfterExtract.asStateFlow()

    fun setTrashArchiveAfterExtract(on: Boolean) {
        _trashArchiveAfterExtract.value = on; write("trashArchiveAfterExtract", on.toString())
    }

    private val _scanFiles =
        MutableStateFlow(read("scanFiles")?.toBooleanStrictOrNull() ?: false)

    val scanFiles: StateFlow<Boolean> = _scanFiles.asStateFlow()

    fun setScanFiles(on: Boolean) {
        _scanFiles.value = on; write("scanFiles", on.toString())
    }

    private val _scanCommand = MutableStateFlow(read("scanCommand").orEmpty())

    val scanCommand: StateFlow<String> = _scanCommand.asStateFlow()

    fun setScanCommand(command: String) {
        val clean = command.lines().joinToString(" ")
        _scanCommand.value = clean; write("scanCommand", clean)
    }

    private val _visibleToDevices =
        MutableStateFlow(read("visibleToDevices")?.toBooleanStrictOrNull() ?: false)

    /** Device sync: other FetchJet devices on the network can find this one and send to it. */
    val visibleToDevices: StateFlow<Boolean> = _visibleToDevices.asStateFlow()

    fun setVisibleToDevices(on: Boolean) {
        _visibleToDevices.value = on; write("visibleToDevices", on.toString())
    }

    private val _wakeForSchedule =
        MutableStateFlow(read("wakeForSchedule")?.toBooleanStrictOrNull() ?: false)

    val wakeForSchedule: StateFlow<Boolean> = _wakeForSchedule.asStateFlow()

    fun setWakeForSchedule(on: Boolean) {
        _wakeForSchedule.value = on; write("wakeForSchedule", on.toString())
    }

    private val _connections =
        MutableStateFlow(read("connections")?.toIntOrNull()?.coerceIn(0, 32) ?: 0)

    /** Connections per direct download; 0 = automatic (by file size). */
    val connections: StateFlow<Int> = _connections.asStateFlow()

    fun setConnections(n: Int) {
        val v = n.coerceIn(0, 32); _connections.value = v; write("connections", v.toString())
    }

    private val _askedMainDownloader =
        MutableStateFlow(read("askedMainDownloader")?.toBooleanStrictOrNull() ?: false)

    /** The first-run "Make FetchJet your main downloader" question was answered (either way). */
    val askedMainDownloader: StateFlow<Boolean> = _askedMainDownloader.asStateFlow()

    fun setAskedMainDownloader(on: Boolean) {
        _askedMainDownloader.value = on; write("askedMainDownloader", on.toString())
    }

    private val _speedLimitOnlyInUse =
        MutableStateFlow(read("speedLimitOnlyInUse")?.toBooleanStrictOrNull() ?: false)

    val speedLimitOnlyInUse: StateFlow<Boolean> = _speedLimitOnlyInUse.asStateFlow()

    fun setSpeedLimitOnlyInUse(on: Boolean) {
        _speedLimitOnlyInUse.value = on; write("speedLimitOnlyInUse", on.toString())
    }

    private val _torrentStopRatio =
        MutableStateFlow(
            read("torrentStopRatio")?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(0f, 100f) ?: 0f
        )

    val torrentStopRatio: StateFlow<Float> = _torrentStopRatio.asStateFlow()

    fun setTorrentStopRatio(value: Float) {
        val v = if (value.isNaN()) 0f else value.coerceIn(0f, 100f)
        _torrentStopRatio.value = v; write("torrentStopRatio", v.toString())
    }

    private val _categoryFolders = MutableStateFlow(
        FileCategory.allFolders.mapNotNull { category ->
            read("folder.$category")?.takeIf { it.isNotBlank() }
                ?.let { category to com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath(it) }
        }.toMap()
    )

    val categoryFolders: StateFlow<Map<String, String>> = _categoryFolders.asStateFlow()

    fun categoryFolder(category: String): String? = _categoryFolders.value[category]

    fun setCategoryFolder(category: String, path: String) {
        if (category !in FileCategory.allFolders) return
        val folder = path.trim()
        if ((_categoryFolders.value[category] ?: "") == folder) return
        _categoryFolders.value =
            if (folder.isEmpty()) _categoryFolders.value - category else _categoryFolders.value + (category to folder)
        write("folder.$category", folder)
    }

    private val _siteFolders = MutableStateFlow(
        SiteFolderRules.decode(read("siteFolders")).map {
            it.copy(folder = com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath(it.folder))
        }
    )

    val siteFolders: StateFlow<List<SiteFolderRule>> = _siteFolders.asStateFlow()

    fun siteFolderFor(vararg urls: String?): SiteFolderRule? = SiteFolderRules.match(_siteFolders.value, *urls)

    fun setSiteFolders(rules: List<SiteFolderRule>) {
        val clean = rules.map { SiteFolderRule(SiteFolderRules.cleanSite(it.site), it.folder.trim()) }
            .filter { SiteFolderRules.isValidSite(it.site) && it.folder.isNotBlank() }
            .distinctBy { it.site }
        if (clean == _siteFolders.value) return
        _siteFolders.value = clean
        write("siteFolders", SiteFolderRules.encode(clean))
    }

    fun addSiteFolder(site: String, folder: String): Boolean {
        val clean = SiteFolderRules.cleanSite(site)
        if (!SiteFolderRules.isValidSite(clean) || folder.isBlank()) return false
        setSiteFolders(_siteFolders.value.filter { it.site != clean } + SiteFolderRule(clean, folder.trim()))
        return true
    }

    fun removeSiteFolder(site: String) = setSiteFolders(_siteFolders.value.filter { it.site != site })

    fun snapshot(): Map<String, String> = buildMap {
        put("monitorClipboard", _monitorClipboard.value.toString())
        put("clipboardVideoPages", _clipboardVideoPages.value.toString())
        put("sortIntoFolders", _sortIntoFolders.value.toString())
        put("groupByFolder", _groupByFolder.value.toString())
        put("videoPlaysEverywhere", _videoPlaysEverywhere.value.toString())
        put("extractArchives", _extractArchives.value.toString())
        put("extractPlace", _extractPlace.value.name)
        put("deleteToTrash", _deleteToTrash.value.toString())
        put("trashArchiveAfterExtract", _trashArchiveAfterExtract.value.toString())
        put("scanFiles", _scanFiles.value.toString())
        put("speedLimitOnlyInUse", _speedLimitOnlyInUse.value.toString())
        put("connections", _connections.value.toString())
        put("torrentStopRatio", _torrentStopRatio.value.toString())
        for ((category, folder) in _categoryFolders.value) put("folder.$category", folder)
        if (_siteFolders.value.isNotEmpty()) put("siteFolders", SiteFolderRules.encode(_siteFolders.value))
    }

    internal fun restore(values: Map<String, String>, paths: com.michelle.raouf.fetch_jet.core.filesystem.ImportedPaths) {
        fun flag(key: String, set: (Boolean) -> Unit) {
            values[key]?.toBooleanStrictOrNull()?.let { set(it) }
        }
        flag("monitorClipboard", this::setMonitorClipboard)
        flag("clipboardVideoPages", this::setClipboardVideoPages)
        flag("sortIntoFolders", this::setSortIntoFolders)
        flag("groupByFolder", this::setGroupByFolder)
        flag("videoPlaysEverywhere", this::setVideoPlaysEverywhere)
        flag("extractArchives", this::setExtractArchives)
        flag("deleteToTrash", this::setDeleteToTrash)
        flag("trashArchiveAfterExtract", this::setTrashArchiveAfterExtract)
        flag("scanFiles", this::setScanFiles)
        flag("speedLimitOnlyInUse", this::setSpeedLimitOnlyInUse)
        values["extractPlace"]?.let { v ->
            com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace.entries.firstOrNull { it.name == v }?.let { setExtractPlace(it) }
        }
        values["connections"]?.toIntOrNull()?.let { setConnections(it.coerceAtLeast(0)) }
        values["torrentStopRatio"]?.toFloatOrNull()?.takeIf { !it.isNaN() }?.let { setTorrentStopRatio(it.coerceAtLeast(0f)) }
        for (category in FileCategory.allFolders) {
            val folder = values["folder.$category"]?.takeIf { it.isNotBlank() }?.let { paths.directory(it) } ?: continue
            setCategoryFolder(category, folder)
        }
        values["siteFolders"]?.let { text ->
            val rules = SiteFolderRules.decode(text).mapNotNull { rule -> paths.directory(rule.folder)?.let { rule.copy(folder = it) } }
            if (rules.isNotEmpty()) setSiteFolders(rules)
        }
    }

    private fun read(key: String): String? = try {
        if (!AppFileSystem.exists(file)) null
        else AppFileSystem.read(file) { readUtf8() }.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()
    } catch (_: Throwable) {
        null
    }

    private fun write(key: String, value: String) {
        try {
            val lines = if (AppFileSystem.exists(file))
                AppFileSystem.read(file) { readUtf8() }.lineSequence()
                    .filter { it.isNotBlank() && !it.trim().startsWith("$key=") }.toMutableList()
            else mutableListOf("# FetchJet behaviour settings")
            lines.add("$key=$value")
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(lines.joinToString("\n") + "\n") }
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

    fun categoryFor(fileName: String, isMedia: Boolean = false, isTorrent: Boolean = false): String =
        folderFor(fileName, isMedia, isTorrent) ?: "Other"
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
        if (!(t.startsWith("http://", true) || t.startsWith("https://", true) ||
                    com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(t))
        ) return null
        val path = t.substringAfter("://").substringBefore('?').substringBefore('#')
        if (!path.contains('/')) return null
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in fileExts) return t
        if (videoPages) {
            val host = path.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
            // a video page, not the site's front page
            if ((videoHosts.any { host == it || host.endsWith(".$it") } || com.michelle.raouf.fetch_jet.feature.queue_manager.LearnedMediaHosts.knows(host)) && path.substringAfter('/').length > 3) return t
        }
        return null
    }
}
