/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.database.models

import com.google.gson.annotations.SerializedName

data class Format(
    @SerializedName(value = "format_id", alternate = ["itag"])
    var format_id: String = "",
    @SerializedName(value = "ext", alternate = ["container", "format"])
    var container: String = "",
    @SerializedName(value = "vcodec")
    var vcodec: String = "",
    @SerializedName(value = "acodec")
    var acodec: String = "",
    @SerializedName(value = "encoding")
    var encoding: String = "",
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx", "contentLength"])
    var filesize: Long = 0,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality", "quality"])
    var format_note: String = "",
    @SerializedName(value = "fps")
    var fps: String? = "",
    @SerializedName(value = "asr", alternate = ["audioSampleRate"])
    var asr: String? = "",
    @SerializedName(value = "url")
    var url: String? = "",
    @SerializedName(value = "language", alternate = ["audioTrackLocale"])
    val lang: String? = "",
    @SerializedName(value = "tbr", alternate = ["bitrate"])
    var tbr: String? = "",
    @SerializedName(value = "width")
    private val _width: String? = null,
    @SerializedName(value = "height")
    private val _height: String? = null,
    @SerializedName(value = "is_filesize_approx")
    var isFilesizeApprox: Boolean = false
) {
    val width: Int? get() = _width?.toIntOrNull()
    val height: Int? get() = _height?.toIntOrNull()
}