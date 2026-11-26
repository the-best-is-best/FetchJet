/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.util

import android.content.Context
import java.io.File

object FileUtil {
    fun getBundledYTDLPPluginsPath(context: Context): String {
        return File(context.filesDir, "yt_dlp_plugins").absolutePath
    }

    /**
     * The app's private cookies.txt (filesDir) only. A file in shared storage (Downloads) is
     * never used: any app could plant one, and yt-dlp writes the cookie jar back to it.
     */
    fun getCookieFile(context: Context, path: (path: String) -> Unit) {
        val file = File(context.filesDir, "cookies.txt")
        if (file.isFile && file.length() > 0) path(file.absolutePath)
    }

}