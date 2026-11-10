package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class AlertKind {
    NONE,
    REMOVE,
    MISSING_FILE,
    WHEN_DONE_COUNTDOWN,
    WHEN_DONE_ERROR,
    CLIPBOARD_OFFER,
    BRIDGE_PAIRING,
    BROWSER_FALLBACK,
    RESTART_PAUSE,
    EXTENSION_MISSING,
    EXTENSION_STEPS,
    EXTENSION_FROM_FILE,
    EXTENSION_FAILED,
}

@Serializable
data class AlertDto(
    val kind: Int = 0,
    val title: String = "",
    val titleSmall: Boolean = false,
    val text: String = "",
    val strong: String = "",
    val detail: String = "",
    val hint: String = "",
    val checkLabel: String = "",
    val progressShown: Boolean = false,
    val progress: Float = 0f,
    val confirmLabel: String = "",
    val confirmDanger: Boolean = false,
    val dismissLabel: String = "",
    val locked: Boolean = false,
    val mono: String = "",
    val extraLabel: String = "",
    val flatDismiss: Boolean = false,
    val round: Boolean = false,
)
