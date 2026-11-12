package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class SideFilterCode { ALL, DOWNLOADING, WAITING, PAUSED, COMPLETED, FAILED, MEDIA, FILES, TORRENTS }

enum class RowKind { TASK, HEADER }

enum class RowActionCode { PAUSE, RESUME, START_NOW, OPEN, NONE }

enum class BarColorCode { PRIMARY, COMPLETED, PAUSED, ERROR }

enum class TagColorCode { VIDEO, AUDIO, MAGNET, ARCHIVE, DISK, PROGRAM, OTHER }

enum class GroupColorCode { PRIMARY, MUTED, PAUSED, TERTIARY }

enum class ListKeyCode { SELECT_ALL, ESCAPE, DELETE }

@Serializable
data class NavEntryDto(
    val filter: Int = 0,
    val label: String = "",
    val count: Int = 0,
    val selected: Boolean = false,
)

@Serializable
data class MenuOptionDto(
    val label: String = "",
    val checked: Boolean = false,
)

@Serializable
data class ChromeDto(
    val settingsOpen: Boolean = false,
    val title: String = "",
    val navState: List<NavEntryDto> = emptyList(),
    val navType: List<NavEntryDto> = emptyList(),
    val navRail: List<NavEntryDto> = emptyList(),
    val search: String = "",
    val searchRev: Int = 0,
    val turtleActive: Boolean = false,
    val modeLabel: String = "",
    val capped: Boolean = false,
    val scheduled: Boolean = false,
    val speedOptions: List<MenuOptionDto> = emptyList(),
    val sortLabel: String = "",
    val sortOptions: List<MenuOptionDto> = emptyList(),
    val anyPausable: Boolean = false,
    val anyResumable: Boolean = false,
    val showToolbar: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedCountText: String = "",
    val allChecked: Boolean = false,
    val listEmpty: Boolean = false,
    val emptyTitle: String = "",
    val emptyHint: String = "",
    val toolsBannerShown: Boolean = false,
    val toolsBannerText: String = "",
    val toolsBannerFraction: Float = 0f,
    val toolsBannerIndeterminate: Boolean = false,
    val whenDoneBannerShown: Boolean = false,
    val whenDoneBannerText: String = "",
    val detailsOpen: Boolean = false,
)

@Serializable
data class StatusDto(
    val downSpeed: String = "",
    val upSpeed: String = "",
    val summary: String = "",
    val queue: String = "",
    val limit: String = "",
    val version: String = "",
)

@Serializable
data class TaskRowDto(
    val kind: Int = 0,
    val canCompress: Boolean = false,
    val id: String = "",
    val name: String = "",
    val nameRtl: Boolean = false,
    val nameHan: Boolean = false,
    val tag: String = "",
    val tagColor: Int = 0,
    val stateLine: String = "",
    val stateIsError: Boolean = false,
    val size: String = "",
    val progress: Float = 0f,
    val preparing: Boolean = false,
    val percent: String = "",
    val barColor: Int = 0,
    val speed: String = "",
    val left: String = "",
    val action: Int = 0,
    val completed: Boolean = false,
    val priority: Int = 0,
    val priorityLabel: String = "",
    val checked: Boolean = false,
    val selected: Boolean = false,
    val headerKey: String = "",
    val headerIcon: String = "",
    val headerTitle: String = "",
    val headerCount: Int = 0,
    val headerColor: Int = 0,
    val headerCollapsed: Boolean = false,
)
