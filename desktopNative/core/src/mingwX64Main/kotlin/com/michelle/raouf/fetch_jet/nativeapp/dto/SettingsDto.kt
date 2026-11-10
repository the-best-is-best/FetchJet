package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class SettingKind { NONE, SWITCH, INFO, MENU, PICKER, CHIPS, NOTE, FIELDS, PRESET, BUTTONS, PROGRESS, LIMIT_BOX }

@Serializable
data class SettingsSectionDto(
    val glyph: String = "",
    val title: String = "",
    val hint: String = "",
    val hintTone: Int = 0,
    val selected: Boolean = false,
)

@Serializable
data class SettingsNavDto(
    val sections: List<SettingsSectionDto> = emptyList(),
    val pageOpen: Boolean = false,
    val current: Int = 0,
    val title: String = "",
    val blurb: String = "",
)

@Serializable
data class SettingFieldDto(
    val id: String = "",
    val label: String = "",
    val placeholder: String = "",
    val value: String = "",
    val rev: Int = 0,
    val enabled: Boolean = false,
    val secret: Boolean = false,
    val width: Int = 0,
    val emoji: String = "",
    val toggle: String = "",
    val toggleId: String = "",
    val slim: Boolean = false,
)

@Serializable
data class SettingButtonDto(
    val id: String = "",
    val label: String = "",
    val filled: Boolean = false,
    val flat: Boolean = false,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val strong: Boolean = false,
    val round: Boolean = false,
    val tone: Int = 0,
)

@Serializable
data class SettingRowDto(
    val kind: Int = 0,
    val id: String = "",
    val caption: String = "",
    val footer: String = "",
    val first: Boolean = false,
    val last: Boolean = false,
    val bare: Boolean = false,
    val divider: Boolean = false,
    val top: Int = 0,
    val bottom: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val subtitleEmoji: String = "",
    val enabled: Boolean = false,
    val checked: Boolean = false,
    val clickable: Boolean = false,
    val compact: Boolean = false,
    val status: String = "",
    val statusTone: Int = 0,
    val side: String = "",
    val sideTone: Int = 0,
    val options: List<String> = emptyList(),
    val pick: Int = 0,
    val hanOption: Int = 0,
    val below: Boolean = false,
    val one: SettingFieldDto = SettingFieldDto(),
    val two: SettingFieldDto = SettingFieldDto(),
    val removeId: String = "",
    val buttons: List<SettingButtonDto> = emptyList(),
    val tone: Int = 0,
    val large: Boolean = false,
    val progress: Float = 0f,
    val indeterminate: Boolean = false,
    val alignEnd: Boolean = false,
    val hint: String = "",
)
