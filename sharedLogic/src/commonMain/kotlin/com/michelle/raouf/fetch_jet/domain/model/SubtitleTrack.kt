package com.michelle.raouf.fetch_jet.domain.model

data class SubtitleTrack(
    val langCode: String,
    val langName: String,
    val ext: String,
    val url: String,
    val isAuto: Boolean = false,
    val isSelected: Boolean = false
)
