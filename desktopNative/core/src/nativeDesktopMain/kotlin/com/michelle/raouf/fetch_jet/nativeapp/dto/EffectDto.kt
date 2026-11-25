package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class EffectKind {
    TOAST,
    SHOW_WINDOW,
    SCROLL_TO_ROW,
    NOTIFY,
    EXIT,
    OPEN_PLAYER,
    PLAYER_PATH,
    PLAYER_PREPARING,
    CLOSE_PLAYER,
}

@Serializable
data class EffectDto(
    val kind: Int = 0,
    val text: String = "",
    val title: String = "",
    val number: Int = 0,
)
