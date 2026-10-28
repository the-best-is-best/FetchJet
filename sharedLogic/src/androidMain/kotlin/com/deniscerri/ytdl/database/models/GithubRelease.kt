/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.database.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class GithubRelease(
    @SerializedName(value = "html_url")
    val html_url: String,
    @SerializedName(value = "tag_name")
    var tag_name: String,
    @SerializedName(value = "body")
    var body: String,
    @SerializedName(value = "published_at")
    var published_at: Date,
    @SerializedName(value = "assets")
    var assets: List<GithubReleaseAsset>
)

data class GithubReleaseAsset(
    @SerializedName(value = "name")
    var name: String,
    @SerializedName(value = "browser_download_url")
    var browser_download_url: String,
    @SerializedName(value = "size")
    var size: Long
)