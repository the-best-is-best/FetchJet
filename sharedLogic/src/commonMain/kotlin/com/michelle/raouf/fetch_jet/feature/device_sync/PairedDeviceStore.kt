package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.security.Secrets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * This device's own id/name and the list of devices it has paired with, in the settings folder
 * (`devices.json`). Each peer's shared key is kept encrypted by [Secrets] (device key), so a
 * copied file does not reveal the keys and does not let another machine impersonate a pairing.
 */
@OptIn(ExperimentalEncodingApi::class)
object PairedDeviceStore {
    private val file get() = "${PathResolver().getSettingsDirectory()}/devices.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Serializable
    private data class StoredPeer(
        val id: String,
        val name: String,
        val platform: String = "",
        val lastSeenEpochMs: Long = 0L,
        /** The 32-byte shared pairing key, base64 then [Secrets]-encrypted (`fjenc1:` on disk). */
        val key: String = ""
    )

    @Serializable
    private data class DeviceFile(
        val selfId: String = "",
        val selfName: String = "",
        val peers: List<StoredPeer> = emptyList(),
        /** Devices whose pairing requests (and sends) are refused. */
        val blocked: List<PairedDevice> = emptyList()
    )

    private var cache: DeviceFile = load()

    private fun load(): DeviceFile = try {
        if (!FileSystem.SYSTEM.exists(file)) DeviceFile()
        else json.decodeFromString(
            DeviceFile.serializer(),
            FileSystem.SYSTEM.read(file) { readUtf8() })
    } catch (_: Throwable) {
        DeviceFile()
    }

    private fun save(updated: DeviceFile) {
        cache = updated
        try {
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) {
                writeUtf8(
                    json.encodeToString(
                        DeviceFile.serializer(),
                        updated
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    /** A stable id for this device, generated once and kept. */
    val selfId: String
        get() {
            cache.selfId.takeIf { it.isNotBlank() }?.let { return it }
            val id = Secrets.randomHex(16)
            save(cache.copy(selfId = id))
            return id
        }

    var selfName: String
        get() = cache.selfName
        set(value) {
            if (value != cache.selfName) save(cache.copy(selfName = value.trim()))
        }

    fun pairedDevices(): List<PairedDevice> =
        cache.peers.map { PairedDevice(it.id, it.name, it.platform, it.lastSeenEpochMs) }

    fun isPaired(deviceId: String): Boolean = cache.peers.any { it.id == deviceId }

    /** The decrypted 32-byte shared key for a paired device, or null if not paired / unreadable. */
    fun keyFor(deviceId: String): ByteArray? {
        val peer = cache.peers.firstOrNull { it.id == deviceId } ?: return null
        val revealed = Secrets.reveal(peer.key) ?: return null
        return try {
            Base64.decode(revealed)
        } catch (_: Throwable) {
            null
        }
    }

    fun addPairing(device: PairedDevice, key: ByteArray) {
        val stored = StoredPeer(
            id = device.id,
            name = device.name,
            platform = device.platform,
            lastSeenEpochMs = device.lastSeenEpochMs,
            key = Secrets.protect(Base64.encode(key))
        )
        val peers = cache.peers.filterNot { it.id == device.id } + stored
        save(cache.copy(peers = peers))
    }

    fun removePairing(deviceId: String) {
        save(cache.copy(peers = cache.peers.filterNot { it.id == deviceId }))
    }

    fun blockedDevices(): List<PairedDevice> = cache.blocked

    fun isBlocked(deviceId: String): Boolean = cache.blocked.any { it.id == deviceId }

    /** Block [device]: its pairing requests and sends are refused; any pairing with it is removed. */
    fun block(device: PairedDevice) {
        save(
            cache.copy(
                peers = cache.peers.filterNot { it.id == device.id },
                blocked = cache.blocked.filterNot { it.id == device.id } + device
            )
        )
    }

    fun unblock(deviceId: String) {
        save(cache.copy(blocked = cache.blocked.filterNot { it.id == deviceId }))
    }
}
