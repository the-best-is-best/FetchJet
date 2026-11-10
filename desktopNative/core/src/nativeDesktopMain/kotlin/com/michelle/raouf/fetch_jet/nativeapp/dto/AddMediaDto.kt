package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class MediaStateCode { HIDDEN, COURSE_READING, INSPECTING, ERROR, ENGINE_MISSING, PLAYLIST, NOT_INSPECTED, QUALITIES }

enum class ChoiceDialogKind { NONE, DUPLICATE, FILE_CONFLICT }

enum class BadgeToneCode { OTHER, COMPLETED, RUNNING, PAUSED, ERROR }

enum class ClipPickerField { HOURS, MINUTES, SECONDS }

@Serializable
data class QualityRowDto(
    val index: Int = 0,
    val label: String = "",
    val line: String = "",
    val selected: Boolean = false,
)

@Serializable
data class SubtitleRowDto(
    val code: String = "",
    val name: String = "",
    val automatic: Boolean = false,
    val selected: Boolean = false,
)

@Serializable
data class PlaylistRowDto(
    val index: Int = 0,
    val number: String = "",
    val title: String = "",
    val titleRtl: Boolean = false,
    val duration: String = "",
    val selected: Boolean = false,
)

@Serializable
data class ClipChipDto(
    val label: String = "",
    val start: String = "",
    val end: String = "",
)

@Serializable
data class TextChoiceDto(
    val value: String = "",
    val label: String = "",
    val selected: Boolean = false,
)

@Serializable
data class AddMediaDto(
    val state: Int = 0,
    val bannerTitle: String = "",
    val bannerHint: String = "",
    val error: String = "",
    val enginePreparing: Boolean = false,
    val engineTitle: String = "",
    val engineStatus: String = "",
    val inspectIcon: String = "",
    val inspectLabel: String = "",
    val inspectEnabled: Boolean = false,
    val qualitiesIcon: String = "",
    val qualitiesTitle: String = "",
    val qualitiesTitleRtl: Boolean = false,
    val qualitiesCount: String = "",
    val subtitlesShown: Boolean = false,
    val subtitlesAnySelected: Boolean = false,
    val embedSubtitles: Boolean = false,
    val clipAvailable: Boolean = false,
    val clipEnabled: Boolean = false,
    val clipTitle: String = "",
    val clipFullRange: String = "",
    val clipStart: String = "",
    val clipStartFriendly: String = "",
    val clipStartError: Boolean = false,
    val clipEnd: String = "",
    val clipEndFriendly: String = "",
    val clipEndError: Boolean = false,
    val clipError: String = "",
    val clipChips: List<ClipChipDto> = emptyList(),
    val clipFastMode: Boolean = false,
    val fullThenCutTitle: String = "",
    val fullThenCutLine1: String = "",
    val audioShown: Boolean = false,
    val audioTitle: String = "",
    val audioFormats: List<TextChoiceDto> = emptyList(),
    val playlistTitle: String = "",
    val playlistTitleRtl: Boolean = false,
    val playlistSummary: String = "",
    val playlistAllSelected: Boolean = false,
    val playlistFolder: String = "",
    val playlistFolderRev: Int = 0,
    val playlistFromBrowser: Boolean = false,
    val playlistQualities: List<ChoiceDto> = emptyList(),
    val playlistCanUseSingle: Boolean = false,
)

@Serializable
data class ClipPickerDto(
    val open: Boolean = false,
    val title: String = "",
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val hoursRev: Int = 0,
    val minutesRev: Int = 0,
    val secondsRev: Int = 0,
    val upEnabled: Boolean = false,
    val hoursDownEnabled: Boolean = false,
    val minutesDownEnabled: Boolean = false,
    val secondsDownEnabled: Boolean = false,
    val summary: String = "",
    val error: String = "",
    val endChip: String = "",
    val minChip: String = "",
    val valid: Boolean = false,
    val applyLabel: String = "",
)

@Serializable
data class LabeledLineDto(
    val label: String = "",
    val text: String = "",
    val accent: Boolean = false,
    val strong: Boolean = false,
)

@Serializable
data class ChoiceDialogDto(
    val kind: Int = 0,
    val icon: String = "",
    val danger: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val name: String = "",
    val nameRtl: Boolean = false,
    val badge: String = "",
    val badgeTone: Int = 0,
    val percent: String = "",
    val barShown: Boolean = false,
    val progress: Float = 0f,
    val lines: List<LabeledLineDto> = emptyList(),
    val question: String = "",
    val primaryIcon: String = "",
    val primaryLabel: String = "",
    val secondaryIcon: String = "",
    val secondaryLabel: String = "",
    val secondaryDanger: Boolean = false,
    val cancelLabel: String = "",
)
