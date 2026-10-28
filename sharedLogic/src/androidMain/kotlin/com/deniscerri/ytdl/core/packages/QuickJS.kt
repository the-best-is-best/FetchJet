/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.packages

object QuickJS : PackageBase() {
    override val executableName: String get() = "qjs"
    override val packageFolderName: String get() = "quickjs"
    override val bundledZipName: String get() = "libqjs.zip.so"
    override val bundledVersion: String get() = "2025-04-26"
    override val canUninstall: Boolean get() = false
    override val githubRepo: String get() = ""
    override val githubPackageName: String get() = ""
    override val apkPackage: String get() = ""
}