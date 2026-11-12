package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class EngineCode { HTTP_DIRECT, BITTORRENT, MEDIA_STREAM }

enum class AddModeCode { SINGLE, LINK_LIST, PLAYLIST }

@Serializable
data class EngineChoiceDto(
    val engine: Int = 0,
    val icon: String = "",
    val label: String = "",
    val selected: Boolean = false,
)

@Serializable
data class ChoiceDto(
    val value: Int = 0,
    val label: String = "",
    val selected: Boolean = false,
)

@Serializable
data class QuickFolderDto(
    val icon: String = "",
    val label: String = "",
    val path: String = "",
)

@Serializable
data class AddDto(
    val visible: Boolean = false,
    val url: String = "",
    val urlRev: Int = 0,
    val canImportTxt: Boolean = false,
    val canOpenTorrent: Boolean = false,
    val mode: Int = 0,
    val engine: Int = 0,
    val engines: List<EngineChoiceDto> = emptyList(),
    val scheduleNow: Boolean = false,
    val scheduleAtTime: Boolean = false,
    val scheduleHour: String = "",
    val scheduleMinute: String = "",
    val scheduleRev: Int = 0,
    val schedulePm: Boolean = false,
    val scheduleDays: List<ChoiceDto> = emptyList(),
    val scheduleError: String = "",
    val scheduleStartsAt: String = "",
    val connectionsShown: Boolean = false,
    val connections: List<ChoiceDto> = emptyList(),
    val fileNameShown: Boolean = false,
    val fileName: String = "",
    val fileNameRev: Int = 0,
    val fileNameBusy: Boolean = false,
    val fileNameCanReset: Boolean = false,
    val checksumShown: Boolean = false,
    val checksum: String = "",
    val checksumRev: Int = 0,
    val testLinkShown: Boolean = false,
    val testLinkBusy: Boolean = false,
    val testLinkOk: Boolean = false,
    val testLinkText: String = "",
    val destination: String = "",
    val destinationRev: Int = 0,
    val quickFolders: List<QuickFolderDto> = emptyList(),
    val linkSummary: String = "",
    val linkAllSelected: Boolean = false,
    val linkFolder: String = "",
    val linkFolderRev: Int = 0,
    val leechShown: Boolean = false,
    val leech: Boolean = false,
    val error: String = "",
    val showRetry: Boolean = false,
    val primaryLabel: String = "",
    val primaryEnabled: Boolean = false,
    val loading: Boolean = false,
)
