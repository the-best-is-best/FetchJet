package com.michelle.raouf.fetch_jet.core.filesystem

expect class PathResolver() {
    fun getDownloadsDirectory(): String
    fun getDocumentsDirectory(): String
    fun getDesktopDirectory(): String
    fun getTempDirectory(): String
    fun getSettingsDirectory(): String
    fun sanitizeFileName(fileName: String): String
    fun isOpenFolderSupported(): Boolean
    fun openDirectoryInExplorer(path: String): Boolean
    fun openFileLocation(filePath: String): Boolean
    fun openFile(filePath: String): Boolean
    fun chooseDirectory(initialPath: String? = null): String?
    fun getAvailableDirectories(): List<Pair<String, String>>
    fun listSubdirectories(path: String): List<String>
    fun listSubdirectories(path: String, includeHidden: Boolean): List<String>
    fun createSubdirectory(parentPath: String, name: String): String?
    fun getParentDirectory(path: String): String?
}