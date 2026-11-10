package com.michelle.raouf.fetch_jet.feature.device_sync

import java.security.SecureRandom

private class MemoryDeviceStore : DeviceSyncStore {
    private val peers = LinkedHashMap<String, Pair<PairedDevice, ByteArray>>()
    private val blockedList = ArrayList<PairedDevice>()

    override val selfId: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    @Volatile
    override var selfName: String = ""

    @Synchronized
    override fun pairedDevices(): List<PairedDevice> = peers.values.map { it.first }

    @Synchronized
    override fun isPaired(deviceId: String): Boolean = peers.containsKey(deviceId)

    @Synchronized
    override fun keyFor(deviceId: String): ByteArray? = peers[deviceId]?.second?.copyOf()

    @Synchronized
    override fun addPairing(device: PairedDevice, key: ByteArray) {
        peers[device.id] = device to key.copyOf()
    }

    @Synchronized
    override fun removePairing(deviceId: String) {
        peers.remove(deviceId)
    }

    @Synchronized
    override fun blockedDevices(): List<PairedDevice> = blockedList.toList()

    @Synchronized
    override fun isBlocked(deviceId: String): Boolean = blockedList.any { it.id == deviceId }

    @Synchronized
    override fun block(device: PairedDevice) {
        peers.remove(device.id)
        blockedList.removeAll { it.id == device.id }
        blockedList.add(device)
    }

    @Synchronized
    override fun unblock(deviceId: String) {
        blockedList.removeAll { it.id == deviceId }
    }
}

internal actual object MulticastLock {
    actual fun acquire(): Any? = null
    actual fun release(token: Any?) {}
}

private fun longProperty(name: String, fallback: Long): Long = System.getProperty(name)?.toLongOrNull() ?: fallback

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = DeviceSyncEngine(
    config = DeviceSyncConfig(
        platformName = "jvm peer",
        tempDir = "$settingsDir/device-sync-tmp",
        discoveryPort = System.getProperty("fj.udp")!!.toInt(),
        tcpPort = System.getProperty("fj.tcp")!!.toInt(),
        bindHost = System.getProperty("fj.host")!!,
        sendBroadcast = false,
        timing = DeviceSyncTiming(
            offerWaitMs = longProperty("fetchjet.sync.offerWaitMs", 60_000L),
            receiveStartMs = longProperty("fetchjet.sync.receiveStartMs", 30_000L),
        ),
    ),
    platform = jvmDeviceSyncPlatform(),
    store = MemoryDeviceStore(),
    onReceive = onReceive
)
