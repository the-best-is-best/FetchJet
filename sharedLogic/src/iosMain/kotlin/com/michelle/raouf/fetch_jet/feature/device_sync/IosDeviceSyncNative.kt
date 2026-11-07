package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

/** One ECDH key pair the native side generated for a pairing handshake. */
class IosEcKeyPair(val keyId: Long, val publicX509: NSData)

/** A UDP datagram arrived on the discovery port (called from a native background thread). */
interface IosUdpPacketListener {
    fun onPacket(senderIp: String, data: NSData)
}

/** A TCP connection was accepted (called from a native background thread). */
interface IosTcpConnectionListener {
    fun onConnection(connId: Long)
}

/**
 * The native (Swift / CryptoKit + POSIX sockets) half of iOS device sync. The protocol, state and
 * flows live in Kotlin ([DeviceSyncImpl] in iosMain); only the two things that must be native are
 * here: the crypto (so it byte-matches the JVM/Android `CryptoBox` + ECDH) and the raw LAN sockets.
 *
 * Set by the Swift app at startup through [IosDeviceSyncNativeRegistry].
 *
 * Threads: [startUdpListen] and [startTcpServer] run their receive/accept loops on the native side
 * and call the callbacks from a background thread; everything else is blocking and is called from
 * Kotlin coroutines on a background dispatcher.
 */
interface IosDeviceSyncNative {

    // ---- crypto (CryptoKit, P-256) ----------------------------------------

    /** A fresh P-256 key pair; [IosEcKeyPair.publicX509] is the DER SubjectPublicKeyInfo (X.509). */
    fun generateEcKeyPair(): IosEcKeyPair?

    /**
     * SHA-256 of the raw ECDH shared secret between [keyId] and the peer's X.509 public key, as
     * 32 bytes. Must match the JVM/Android `deriveKey` (`SHA-256(agree.generateSecret())`).
     */
    fun deriveKey(keyId: Long, peerPublicX509: NSData): NSData?

    fun releaseKeyPair(keyId: Long)

    /** AES-256-GCM. Layout: 12-byte nonce ++ ciphertext ++ 16-byte tag (same as `CryptoBox`). */
    fun aesSeal(key: NSData, plain: NSData): NSData?

    /** Returns null if the tag does not verify. */
    fun aesOpen(key: NSData, sealed: NSData): NSData?

    /** SHA-256 of [data] (for the pairing code; standard, so it matches every platform). */
    fun sha256(data: NSData): NSData

    // ---- UDP discovery ----------------------------------------------------

    /** Listen for UDP datagrams on [port]; each one calls [listener] with the sender IP + bytes. */
    fun startUdpListen(port: Int, listener: IosUdpPacketListener): Boolean

    /** Send [data] to the IPv4 broadcast address(es) on [port]. */
    fun sendUdpBroadcast(port: Int, data: NSData)

    fun stopUdp()

    // ---- TCP server (receiver) --------------------------------------------

    /**
     * Bind a TCP server on [port] (0 = any) and accept connections; each accepted socket calls
     * [listener] with a connection id usable with [tcpReadFully] / [tcpWrite] / [tcpClose].
     * Returns the bound port, or -1 on failure.
     */
    fun startTcpServer(port: Int, listener: IosTcpConnectionListener): Int

    fun stopTcpServer()

    // ---- TCP connection (blocking byte IO) --------------------------------

    /** Connect to [host]:[port]; returns a connection id, or -1 on failure/timeout. */
    fun tcpConnect(host: String, port: Int, timeoutMs: Int): Long

    /** Block until exactly [length] bytes are read; null on EOF or error. */
    fun tcpReadFully(connId: Long, length: Int): NSData?

    fun tcpWrite(connId: Long, data: NSData): Boolean

    fun tcpClose(connId: Long)
}

/** Set by the Swift app at startup (`IosDeviceSyncNativeRegistry.shared.native = ...`). */
object IosDeviceSyncNativeRegistry {
    var native: IosDeviceSyncNative? = null
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData.dataWithBytes(null, 0.convert())
    else usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }
