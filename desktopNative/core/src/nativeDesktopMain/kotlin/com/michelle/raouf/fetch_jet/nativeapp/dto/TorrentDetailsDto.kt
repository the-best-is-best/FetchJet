package com.michelle.raouf.fetch_jet.nativeapp.dto

import kotlinx.serialization.Serializable

enum class TrackerStateCode { WORKING, UPDATING, WAITING, ERROR, DISABLED }

@Serializable
data class TorrentTrackerDto(
    val name: String = "",
    val state: Int = 0,
    val stateLabel: String = "",
    val tone: Int = 0,
    val message: String = "",
)

@Serializable
data class TorrentPeerDto(
    val address: String = "",
    val client: String = "",
    val down: String = "",
    val up: String = "",
    val percent: String = "",
    val seed: Boolean = false,
)

@Serializable
data class TorrentDetailsDto(
    val shown: Boolean = false,
    val id: String = "",
    val supported: Boolean = false,
    val loading: Boolean = false,
    val ratio: String = "",
    val uploaded: String = "",
    val pieces: String = "",
    val pieceSize: String = "",
    val piecesFraction: Float = 0f,
    val trackersTitle: String = "",
    val trackers: List<TorrentTrackerDto> = emptyList(),
    val trackerRev: Int = 0,
    val trackerError: String = "",
    val peersTitle: String = "",
    val peers: List<TorrentPeerDto> = emptyList(),
    val firstLast: Boolean = false,
    val magnet: String = "",
)
