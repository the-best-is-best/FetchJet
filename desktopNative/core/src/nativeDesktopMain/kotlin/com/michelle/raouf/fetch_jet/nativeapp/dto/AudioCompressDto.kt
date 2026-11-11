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
)
