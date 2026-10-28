package com.michelle.raouf.fetch_jet.core.filesystem

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

actual class PathResolver actual constructor() {
    companion object {
        var appContext: Context? = null
    }

    actual fun getDownloadsDirectory(): String {
        val publicDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        try {
            if (!publicDir.exists()) publicDir.mkdirs()
        } catch (_: Exception) {
        }
        return publicDir.absolutePath
    }

    actual fun getDocumentsDirectory(): String {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        try {
            if (!docsDir.exists()) docsDir.mkdirs()
        } catch (_: Exception) {
        }
        return docsDir.absolutePath
    }

    actual fun getDesktopDirectory(): String {
        return getDownloadsDirectory()
    }

    actual fun getSettingsDirectory(): String {
        val ctx = appContext
        val dir = if (ctx != null) {
            File(ctx.filesDir, "settings")
        } else {
            File(getDownloadsDirectory(), ".fetch_jet")
        }
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
        }
        return dir.absolutePath
    }

    actual fun getTempDirectory(): String {
        val ctx = appContext
        val cache = ctx?.cacheDir ?: ctx?.externalCacheDir
        val tempDir = if (cache != null) {
            File(cache, "temp")
        } else {
            File(getDownloadsDirectory(), ".temp")
        }
        try {
            if (!tempDir.exists()) tempDir.mkdirs()
        } catch (_: Exception) {
        }
        return tempDir.absolutePath
    }

    actual fun sanitizeFileName(fileName: String): String {
        val decoded = decodeUnicodeEscapes(fileName)
        val cleaned = decoded.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val ext = cleaned.substringAfterLast(".", "")
        val nameWithoutExt = if (ext.isNotEmpty()) cleaned.substringBeforeLast(".") else cleaned
        val truncatedName =
            if (nameWithoutExt.length > 80) nameWithoutExt.take(80).trimEnd() else nameWithoutExt
        return if (ext.isNotEmpty()) "$truncatedName.$ext" else truncatedName
    }

    actual fun chooseDirectory(initialPath: String?): String? {
        return null
    }

    actual fun isOpenFolderSupported(): Boolean = true

    actual fun openDirectoryInExplorer(path: String): Boolean {
        val ctx = appContext ?: return false
        val file = File(path)
        val dir = if (file.isDirectory) file else file.parentFile ?: file
        if (!dir.exists()) {
            try {
                dir.mkdirs()
            } catch (_: Exception) {
            }
        }

        // 1. Try DocumentsContract for external storage folder
        try {
            val storageRoot = Environment.getExternalStorageDirectory().absolutePath
            val dirPath = dir.absolutePath
            val relPath = if (dirPath.startsWith(storageRoot)) {
                dirPath.removePrefix(storageRoot).removePrefix("/")
            } else {
                ""
            }
            val docId = if (relPath.isEmpty()) "primary:" else "primary:$relPath"
            val docUri =
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 2. Try FileProvider with folder MIME type
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 3. Try FileProvider with vnd.android.document/directory
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 4. Fallback to System Downloads Manager
        return try {
            val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(dlIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    actual fun openFileLocation(filePath: String): Boolean {
        val ctx = appContext ?: return false
        val file = File(filePath)
        val dir = if (file.isDirectory) file else file.parentFile ?: file
        if (!dir.exists()) {
            try {
                dir.mkdirs()
            } catch (_: Exception) {
            }
        }

        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        val filePathStr = file.absolutePath
        val relPath = if (filePathStr.startsWith(storageRoot)) {
            filePathStr.removePrefix(storageRoot).removePrefix("/")
        } else {
            ""
        }
        val dirRelPath = if (dir.absolutePath.startsWith(storageRoot)) {
            dir.absolutePath.removePrefix(storageRoot).removePrefix("/")
        } else {
            ""
        }

        fun Intent.addSelectionExtras(): Intent {
            putExtra("org.openintents.extra.ABSOLUTE_PATH", file.absolutePath)
            putExtra("selectFile", file.absolutePath)
            putExtra("highlight_file", file.name)
            putExtra("focus_file", file.name)
            putExtra("com.sec.android.app.myfiles.EXTRA_PATH", file.absolutePath)
            putExtra("path", file.absolutePath)
            putExtra("current_path", dir.absolutePath)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return this
        }

        // 1. Try DocumentsContract with initial URI pointing to the specific file
        try {
            val docFileUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                if (relPath.isEmpty()) "primary:" else "primary:$relPath"
            )
            val docDirUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                if (dirRelPath.isEmpty()) "primary:" else "primary:$dirRelPath"
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docDirUri, DocumentsContract.Document.MIME_TYPE_DIR)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, docFileUri)
                addSelectionExtras()
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 2. Try FileProvider with folder MIME type and file selection extras
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addSelectionExtras()
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 3. Try FileProvider with vnd.android.document/directory
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addSelectionExtras()
            }
            ctx.startActivity(intent)
            return true
        } catch (_: Exception) {
        }

        // 4. Fallback to System Downloads Manager
        return try {
            val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addSelectionExtras()
            }
            ctx.startActivity(dlIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveExistingFile(filePath: String): File? {
        val file = File(filePath)
        if (file.exists()) return file
        val dir = file.parentFile ?: return null
        if (!dir.isDirectory) return null
        val stem = file.nameWithoutExtension.ifBlank { file.name }
        return dir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") &&
                    !f.name.endsWith(".temp") && !f.name.endsWith(".aria2") &&
                    (f.nameWithoutExtension.equals(stem, ignoreCase = true) ||
                            f.name.startsWith("$stem.", ignoreCase = true) ||
                            f.name.startsWith("$stem (", ignoreCase = true))
        }?.maxByOrNull { it.lastModified() }
    }

    private fun mimeTypeFor(file: File): String {
        return when (val extension = file.extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            "torrent" -> "application/x-bittorrent"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
    }

    actual fun openFile(filePath: String): Boolean {
        val ctx = appContext ?: return false
        val file = resolveExistingFile(filePath) ?: return false
        if (file.isDirectory) return openDirectoryInExplorer(file.absolutePath)

        // Never fall back to Uri.fromFile(): on API 24+ it throws FileUriExposedException.
        val fileUri: Uri = try {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (_: Exception) {
            return openFileLocation(file.absolutePath)
        }

        val mime = mimeTypeFor(file)
        val pm = ctx.packageManager

        fun viewIntent(type: String) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 1. The exact MIME type, 2. a wildcard of the same family (video/*), 3. anything.
        val candidates = listOf(
            viewIntent(mime),
            viewIntent(mime.substringBefore('/') + "/*"),
            viewIntent("*/*")
        )
        for (intent in candidates) {
            if (intent.resolveActivity(pm) == null) continue
            try {
                val chooser = Intent.createChooser(intent, file.name).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(chooser)
                return true
            } catch (_: Exception) {
                try {
                    ctx.startActivity(intent)
                    return true
                } catch (_: Exception) {
                }
            }
        }
        // Nothing can open it: show it in a file manager instead.
        return openFileLocation(file.absolutePath)
    }

    actual fun getAvailableDirectories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val doc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        try {
            if (dl.exists() || dl.mkdirs()) list.add("Downloads" to dl.absolutePath)
        } catch (_: Exception) {
        }
        try {
            if (doc.exists() || doc.mkdirs()) list.add("Documents" to doc.absolutePath)
        } catch (_: Exception) {
        }
        try {
            if (movies.exists() || movies.mkdirs()) list.add("Movies" to movies.absolutePath)
        } catch (_: Exception) {
        }
        try {
            if (music.exists() || music.mkdirs()) list.add("Music" to music.absolutePath)
        } catch (_: Exception) {
        }
        try {
            if (dcim.exists() || dcim.mkdirs()) list.add("DCIM" to dcim.absolutePath)
        } catch (_: Exception) {
        }

        try {
            val fetchJet = File(dl, "FetchJet")
            list.add("FetchJet" to fetchJet.absolutePath)
        } catch (_: Exception) {
        }

        return list
    }

    actual fun listSubdirectories(path: String): List<String> {
        return listSubdirectories(path, false)
    }

    actual fun listSubdirectories(path: String, includeHidden: Boolean): List<String> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles { f -> f.isDirectory && (includeHidden || !f.name.startsWith(".")) }
                ?.map { it.name }
                ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    actual fun createSubdirectory(parentPath: String, name: String): String? {
        return try {
            val safeName = sanitizeFileName(name)
            if (safeName.isBlank()) return null
            val newDir = File(parentPath, safeName)
            if (newDir.exists() || newDir.mkdirs()) {
                newDir.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    actual fun getParentDirectory(path: String): String? {
        return try {
            val file = File(path)
            file.parentFile?.takeIf { it.exists() && it.canRead() }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
        return regex.replace(input) { m ->
            try {
                val codePoint = m.groupValues[1].toInt(16)
                codePoint.toChar().toString()
            } catch (_: Exception) {
                m.value
            }
        }
    }
}