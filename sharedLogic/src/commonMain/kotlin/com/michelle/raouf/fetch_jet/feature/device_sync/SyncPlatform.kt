package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.CancellationException

internal class SyncDatagram(val host: String, val bytes: ByteArray)

internal interface SyncUdpSocket {
    suspend fun receive(): SyncDatagram
    suspend fun send(host: String, port: Int, body: ByteArray)
    fun close()
}

internal interface SyncConnection {
    suspend fun read(count: Int, timeoutMs: Long): ByteArray?
    suspend fun write(bytes: ByteArray)
    suspend fun flush()
    fun close()
}

internal interface SyncServerSocket {
    val port: Int
    suspend fun accept(): SyncConnection
    fun close()
}

internal interface SyncNetwork {
    suspend fun bindDiscovery(host: String?, port: Int): SyncUdpSocket
    suspend fun openAnnouncer(host: String?): SyncUdpSocket
    suspend fun listen(host: String?, port: Int): SyncServerSocket
    suspend fun connect(host: String, port: Int, timeoutMs: Long): SyncConnection?
    suspend fun broadcast(via: SyncUdpSocket, port: Int, body: ByteArray): List<String>
    suspend fun sweepSubnet(port: Int, body: ByteArray) {}
    fun close() {}
}

internal interface SyncKeyPair {
    val publicSpki: ByteArray
    fun derive(peerSpki: ByteArray): ByteArray
    fun close()
}

internal interface SyncCrypto {
    fun newKeyPair(): SyncKeyPair
    fun seal(key: ByteArray, plain: ByteArray): ByteArray
    fun open(key: ByteArray, sealed: ByteArray): ByteArray
    fun sha256(data: ByteArray): ByteArray
}

internal interface SyncChunkReader {
    fun next(): ByteArray?
    fun close()
}

internal interface SyncChunkWriter {
    fun write(bytes: ByteArray)
    fun close()
}

internal interface SyncFiles {
    fun absolute(path: String): String
    fun child(dir: String, name: String): String
    fun name(path: String): String
    fun size(path: String): Long?
    fun createDirectories(path: String)
    fun deleteRecursively(path: String)
    fun reader(path: String, chunk: Int): SyncChunkReader
    fun writer(path: String): SyncChunkWriter
}

internal interface SyncLock {
    fun <T> withLock(block: () -> T): T
}

internal class DeviceSyncPlatform(
    val network: SyncNetwork,
    val crypto: SyncCrypto,
    val files: SyncFiles,
    val newLock: () -> SyncLock,
)

internal suspend fun SyncUdpSocket.sendQuietly(host: String, port: Int, body: ByteArray) {
    try {
        send(host, port, body)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
    }
}

internal suspend fun SyncUdpSocket.sendToAll(hosts: List<String>, port: Int, body: ByteArray): List<String> {
    for (host in hosts) sendQuietly(host, port, body)
    return hosts
}

internal fun SyncUdpSocket.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
    }
}

internal fun SyncConnection.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
    }
}

internal fun SyncServerSocket.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
    }
}
