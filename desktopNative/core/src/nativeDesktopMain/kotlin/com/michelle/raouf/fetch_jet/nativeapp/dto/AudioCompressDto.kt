package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class AudioCopyDto(
    val path: String = "",
    val name: String = "",
    val size: String = "",
)

@Serializable
data class AudioCompressDto(
    val open: Boolean = false,
    val fileName: String = "",
    val originalSize: String = "",
    val newSize: String = "",
    val simple: Boolean = false,
    val levels: List<ChoiceDto> = emptyList(),
    val bitratesTop: List<ChoiceDto> = emptyList(),
    val bitratesBottom: List<ChoiceDto> = emptyList(),
    val ratesTop: List<ChoiceDto> = emptyList(),
    val ratesBottom: List<ChoiceDto> = emptyList(),
    val channels: List<ChoiceDto> = emptyList(),
    val running: Boolean = false,
    val progress: Float = 0f,
    val failed: Boolean = false,
    val canStart: Boolean = false,
    val copies: List<AudioCopyDto> = emptyList(),
    val saving: String = "",
    val ratio: Float = 0f,
    val level: Int = 0,
    val levelCount: Int = 0,
    val levelOffset: Int = 0,
    val nothing: Boolean = false,
    val formatOptions: List<String> = emptyList(),
    val formatPick: Int = 0,
    val levelLabel: String = "",
    val bitrates: List<ChoiceDto> = emptyList(),
    val bitrateOptions: List<String> = emptyList(),
    val bitratePick: Int = 0,
    val rates: List<ChoiceDto> = emptyList(),
    val rateOptions: List<String> = emptyList(),
    val ratePick: Int = 0,
    val channelOptions: List<String> = emptyList(),
    val channelPick: Int = 0,
    val progressText: String = "",
    val cut: VideoCutDto = VideoCutDto(),
    val merge: VideoMergeDto = VideoMergeDto(),
    val canDeleteOriginal: Boolean = false,
    val deleteOriginal: Boolean = false,
)

@Serializable
data class VideoClipDto(
    val path: String = "",
    val name: String = "",
    val nameRtl: Boolean = false,
    val size: String = "",
    val range: String = "",
)

@Serializable
data class VideoCutDto(
    val open: Boolean = false,
    val needsTools: Boolean = false,
    val audio: Boolean = false,
    val chooser: Boolean = false,
    val timeline: Boolean = false,
    val clipsOnly: Boolean = false,
    val reading: Boolean = false,
    val timelineOff: Boolean = false,
    val fileName: String = "",
    val fileNameRtl: Boolean = false,
    val duration: String = "",
    val start: String = "",
    val startFriendly: String = "",
    val end: String = "",
    val endFriendly: String = "",
    val length: String = "",
    val rangeError: Boolean = false,
    val canStart: Boolean = false,
    val running: Boolean = false,
    val stopping: Boolean = false,
    val progress: Float = 0f,
    val percent: String = "",
    val elapsed: String = "",
    val timeLeft: String = "",
    val failed: Boolean = false,
    val message: String = "",
    val clips: List<VideoClipDto> = emptyList(),
    val canDeleteOriginal: Boolean = false,
    val deleteOriginal: Boolean = false,
)

@Serializable
data class VideoMergeItemDto(
    val id: String = "",
    val name: String = "",
    val nameRtl: Boolean = false,
    val size: String = "",
    val duration: String = "",
    val notSame: Boolean = false,
    val canUp: Boolean = false,
    val canDown: Boolean = false,
)

@Serializable
data class VideoMergeDto(
    val open: Boolean = false,
    val items: List<VideoMergeItemDto> = emptyList(),
    val fileName: String = "",
    val extension: String = "",
    val total: String = "",
    val checking: Boolean = false,
    val canStart: Boolean = false,
    val running: Boolean = false,
    val stopping: Boolean = false,
    val progress: Float = 0f,
    val percent: String = "",
    val elapsed: String = "",
    val timeLeft: String = "",
    val failed: Boolean = false,
    val saved: Boolean = false,
    val message: String = "",
)
