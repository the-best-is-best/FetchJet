package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShellDto(
    val windowTitle: String = "",
    val dark: Boolean = false,
    val trayTooltip: String = "",
    val trayShowLabel: String = "",
    val trayCloseLabel: String = "",
    val whenDoneSupported: Boolean = false,
    val whenDoneLabel: String = "",
    val whenDoneOptions: List<String> = emptyList(),
    val whenDoneIndex: Int = 0,
    val toastText: String = "",
    val toastShown: Boolean = false,
)
