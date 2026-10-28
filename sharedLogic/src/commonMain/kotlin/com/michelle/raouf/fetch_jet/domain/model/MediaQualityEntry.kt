package com.michelle.raouf.fetch_jet.domain.model

data class MediaQualityEntry(
    val index: Int,
    val qualityLabel: String,
    val directUrl: String,
    val format: String,
    val size: Long = -1L,
    val isSelected: Boolean = true,
    val formatId: String? = null,
    val originalUrl: String? = null,
    val videoTitle: String? = null,
    val durationSeconds: Long = 0L
)