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
