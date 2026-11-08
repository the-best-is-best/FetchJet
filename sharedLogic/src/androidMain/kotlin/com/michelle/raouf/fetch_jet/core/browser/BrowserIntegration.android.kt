package com.michelle.raouf.fetch_jet.core.browser

import android.content.Context
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

actual object BrowserIntegration {
    actual val canAddToEdgeAutomatically: Boolean get() = false
    actual fun isEdgeRegistered(): Boolean = false
    actual fun addToEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun removeFromEdge(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Desktop only"))

    actual fun setUpOnFirstRun() {}

    private val firefoxPackages = listOf(
        "org.mozilla.firefox" to "Firefox",
        "org.mozilla.firefox_beta" to "Firefox Beta",
        "org.mozilla.fenix" to "Firefox Nightly"
    )

    private fun isInstalled(pkg: String): Boolean = try {
        PathResolver.appContext?.packageManager?.getPackageInfo(pkg, 0) != null
    } catch (_: Exception) {
        false
    }

    actual fun browsers(): List<BrowserInfo> {
        val installed = firefoxPackages.filter { isInstalled(it.first) }
        val rows = installed.ifEmpty { firefoxPackages.take(1) }
        return rows.map { (pkg, name) ->
            BrowserInfo(
                pkg, name, isInstalled(pkg),
                "https://play.google.com/store/apps/details?id=$pkg"
            )
        }
    }

    actual fun addExtension(id: String): AddExtensionResult {
        val ctx = PathResolver.appContext ?: return AddExtensionResult.Failed("no context")
        if (!isInstalled(id)) return AddExtensionResult.NotInstalled
        return try {
            ctx.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(FIREFOX_ADDON_URL.replace("/firefox/", "/android/"))
                ).setPackage(id).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            AddExtensionResult.Opened
        } catch (e: Exception) {
            AddExtensionResult.Failed(e.message ?: "could not open Firefox")
        }
    }

    actual fun openFolder(folder: String) {}

    actual fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult {
        val ctx = PathResolver.appContext ?: return AddExtensionResult.Failed("no context")
        if (!isInstalled(id)) return AddExtensionResult.NotInstalled
        val name = fileName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val resolver = ctx.contentResolver
                val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                resolver.delete(
                    collection,
                    "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf(name)
                )
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/x-xpinstall")
                    put(
                        android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                }
                val uri = resolver.insert(collection, values)
                    ?: return AddExtensionResult.Failed("could not save to Downloads")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                java.io.File(dir, name).writeBytes(bytes)
            }
            AddExtensionResult.InstallFromFile(name)
        } catch (e: Exception) {
            AddExtensionResult.Failed(e.message ?: "could not save the extension")
        }
    }

    actual fun openBrowser(id: String) {
        val ctx = PathResolver.appContext ?: return
        try {
            ctx.packageManager.getLaunchIntentForPackage(id)
                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { ctx.startActivity(it) }
        } catch (_: Exception) {
        }
    }

    private const val PREFS = "fetchjet_browser"
    private const val KEY_KEEP_READY = "keepReady"

    private fun prefs() =
        PathResolver.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    actual val canKeepReadyForBrowser: Boolean get() = true

    actual val canPopUpOverBrowser: Boolean get() = android.os.Build.VERSION.SDK_INT >= 29
    actual fun isPopUpAllowed(): Boolean {
        val ctx = PathResolver.appContext ?: return false
        return android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(
            ctx
        )
    }

    actual fun requestPopUpPermission() {
        val ctx = PathResolver.appContext ?: return
        try {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + ctx.packageName)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    actual fun isKeptReadyForBrowser(): Boolean =
        prefs()?.getBoolean(KEY_KEEP_READY, false) ?: false

    actual fun setKeepReadyForBrowser(enabled: Boolean) {
        val ctx = PathResolver.appContext ?: return
        prefs()?.edit()?.putBoolean(KEY_KEEP_READY, enabled)?.apply()
        if (enabled) BrowserReadyService.start(ctx) else BrowserReadyService.stop(ctx)
    }

    actual fun restoreKeepReady() {
        val ctx = PathResolver.appContext ?: return
        if (isKeptReadyForBrowser()) BrowserReadyService.start(ctx)
    }
}
