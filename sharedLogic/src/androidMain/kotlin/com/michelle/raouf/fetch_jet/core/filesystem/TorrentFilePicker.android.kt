package com.michelle.raouf.fetch_jet.core.filesystem

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

actual object TorrentFilePicker {
    /**
     * Set by the Activity (an ActivityResultLauncher must be registered there): shows the
     * system document picker and calls back with the picked content Uri, or null.
     */
    @Volatile
    var launcher: ((onResult: (Uri?) -> Unit) -> Unit)? = null

    actual val isSupported: Boolean
        get() = launcher != null

    actual fun pick(onPicked: (String?) -> Unit) {
        val launch = launcher
        if (launch == null) {
            onPicked(null)
            return
        }
        launch { uri ->
            val ctx = PathResolver.appContext
            onPicked(if (uri != null && ctx != null) copyTorrentToCache(ctx, uri) else null)
        }
    }

    /**
     * Copies a .torrent behind a content:// (or file://) Uri into the app's cache and returns
     * the path; null when it cannot be read. Used for the picker and for "Open with FetchJet".
     */
    fun copyTorrentToCache(ctx: Context, uri: Uri): String? = try {
        val name = displayName(ctx, uri)
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            ?.let { if (it.lowercase().endsWith(".torrent")) it else "$it.torrent" }
            ?: "shared-${System.currentTimeMillis()}.torrent"
        val dir = File(ctx.cacheDir, "opened-torrents").apply { mkdirs() }
        val out = File(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (out.length() == 0L) null else out.absolutePath
    } catch (_: Throwable) {
        null
    }

    private fun displayName(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Throwable) {
            null
        } ?: uri.lastPathSegment
    }
}
