/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.packages

object Aria2c : PackageBase() {
    override val executableName: String get() = "aria2c"
    override val packageFolderName: String get() = "aria2c"
    override val bundledZipName: String get() = "libaria2c.zip.so"
    override val canUninstall: Boolean = false
    override val bundledVersion: String get() = "v1.37.0"
    override val githubRepo: String get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String get() = "aria2c"
    override val apkPackage: String get() = "com.deniscerri.ytdl.aria2c"
}