package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubtitlePageRowDto(
    val key: String = "",
    val name: String = "",
    val checked: Boolean = false,
    val present: Boolean = false,
    val enabled: Boolean = false,
)

@Serializable
data class SubtitlesPageDto(
    val open: Boolean = false,
    val fileName: String = "",
    val reading: Boolean = false,
    val message: String = "",
    val messageError: Boolean = false,
    val note: String = "",
    val rows: List<SubtitlePageRowDto> = emptyList(),
    val running: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val added: String = "",
    val refused: String = "",
    val failed: String = "",
    val canAdd: Boolean = false,
)
