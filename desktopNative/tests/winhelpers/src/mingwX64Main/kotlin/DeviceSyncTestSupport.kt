package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.win.WinLock
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes

internal class MemoryDeviceStore(override val selfId: String = winRandomBytes(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }) : DeviceSyncStore {
    private val lock = WinLock()
    private val peers = LinkedHashMap<String, Pair<PairedDevice, ByteArray>>()
    private val blockedList = ArrayList<PairedDevice>()

    override var selfName: String = ""

    override fun pairedDevices(): List<PairedDevice> = lock.withLock { peers.values.map { it.first } }

    override fun isPaired(deviceId: String): Boolean = lock.withLock { peers.containsKey(deviceId) }

    override fun keyFor(deviceId: String): ByteArray? = lock.withLock { peers[deviceId]?.second?.copyOf() }

    override fun addPairing(device: PairedDevice, key: ByteArray) {
        lock.withLock { peers[device.id] = device to key.copyOf() }
    }

    override fun removePairing(deviceId: String) {
        lock.withLock { peers.remove(deviceId) }
    }

    override fun blockedDevices(): List<PairedDevice> = lock.withLock { blockedList.toList() }

    override fun isBlocked(deviceId: String): Boolean = lock.withLock { blockedList.any { it.id == deviceId } }

    override fun block(device: PairedDevice) {
        lock.withLock {
            peers.remove(device.id)
            blockedList.removeAll { it.id == device.id }
            blockedList.add(device)
        }
    }

    override fun unblock(deviceId: String) {
        lock.withLock { blockedList.removeAll { it.id == deviceId } }
    }

    fun forceKey(deviceId: String, key: ByteArray) {
        lock.withLock {
            val current = peers[deviceId] ?: return
            peers[deviceId] = current.first to key.copyOf()
        }
    }
}

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = winDeviceSync(
    platformName = "Test · Windows",
    tempDir = "$settingsDir/device-sync-tmp",
    store = MemoryDeviceStore(),
    onReceive = onReceive,
    discoveryPort = 28481,
    tcpPort = 28482,
    bindHost = "127.0.0.1",
    sendBroadcast = false,
)
