package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class ToneCode { ON_SURFACE, MUTED, PRIMARY, COMPLETED, PAUSED, ERROR, TORRENT, MEDIA, TERTIARY, ACTIVE, CHECKSUM_OK }

enum class DetailActionCode { NONE, OPEN_FILE, OPEN_FOLDER, PAUSE, START_NOW, RESUME }

enum class PieceStateCode { IDLE, COMPLETED, ACTIVE, WAITING }

@Serializable
data class ChipDto(
    val text: String = "",
    val tone: Int = 0,
    val dot: Boolean = false,
)

@Serializable
data class LinkDto(
    val label: String = "",
    val url: String = "",
)

@Serializable
data class DetailsDto(
    val open: Boolean = false,
    val id: String = "",
    val name: String = "",
    val nameRtl: Boolean = false,
    val nameHan: Boolean = false,
    val chips: List<ChipDto> = emptyList(),
    val hasFiles: Boolean = false,
    val filesTitle: String = "",
    val percent: String = "",
    val statusWord: String = "",
    val stepLine: String = "",
    val progress: Float = 0f,
    val postShown: Boolean = false,
    val postIcon: String = "",
    val postLabel: String = "",
    val postRight: String = "",
    val postProgress: Float = 0f,
    val postIndeterminate: Boolean = false,
    val downloaded: String = "",
    val speedLabel: String = "",
    val speedIcon: String = "",
    val speedValue: String = "",
    val speedTone: Int = 0,
    val timeLeft: String = "",
    val added: String = "",
    val piecesShown: Boolean = false,
    val piecePills: List<ChipDto> = emptyList(),
    val piecesSummary: String = "",
    val pieceGap: Int = 0,
    val error: String = "",
    val fullPath: String = "",
    val fullPathRtl: Boolean = false,
    val links: List<LinkDto> = emptyList(),
    val checksumShown: Boolean = false,
    val checksumIcon: String = "",
    val checksumText: String = "",
    val checksumTone: Int = 0,
    val primaryAction: Int = 0,
    val primaryLabel: String = "",
    val secondaryAction: Int = 0,
    val secondaryLabel: String = "",
    val canShare: Boolean = false,
    val canCompress: Boolean = false,
    val compressLabel: String = "",
    val removeLabel: String = "",
)

@Serializable
data class PieceDto(
    val start: Float = 0f,
    val end: Float = 0f,
    val fill: Float = 0f,
    val state: Int = 0,
)

@Serializable
data class ConnectionDto(
    val label: String = "",
    val text: String = "",
    val percent: String = "",
    val fraction: Float = 0f,
    val active: Boolean = false,
)

@Serializable
data class FileRowDto(
    val index: Int = 0,
    val path: String = "",
    val pathRtl: Boolean = false,
    val sizeText: String = "",
    val selected: Boolean = false,
    val completed: Boolean = false,
    val priority: Int = 0,
    val priorityLabel: String = "",
)
