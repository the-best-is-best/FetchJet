package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class EffectKind {
    TOAST,
    SHOW_WINDOW,
    SCROLL_TO_ROW,
    NOTIFY,
    EXIT,
}

@Serializable
data class EffectDto(
    val kind: Int = 0,
    val text: String = "",
    val title: String = "",
    val number: Int = 0,
)
