/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.util

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import java.io.File

object FileUtil {
    fun getBundledYTDLPPluginsPath(context: Context): String {
        return File(context.filesDir, "yt_dlp_plugins").absolutePath
    }

    fun getDefaultApplicationPath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath +
                File.separator + "FetchJet"
    }

    fun getDefaultTerminalPath(): String {
        return "${getDefaultApplicationPath()}/Terminal"
    }

    fun getDefaultApksPath(): String {
        return "${getDefaultApplicationPath()}/Apks"
    }

    fun getCachePath(context: Context): String {
        val preference =
            PreferenceManager.getDefaultSharedPreferences(context).getString("cache_path", "")
        if (!preference.isNullOrBlank()) {
            return preference
        }
        return "${context.cacheDir.absolutePath}/ytdlnis_cache/"
    }

    fun getCacheYTDLPPath(context: Context): String {
        return "${getCachePath(context)}yt-dlp"
    }

    fun getInfoJsonPath(context: Context): String {
        return "${getCachePath(context)}infojsons"
    }

    /**
     * Looks for cookies.txt in multiple standard accessible paths so users can easily
     * drop a cookies.txt file into their Downloads or Downloads/FetchJet folder to bypass
     * age restrictions and bot verifications.
     */
    fun getCookieFile(
        context: Context,
        ignoreIfExists: Boolean = false,
        path: (path: String) -> Unit
    ) {
        val candidates = listOf(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FetchJet/cookies.txt"
            ),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "cookies.txt"
            ),
            File(context.getExternalFilesDir(null), "cookies.txt"),
            File(context.filesDir, "cookies.txt"),
            File(context.cacheDir, "cookies.txt")
        )

        val found = candidates.firstOrNull { it.exists() && it.length() > 0 }
        if (found != null) {
            path(found.absolutePath)
        } else if (ignoreIfExists) {
            path(File(context.cacheDir, "cookies.txt").absolutePath)
        }
    }

    fun deleteConfigFiles(request: YTDLRequest) {
        runCatching {
            request.getArguments("--config")?.forEach {
                if (it != null && it.contains("##url.txt")) {
                    File(it).delete()
                }
            }
        }
    }
}