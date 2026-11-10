package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class LinkLineCode { NONE, INSPECTING, QUALITIES, FAILED, ERROR, FILES }

enum class LinkTagCode { FILE, VIDEO, AUDIO, TORRENT }

@Serializable
data class LinkRowDto(
    val index: Int = 0,
    val title: String = "",
    val host: String = "",
    val tag: String = "",
    val tagCode: Int = 0,
    val selected: Boolean = false,
    val line: Int = 0,
    val lineText: String = "",
    val qualities: List<String> = emptyList(),
    val qualityIndexes: List<Int> = emptyList(),
    val qualityPick: Int = 0,
    val files: List<TorrentFileRowDto> = emptyList(),
    val filesName: String = "",
    val filesAllSelected: Boolean = false,
)
