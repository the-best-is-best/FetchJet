package com.michelle.raouf.fetch_jet.feature.device_sync

internal interface DeviceSyncStore {
    val selfId: String
    var selfName: String

    fun pairedDevices(): List<PairedDevice>

    fun isPaired(deviceId: String): Boolean

    fun keyFor(deviceId: String): ByteArray?

    fun addPairing(device: PairedDevice, key: ByteArray)

    fun removePairing(deviceId: String)

    fun blockedDevices(): List<PairedDevice>

    fun isBlocked(deviceId: String): Boolean

    fun block(device: PairedDevice)

    fun unblock(deviceId: String)
}

internal class DeviceSyncTiming(
    val announceEveryMs: Long = 2000L,
    val deviceExpireMs: Long = 8000L,
    val acceptWaitMs: Long = 90_000L,
    val pairRequestGapMs: Long = 30_000L,
    val firstFrameMs: Long = 20_000L,
)
