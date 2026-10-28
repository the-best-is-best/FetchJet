/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.packages

object FFmpeg : PackageBase() {
    override val executableName: String get() = "ffmpeg"
    override val packageFolderName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val canUninstall: Boolean get() = false
    override val bundledVersion: String get() = "v7.0.1"
    override val githubRepo: String get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String get() = "ffmpeg"
    override val apkPackage: String get() = "com.deniscerri.ytdl.ffmpeg"
}