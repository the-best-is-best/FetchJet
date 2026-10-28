/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.database.models

import com.deniscerri.ytdl.core.packages.PackageBase

data class PackageItem(
    val title: String,
    val plugin: PackageBase
) {
    fun getInstance(): PackageBase = plugin.getInstance()
}