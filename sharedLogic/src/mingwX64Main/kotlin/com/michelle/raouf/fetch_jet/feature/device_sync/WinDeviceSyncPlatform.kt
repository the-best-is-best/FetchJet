@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WIN_OPEN_EXISTING
import com.michelle.raouf.fetch_jet.platform.win.WinEcdhP256
import com.michelle.raouf.fetch_jet.platform.win.WinLock
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winBroadcastAddresses
import com.michelle.raouf.fetch_jet.platform.win.winCloseFile
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winReadAt
import com.michelle.raouf.fetch_jet.platform.win.winSha256
import com.michelle.raouf.fetch_jet.platform.win.winUdpIgnoreUnreachable
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.HANDLE

internal fun winDeviceSync(
    platformName: String,
    tempDir: String,
    store: DeviceSyncStore,
    onReceive: (SendPayload, List<String>) -> Unit,
    discoveryPort: Int = DEVICE_SYNC_DISCOVERY_PORT,
    tcpPort: Int = DEVICE_SYNC_TCP_PORT,
    bindHost: String = DEVICE_SYNC_ANY_HOST,
    sendBroadcast: Boolean = winGetEnv(DEVICE_SYNC_NO_BROADCAST_ENV) != "1",
    timing: DeviceSyncTiming = DeviceSyncTiming(),
): DeviceSyncEngine = nativeDeviceSync(
    platformName, tempDir, store, onReceive, discoveryPort, tcpPort, bindHost, sendBroadcast, timing,
    DeviceSyncPlatform(
        KtorSyncNetwork(WinSyncLock(), { winBroadcastAddresses() }, { winUdpIgnoreUnreachable(it.toULong()) }),
        WinSyncCrypto,
        WinSyncFiles,
    ) { WinSyncLock() },
)

internal class WinSyncLock : SyncLock {
    private val lock = WinLock()

    override fun <T> withLock(block: () -> T): T = lock.withLock { block() }
}

private object WinSyncCrypto : SyncCrypto {
    override fun newKeyPair(): SyncKeyPair {
        val keys = WinEcdhP256()
        return object : SyncKeyPair {
            override val publicSpki: ByteArray get() = keys.publicSpki
            override fun derive(peerSpki: ByteArray): ByteArray = keys.deriveSha256(peerSpki)
            override fun close() = keys.close()
        }
    }

    override fun seal(key: ByteArray, plain: ByteArray): ByteArray = winAesGcmSeal(key, plain)

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray = winAesGcmOpen(key, sealed)

    override fun sha256(data: ByteArray): ByteArray = winSha256(data)
}

private object WinSyncFiles : SyncFiles {
    override fun absolute(path: String): String = winFullPath(path.replace('/', '\\'))

    override fun child(dir: String, name: String): String = "$dir\\$name"

    override fun name(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

    override fun size(path: String): Long? = winFileInfo(path)?.takeIf { !it.isDirectory }?.size

    override fun createDirectories(path: String) {
        winCreateDirectories(path)
    }

    override fun deleteRecursively(path: String) {
        winDeleteRecursively(path)
    }

    override fun reader(path: String, chunk: Int): SyncChunkReader = WinChunkReader(path, chunk)

    override fun writer(path: String): SyncChunkWriter = WinChunkWriter(path)
}

private class WinChunkReader(path: String, chunk: Int) : SyncChunkReader {
    private var handle: HANDLE? = winOpenFile(path, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw IllegalStateException("cannot read ${WinSyncFiles.name(path)}")
    private val buffer = ByteArray(chunk)
    private var offset = 0L

    override fun next(): ByteArray? {
        val read = winReadAt(handle, offset, buffer)
        if (read < 0) throw IllegalStateException("read failed")
        if (read == 0) return null
        offset += read
        return buffer.copyOf(read)
    }

    override fun close() {
        winCloseFile(handle)
        handle = null
    }
}

private class WinChunkWriter(path: String) : SyncChunkWriter {
    private var handle: HANDLE? = winOpenFile(path, write = true, disposition = WIN_CREATE_ALWAYS)
        ?: throw IllegalStateException("cannot write ${WinSyncFiles.name(path)}")
    private var offset = 0L

    override fun write(bytes: ByteArray) {
        if (!winWriteAt(handle, offset, bytes)) throw IllegalStateException("write failed")
        offset += bytes.size
    }

    override fun close() {
        winCloseFile(handle)
        handle = null
    }
}
