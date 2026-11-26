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

    private const val MAX_TORRENT_BYTES = 10L * 1024 * 1024

    /**
     * Copies a .torrent behind a content:// Uri into the app's cache and returns the path; null
     * when it cannot be read, is not a torrent (bencoded dict) or is over 10 MB. Used for the
     * picker and for "Open with FetchJet". file:// and FetchJet's own providers are refused: the
     * copy runs with FetchJet's access, so another app could point it at FetchJet's private files.
     */
    fun copyTorrentToCache(ctx: Context, uri: Uri): String? = try {
        if (!uri.scheme.equals("content", true)) return null
        val authority = uri.authority.orEmpty()
        if (authority == ctx.packageName || authority.startsWith(ctx.packageName + ".")) return null
        val name = displayName(ctx, uri)
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f]"), "_")
            ?.takeIf { it.trim('.').isNotEmpty() }
            ?.take(200)
            ?.let { if (it.lowercase().endsWith(".torrent")) it else "$it.torrent" }
            ?: "shared-${System.currentTimeMillis()}.torrent"
        val dir = File(ctx.cacheDir, "opened-torrents").apply { mkdirs() }
        val out = File(dir, name)
        val ok = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val first = input.read()
            first == 'd'.code && out.outputStream().use { output ->
                output.write(first)
                val buf = ByteArray(64 * 1024)
                var total = 1L
                var fits = true
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_TORRENT_BYTES) {
                        fits = false
                        break
                    }
                    output.write(buf, 0, n)
                }
                fits
            }
        } ?: false
        if (!ok) {
            out.delete()
            null
        } else out.absolutePath
    } catch (_: Throwable) {
        null
    }

    private fun displayName(ctx: Context, uri: Uri): String? {
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Throwable) {
            null
        } ?: uri.lastPathSegment
    }
}
