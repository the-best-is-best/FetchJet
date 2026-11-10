package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class DeviceSendCode { NONE, WAITING, SENDING, FAILED, DONE }

enum class PairingCode { NONE, CONNECTING, CONFIRM, FAILED, OFFER, RECEIVING, RECEIVE_FAILED }

@Serializable
data class DeviceRowDto(
    val id: String = "",
    val name: String = "",
    val platform: String = "",
    val emoji: String = "",
    val paired: Boolean = false,
    val selected: Boolean = false,
    val header: String = "",
    val divider: Boolean = false,
)

@Serializable
data class DeviceSheetDto(
    val open: Boolean = false,
    val fileName: String = "",
    val fileSize: String = "",
    val deviceName: String = "",
    val deviceEmoji: String = "",
    val searching: Boolean = false,
    val searchShown: Boolean = false,
    val search: String = "",
    val searchRev: Int = 0,
    val noMatch: Boolean = false,
    val canTransfer: Boolean = false,
    val transfer: Boolean = false,
    val deleteAfter: Boolean = false,
    val manualShown: Boolean = false,
    val manualIp: String = "",
    val manualRev: Int = 0,
    val connectEnabled: Boolean = false,
    val state: Int = 0,
    val stateText: String = "",
    val progress: Float = 0f,
    val sendEnabled: Boolean = false,
    val cancelEnabled: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
data class DevicePairingDto(
    val state: Int = 0,
    val text: String = "",
    val code: String = "",
    val peer: String = "",
    val fileName: String = "",
    val fileSize: String = "",
    val progress: Float = 0f,
    val indeterminate: Boolean = false,
    val percent: String = "",
)
