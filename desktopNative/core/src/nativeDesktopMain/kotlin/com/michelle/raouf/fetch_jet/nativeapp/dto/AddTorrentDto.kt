package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class TorrentStateCode { HIDDEN, INSPECTING, ERROR, NOT_INSPECTED, FILES }

@Serializable
data class TorrentFileRowDto(
    val index: Int = 0,
    val path: String = "",
    val size: String = "",
    val selected: Boolean = false,
    val priority: Int = 0,
    val priorityLabel: String = "",
)

@Serializable
data class AddTorrentDto(
    val state: Int = 0,
    val error: String = "",
    val inspectEnabled: Boolean = false,
    val name: String = "",
    val allSelected: Boolean = false,
)
