package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.linux.LinuxCrypto
import com.michelle.raouf.fetch_jet.platform.linux.LinuxLock
import com.michelle.raouf.fetch_jet.platform.linux.linuxBroadcastAddresses
import com.michelle.raouf.fetch_jet.platform.linux.linuxHostName
import okio.ByteString.Companion.toByteString
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal class LinuxSyncLock : SyncLock {
    private val lock = LinuxLock()

    override fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

internal fun linuxDeviceSync(
    platformName: String,
    tempDir: String,
    store: DeviceSyncStore,
    onReceive: (SendPayload, List<String>) -> Unit,
    discoveryPort: Int = DEVICE_SYNC_DISCOVERY_PORT,
    tcpPort: Int = DEVICE_SYNC_TCP_PORT,
    bindHost: String = DEVICE_SYNC_ANY_HOST,
    sendBroadcast: Boolean = DesktopOs.getEnv(DEVICE_SYNC_NO_BROADCAST_ENV) != "1",
    timing: DeviceSyncTiming = DeviceSyncTiming(),
): DeviceSyncEngine = nativeDeviceSync(
    platformName, tempDir, store, onReceive, discoveryPort, tcpPort, bindHost, sendBroadcast, timing,
    DeviceSyncPlatform(KtorSyncNetwork(LinuxSyncLock(), { linuxBroadcastAddresses() }, {}), LinuxSyncCrypto, LinuxSyncFiles) {
        LinuxSyncLock()
    },
)

internal fun linuxDeviceName(): String {
    val host = try {
        linuxHostName().substringBefore('.').trim()
    } catch (_: Throwable) {
        ""
    }
    return if (host.isBlank() || host.equals("localhost", ignoreCase = true)) "Linux" else "$host · Linux"
}

private object LinuxSyncCrypto : SyncCrypto {
    override fun newKeyPair(): SyncKeyPair {
        val keys = LinuxCrypto.newEcdhP256()
        return object : SyncKeyPair {
            override val publicSpki: ByteArray get() = keys.publicSpki
            override fun derive(peerSpki: ByteArray): ByteArray = sha256(keys.sharedSecret(peerSpki))
            override fun close() = keys.close()
        }
    }

    override fun seal(key: ByteArray, plain: ByteArray): ByteArray = DesktopOs.aesGcmSeal(key, plain)

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray = DesktopOs.aesGcmOpen(key, sealed)

    override fun sha256(data: ByteArray): ByteArray = data.toByteString().sha256().toByteArray()
}

private object LinuxSyncFiles : SyncFiles {
    override fun absolute(path: String): String = DesktopOs.fullPath(path)

    override fun child(dir: String, name: String): String = DesktopOs.join(dir, name)

    override fun name(path: String): String = path.substringAfterLast('/')

    override fun size(path: String): Long? = DesktopOs.fileInfo(path)?.takeIf { !it.isDirectory }?.size

    override fun createDirectories(path: String) {
        DesktopOs.createDirectories(path)
    }

    override fun deleteRecursively(path: String) {
        DesktopOs.deleteRecursively(path)
    }

    override fun reader(path: String, chunk: Int): SyncChunkReader = LinuxChunkReader(path, chunk)

    override fun writer(path: String): SyncChunkWriter = LinuxChunkWriter(path)
}

private class LinuxChunkReader(path: String, chunk: Int) : SyncChunkReader {
    private val handle: FileHandle = try {
        FileSystem.SYSTEM.openReadOnly(path.toPath())
    } catch (_: Exception) {
        throw IllegalStateException("cannot read ${LinuxSyncFiles.name(path)}")
    }
    private val buffer = ByteArray(chunk)
    private var offset = 0L

    override fun next(): ByteArray? {
        val read = handle.read(offset, buffer, 0, buffer.size)
        if (read <= 0) return null
        offset += read
        return buffer.copyOf(read)
    }

    override fun close() = handle.close()
}

private class LinuxChunkWriter(path: String) : SyncChunkWriter {
    private val handle: FileHandle = try {
        FileSystem.SYSTEM.openReadWrite(path.toPath()).also { it.resize(0) }
    } catch (_: Exception) {
        throw IllegalStateException("cannot write ${LinuxSyncFiles.name(path)}")
    }
    private var offset = 0L

    override fun write(bytes: ByteArray) {
        handle.write(offset, bytes, 0, bytes.size)
        offset += bytes.size
    }

    override fun close() = handle.close()
}
