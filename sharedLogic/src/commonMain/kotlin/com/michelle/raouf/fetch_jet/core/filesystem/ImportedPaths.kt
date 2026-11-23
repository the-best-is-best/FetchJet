package com.michelle.raouf.fetch_jet.core.filesystem

internal class ImportedPaths(
    pathResolver: PathResolver,
    private val trustPaths: Boolean = true,
    extraRoots: Collection<String> = emptyList()
) {
    val downloads: String = pathResolver.getDownloadsDirectory()

    private val roots: List<String> = buildList {
        add(downloads)
        add(pathResolver.getDocumentsDirectory())
        add(pathResolver.getDesktopDirectory())
        pathResolver.getAvailableDirectories().forEach { add(it.second) }
        addAll(extraRoots)
    }.map { clean(it) }.filter { it.isNotEmpty() && !driveRoot.matches(it) }.distinct()

    private val storageRoot: String? = androidStorageRoot.find(clean(downloads))?.value

    fun directory(path: String): String? {
        if (!trustPaths) return null
        val relocated = remapStorageUser(relocateAppPath(path.trim()), storageRoot)
        val clean = clean(relocated)
        if (clean.isEmpty() || clean.any { it < ' ' }) return null
        if (!clean.startsWith("/") && !driveStart.containsMatchIn(clean)) return null
        if (clean.split('/').any { it == ".." }) return null
        val windows = driveStart.containsMatchIn(clean)
        val ownStorage = storageRoot != null && clean.startsWith("$storageRoot/") &&
            !clean.startsWith("$storageRoot/Android/") && clean != "$storageRoot/Android"
        val inside = ownStorage || roots.any { root ->
            clean.equals(root, ignoreCase = windows) || clean.startsWith("$root/", ignoreCase = windows)
        }
        return if (inside) relocated else null
    }

    fun directoryOrDownloads(path: String): String = directory(path) ?: downloads

    private fun clean(path: String): String = path.replace('\\', '/').trimEnd('/')

    private companion object {
        val driveStart = Regex("^[A-Za-z]:/")
        val driveRoot = Regex("^[A-Za-z]:$")
    }
}

private val androidStorageRoot = Regex("^/storage/emulated/[0-9]+")

internal fun remapStorageUser(path: String, currentRoot: String?): String =
    if (currentRoot == null) path else androidStorageRoot.replaceFirst(path, currentRoot)
