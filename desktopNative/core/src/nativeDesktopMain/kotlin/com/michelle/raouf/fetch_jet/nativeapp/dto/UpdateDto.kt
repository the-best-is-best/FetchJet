package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateDialogDto(
    val open: Boolean = false,
    val title: String = "",
    val versionLine: String = "",
    val notesLabel: String = "",
    val notes: String = "",
    val status: String = "",
    val progressShown: Boolean = false,
    val progress: Float = 0f,
    val indeterminate: Boolean = false,
    val error: String = "",
    val confirmLabel: String = "",
    val confirmEnabled: Boolean = false,
    val skipLabel: String = "",
    val laterLabel: String = "",
    val locked: Boolean = false,
)
