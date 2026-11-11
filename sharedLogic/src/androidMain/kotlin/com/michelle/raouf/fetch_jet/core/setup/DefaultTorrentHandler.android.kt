package com.michelle.raouf.fetch_jet.core.setup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android lets only the user pick a default ("Always" in the chooser, or the app's
 * "Open by default" page). FetchJet reads who answers a magnet link / .torrent file and opens
 * the settings page of the app that holds the default (to clear it), or its own page.
 */
actual object DefaultTorrentHandler {
    actual val supported: Boolean get() = PathResolver.appContext != null
    actual val opensSystemPage: Boolean = true

    private fun magnetIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("magnet:?xt=urn:btih:0000000000000000000000000000000000000000"))

    private fun torrentIntent() = Intent(Intent.ACTION_VIEW).setDataAndType(
        Uri.parse("content://com.michelle.raouf.fetch_jet.probe/file.torrent"), TORRENT_MIME
    )

    private fun handlerOf(context: Context, intent: Intent): String? {
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        return info.activityInfo?.packageName
    }

    actual suspend fun isDefault(): Boolean? = withContext(Dispatchers.IO) {
        val context = PathResolver.appContext ?: return@withContext null
        runCatching {
            val own = context.packageName
            handlerOf(context, magnetIntent()) == own && handlerOf(context, torrentIntent()) == own
        }.getOrNull()
    }

    actual suspend fun makeDefault(): Boolean = withContext(Dispatchers.Main) {
        val context = PathResolver.appContext ?: return@withContext false
        val own = context.packageName
        val other = listOf(magnetIntent(), torrentIntent())
            .mapNotNull { runCatching { handlerOf(context, it) }.getOrNull() }
            .firstOrNull { it != own && it != "android" && isInstalled(context, it) }
        val page = when {
            other != null -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$other"))
            Build.VERSION.SDK_INT >= 31 -> Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$own"))
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$own"))
        }
        runCatching {
            context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$own"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0); true
    }.getOrDefault(false)
}
