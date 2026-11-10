package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSData
import platform.Foundation.NSRecursiveLock

internal fun iosDeviceSyncPlatform(native: IosDeviceSyncNative): DeviceSyncPlatform =
    DeviceSyncPlatform(IosSyncNetwork(native), IosSyncCrypto(native), IosSyncFiles) { IosSyncLock() }

internal class IosSyncLock : SyncLock {
    private val lock = NSRecursiveLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

private class IosSyncNetwork(private val native: IosDeviceSyncNative) : SyncNetwork {
    override suspend fun bindDiscovery(host: String?, port: Int): SyncUdpSocket {
        val packets = Channel<SyncDatagram>(64, BufferOverflow.DROP_OLDEST)
        val listener = object : IosUdpPacketListener {
            override fun onPacket(senderIp: String, data: NSData) {
                packets.trySend(SyncDatagram(senderIp, data.toByteArray()))
            }
        }
        if (!native.startUdpListen(port, listener)) throw IllegalStateException("cannot listen on UDP $port")
        return IosUdpSocket(native, packets)
    }

    override suspend fun openAnnouncer(host: String?): SyncUdpSocket = IosUdpSocket(native, null)

    override suspend fun listen(host: String?, port: Int): SyncServerSocket {
        val accepted = Channel<Long>(Channel.UNLIMITED)
        val listener = object : IosTcpConnectionListener {
            override fun onConnection(connId: Long) {
                if (accepted.trySend(connId).isFailure) native.tcpClose(connId)
            }
        }
        val bound = native.startTcpServer(port, listener)
        if (bound <= 0) throw IllegalStateException("cannot listen on TCP $port")
        return IosServerSocket(native, bound, accepted)
    }

    override suspend fun connect(host: String, port: Int, timeoutMs: Long): SyncConnection? {
        val connId = native.tcpConnect(host, port, timeoutMs.toInt())
        return if (connId < 0) null else IosConnection(native, connId)
    }

    override suspend fun broadcast(via: SyncUdpSocket, port: Int, body: ByteArray): List<String> {
        native.sendUdpBroadcast(port, body.toNSData())
        return emptyList()
    }

    override suspend fun sweepSubnet(port: Int, body: ByteArray) {
        native.sendUdpToSubnet(port, body.toNSData())
    }
}

private class IosUdpSocket(
    private val native: IosDeviceSyncNative,
    private val packets: Channel<SyncDatagram>?,
) : SyncUdpSocket {
    override suspend fun receive(): SyncDatagram =
        (packets ?: throw IllegalStateException("not listening")).receive()

    override suspend fun send(host: String, port: Int, body: ByteArray) {
        native.sendUdpTo(host, port, body.toNSData())
    }

    override fun close() {
        if (packets == null) return
        native.stopUdp()
        packets.close()
    }
}

private class IosServerSocket(
    private val native: IosDeviceSyncNative,
    override val port: Int,
    private val accepted: Channel<Long>,
) : SyncServerSocket {
    override suspend fun accept(): SyncConnection = IosConnection(native, accepted.receive())

    override fun close() {
        native.stopTcpServer()
        accepted.close()
    }
}

private class IosConnection(private val native: IosDeviceSyncNative, private val connId: Long) : SyncConnection {
    override suspend fun read(count: Int, timeoutMs: Long): ByteArray? =
        native.tcpReadFully(connId, count)?.toByteArray()?.takeIf { it.size == count }

    override suspend fun write(bytes: ByteArray) {
        if (!native.tcpWrite(connId, bytes.toNSData())) throw IllegalStateException("write failed")
    }

    override suspend fun flush() {}

    override fun close() = native.tcpClose(connId)
}

private class IosSyncCrypto(private val native: IosDeviceSyncNative) : SyncCrypto {
    override fun newKeyPair(): SyncKeyPair {
        val pair = native.generateEcKeyPair() ?: throw IllegalStateException("crypto unavailable")
        return object : SyncKeyPair {
            override val publicSpki: ByteArray get() = pair.publicX509.toByteArray()

            override fun derive(peerSpki: ByteArray): ByteArray =
                native.deriveKey(pair.keyId, peerSpki.toNSData())?.toByteArray()
                    ?: throw IllegalStateException("key agreement failed")

            override fun close() = native.releaseKeyPair(pair.keyId)
        }
    }

    override fun seal(key: ByteArray, plain: ByteArray): ByteArray =
        native.aesSeal(key.toNSData(), plain.toNSData())?.toByteArray() ?: throw IllegalStateException("encryption failed")

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray =
        native.aesOpen(key.toNSData(), sealed.toNSData())?.toByteArray() ?: throw IllegalStateException("bad chunk")

    override fun sha256(data: ByteArray): ByteArray = native.sha256(data.toNSData()).toByteArray()
}

private object IosSyncFiles : SyncFiles {
    override fun absolute(path: String): String = path

    override fun child(dir: String, name: String): String = "$dir/$name"

    override fun name(path: String): String = path.toPath().name

    override fun size(path: String): Long? =
        FileSystem.SYSTEM.metadataOrNull(path.toPath())?.takeIf { !it.isDirectory }?.let { it.size ?: 0L }

    override fun createDirectories(path: String) {
        FileSystem.SYSTEM.createDirectories(path.toPath())
    }

    override fun deleteRecursively(path: String) {
        try {
            FileSystem.SYSTEM.deleteRecursively(path.toPath())
        } catch (_: Throwable) {
        }
    }

    override fun reader(path: String, chunk: Int): SyncChunkReader = IosChunkReader(path, chunk)

    override fun writer(path: String): SyncChunkWriter = IosChunkWriter(path)
}

private class IosChunkReader(path: String, private val chunk: Int) : SyncChunkReader {
    private val source = FileSystem.SYSTEM.source(path.toPath()).buffer()

    override fun next(): ByteArray? {
        if (source.exhausted()) return null
        val sink = okio.Buffer()
        val n = source.read(sink, chunk.toLong())
        if (n <= 0L) return null
        return sink.readByteArray()
    }

    override fun close() = source.close()
}

private class IosChunkWriter(path: String) : SyncChunkWriter {
    private val sink = FileSystem.SYSTEM.sink(path.toPath()).buffer()

    override fun write(bytes: ByteArray) {
        sink.write(bytes)
    }

    override fun close() {
        sink.flush()
        sink.close()
    }
}
