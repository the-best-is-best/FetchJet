package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.core.security.CryptoBox
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

internal fun jvmDeviceSyncPlatform(): DeviceSyncPlatform =
    DeviceSyncPlatform(JvmSyncNetwork, JvmSyncCrypto, JvmSyncFiles) { JvmSyncLock() }

internal class JvmSyncLock : SyncLock {
    override fun <T> withLock(block: () -> T): T = synchronized(this) { block() }
}

private object JvmSyncNetwork : SyncNetwork {
    override suspend fun bindDiscovery(host: String?, port: Int): SyncUdpSocket {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(address(host, port))
        } catch (t: Throwable) {
            socket.close()
            throw t
        }
        return JvmUdpSocket(socket)
    }

    override suspend fun openAnnouncer(host: String?): SyncUdpSocket {
        val socket = if (host == null) DatagramSocket() else DatagramSocket(InetSocketAddress(host, 0))
        socket.broadcast = true
        return JvmUdpSocket(socket)
    }

    override suspend fun listen(host: String?, port: Int): SyncServerSocket {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(address(host, port))
        } catch (t: Throwable) {
            server.close()
            throw t
        }
        return JvmServerSocket(server)
    }

    override suspend fun connect(host: String, port: Int, timeoutMs: Long): SyncConnection? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            JvmConnection(socket)
        } catch (_: Throwable) {
            runCatching { socket.close() }
            null
        }
    }

    override suspend fun broadcast(via: SyncUdpSocket, port: Int, body: ByteArray): List<String> =
        via.sendToAll(broadcastAddresses(), port, body)

    private fun address(host: String?, port: Int): InetSocketAddress =
        if (host == null) InetSocketAddress(port) else InetSocketAddress(host, port)

    private fun broadcastAddresses(): List<String> {
        val out = LinkedHashSet<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address as? Inet4Address ?: continue
                    val b = ia.broadcast ?: computeBroadcast(a, ia.networkPrefixLength.toInt())
                    b?.hostAddress?.let { out.add(it) }
                }
            }
        } catch (_: Throwable) {
        }
        if (out.isEmpty()) out.add("255.255.255.255")
        return out.toList()
    }

    private fun computeBroadcast(addr: Inet4Address, prefix: Int): InetAddress? = try {
        if (prefix !in 1..31) {
            null
        } else {
            val a = addr.address
            val ip = ((a[0].toInt() and 0xff) shl 24) or ((a[1].toInt() and 0xff) shl 16) or
                ((a[2].toInt() and 0xff) shl 8) or (a[3].toInt() and 0xff)
            val bc = ip or (-0x1 ushr prefix)
            InetAddress.getByAddress(byteArrayOf((bc ushr 24).toByte(), (bc ushr 16).toByte(), (bc ushr 8).toByte(), bc.toByte()))
        }
    } catch (_: Throwable) {
        null
    }
}

private class JvmUdpSocket(private val socket: DatagramSocket) : SyncUdpSocket {
    private val buffer = ByteArray(SYNC_MAX_DATAGRAM + 1)

    override suspend fun receive(): SyncDatagram {
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        return SyncDatagram(packet.address?.hostAddress ?: "", packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
    }

    override suspend fun send(host: String, port: Int, body: ByteArray) {
        socket.send(DatagramPacket(body, body.size, InetAddress.getByName(host), port))
    }

    override fun close() = socket.close()
}

private class JvmServerSocket(private val server: ServerSocket) : SyncServerSocket {
    override val port: Int = server.localPort

    override suspend fun accept(): SyncConnection = JvmConnection(server.accept())

    override fun close() = server.close()
}

private class JvmConnection(private val socket: Socket) : SyncConnection {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = socket.getOutputStream().buffered()

    override suspend fun read(count: Int, timeoutMs: Long): ByteArray? = try {
        socket.soTimeout = timeoutMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        ByteArray(count).also { input.readFully(it) }
    } catch (_: Throwable) {
        null
    }

    override suspend fun write(bytes: ByteArray) = output.write(bytes)

    override suspend fun flush() = output.flush()

    override fun close() = socket.close()
}

private object JvmSyncCrypto : SyncCrypto {
    override fun newKeyPair(): SyncKeyPair {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        return object : SyncKeyPair {
            override val publicSpki: ByteArray get() = pair.public.encoded

            override fun derive(peerSpki: ByteArray): ByteArray {
                val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerSpki))
                val agree = KeyAgreement.getInstance("ECDH").apply {
                    init(pair.private)
                    doPhase(peer, true)
                }
                return MessageDigest.getInstance("SHA-256").digest(agree.generateSecret())
            }

            override fun close() {}
        }
    }

    override fun seal(key: ByteArray, plain: ByteArray): ByteArray = CryptoBox.seal(key, plain)

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray = CryptoBox.open(key, sealed)

    override fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}

private object JvmSyncFiles : SyncFiles {
    override fun absolute(path: String): String = File(path).absolutePath

    override fun child(dir: String, name: String): String = File(dir, name).path

    override fun name(path: String): String = File(path).name

    override fun size(path: String): Long? = File(path).takeIf { it.isFile }?.length()

    override fun createDirectories(path: String) {
        File(path).mkdirs()
    }

    override fun deleteRecursively(path: String) {
        File(path).deleteRecursively()
    }

    override fun reader(path: String, chunk: Int): SyncChunkReader = JvmChunkReader(File(path).inputStream().buffered(), chunk)

    override fun writer(path: String): SyncChunkWriter = JvmChunkWriter(File(path).outputStream().buffered())
}

private class JvmChunkReader(private val stream: InputStream, chunk: Int) : SyncChunkReader {
    private val buffer = ByteArray(chunk)

    override fun next(): ByteArray? {
        val n = stream.read(buffer)
        return if (n <= 0) null else buffer.copyOf(n)
    }

    override fun close() = stream.close()
}

private class JvmChunkWriter(private val stream: OutputStream) : SyncChunkWriter {
    override fun write(bytes: ByteArray) = stream.write(bytes)

    override fun close() = stream.close()
}
