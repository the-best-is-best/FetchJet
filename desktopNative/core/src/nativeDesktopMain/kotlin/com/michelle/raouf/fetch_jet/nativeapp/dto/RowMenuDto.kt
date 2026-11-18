package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class RowMenuItemDto(
    val key: Int = 0,
    val label: String = "",
    val danger: Boolean = false,
    val divider: Boolean = false,
)
