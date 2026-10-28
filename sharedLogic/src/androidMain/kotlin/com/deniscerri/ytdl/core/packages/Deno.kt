/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.packages

object Deno : PackageBase() {
    override val executableName: String get() = "deno"
    override val packageFolderName: String get() = "deno"
    override val bundledZipName: String get() = "libdeno.zip.so"
    override val canUninstall: Boolean get() = true
    override val bundledVersion: String get() = ""
    override val githubRepo: String get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String get() = "deno"
    override val apkPackage: String get() = "com.deniscerri.ytdl.deno"
}