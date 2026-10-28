/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.database.models

data class ResultItem(
    var id: Long = 0,
    var url: String = "",
    var title: String = "",
    var author: String = "",
    val duration: String = "",
    val thumb: String = "",
    val website: String = "",
    var playlistTitle: String = "",
    var formats: List<Format> = emptyList(),
    var urls: String = "",
    var chapters: List<ChapterItem>? = null,
    var playlistURL: String? = "",
    var playlistIndex: Int? = null,
    var creationTime: Long = System.currentTimeMillis() / 1000,
    var availableSubtitles: List<String> = listOf()
)