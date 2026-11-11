@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyKeyExchangeResult
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDHKeyExchangeStandard
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.gethostname
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.sockaddr_in

internal class MacRecursiveLock {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().also { created ->
        memScoped {
            val attributes = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attributes.ptr)
            pthread_mutexattr_settype(attributes.ptr, PTHREAD_MUTEX_RECURSIVE)
            pthread_mutex_init(created.ptr, attributes.ptr)
            pthread_mutexattr_destroy(attributes.ptr)
        }
    }

    fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }

    fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }

    fun dispose() {
        pthread_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex)
    }
}

internal class MacSyncLock : SyncLock {
    private val lock = MacRecursiveLock()

    override fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

internal fun macDeviceSync(
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
    DeviceSyncPlatform(KtorSyncNetwork(MacSyncLock(), { macBroadcastAddresses() }, {}), MacSyncCrypto, MacSyncFiles) {
        MacSyncLock()
    },
)

internal fun macDeviceName(): String {
    val host = try {
        macHostName().substringBefore('.').trim()
    } catch (_: Throwable) {
        ""
    }
    return if (host.isBlank() || host.equals("localhost", ignoreCase = true)) "Mac" else "$host · Mac"
}

private const val IFF_UP = 0x1u
private const val IFF_LOOPBACK = 0x8u

private fun octets(value: UInt): String =
    "${value and 0xFFu}.${(value shr 8) and 0xFFu}.${(value shr 16) and 0xFFu}.${(value shr 24) and 0xFFu}"

internal fun macBroadcastAddresses(): List<String> = memScoped {
    val head = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
    val out = ArrayList<String>()
    try {
        var cursor = head.value
        while (cursor != null) {
            val item = cursor.pointed
            val address = item.ifa_addr
            val mask = item.ifa_netmask
            val flags = item.ifa_flags
            if (address != null && mask != null && address.pointed.sa_family.toInt() == AF_INET &&
                flags and IFF_UP != 0u && flags and IFF_LOOPBACK == 0u
            ) {
                val ip = address.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                val bits = mask.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                if (bits.countOneBits() in 1..31) out.add(octets(ip or bits.inv()))
            }
            cursor = item.ifa_next
        }
    } finally {
        freeifaddrs(head.value)
    }
    out.distinct()
}

internal fun macHostName(): String = memScoped {
    val buffer = allocArray<ByteVar>(256)
    if (gethostname(buffer, 255.convert()) != 0) "" else buffer.toKString()
}

private val P256_SPKI_PREFIX = "3059301306072a8648ce3d020106082a8648ce3d030107034200".decodeHex().toByteArray()

private fun cfData(bytes: ByteArray): CFDataRef =
    bytes.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
    } ?: throw IllegalStateException("out of memory")

private fun bytesOf(data: CFDataRef): ByteArray {
    val length = CFDataGetLength(data).toInt()
    if (length == 0) return ByteArray(0)
    return CFDataGetBytePtr(data)!!.readBytes(length)
}

private fun ecAttributes(publicKey: Boolean): CFMutableDictionaryRef = memScoped {
    val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        ?: throw IllegalStateException("out of memory")
    val bits = alloc<IntVar>().apply { value = 256 }
    val size = CFNumberCreate(null, kCFNumberIntType, bits.ptr)
    CFDictionaryAddValue(dict, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
    CFDictionaryAddValue(dict, kSecAttrKeySizeInBits, size)
    if (publicKey) CFDictionaryAddValue(dict, kSecAttrKeyClass, kSecAttrKeyClassPublic)
    CFRelease(size)
    dict
}

private inline fun <T> releasing(ref: CFTypeRef?, block: () -> T): T = try {
    block()
} finally {
    if (ref != null) CFRelease(ref)
}

private class MacEcdhKeyPair : SyncKeyPair {
    private var privateKey: SecKeyRef?
    override val publicSpki: ByteArray

    init {
        val attributes = ecAttributes(publicKey = false)
        privateKey = releasing(attributes) {
            memScoped {
                val error = alloc<CFErrorRefVar>()
                SecKeyCreateRandomKey(attributes, error.ptr).also { error.value?.let { CFRelease(it) } }
            }
        } ?: throw IllegalStateException("could not create an ECDH key")
        val publicKey = SecKeyCopyPublicKey(privateKey)
        val point = releasing(publicKey) {
            memScoped {
                val error = alloc<CFErrorRefVar>()
                val data = SecKeyCopyExternalRepresentation(publicKey, error.ptr)
                error.value?.let { CFRelease(it) }
                data?.let { releasing(it) { bytesOf(it) } }
            }
        }
        if (point == null || point.size != 65) {
            close()
            throw IllegalStateException("could not export the ECDH public key")
        }
        publicSpki = P256_SPKI_PREFIX + point
    }

    override fun derive(peerSpki: ByteArray): ByteArray {
        val key = privateKey ?: throw IllegalStateException("key closed")
        require(peerSpki.size >= 65) { "bad peer key" }
        val point = peerSpki.copyOfRange(peerSpki.size - 65, peerSpki.size)
        require(point[0] == 0x04.toByte()) { "bad peer key" }
        val pointData = cfData(point)
        val attributes = ecAttributes(publicKey = true)
        val params = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            val secret = memScoped {
                val error = alloc<CFErrorRefVar>()
                val peer = SecKeyCreateWithData(pointData, attributes, error.ptr)
                error.value?.let { CFRelease(it) }
                peer ?: throw IllegalStateException("bad peer key")
                releasing(peer) {
                    val shared = alloc<CFErrorRefVar>()
                    val data = SecKeyCopyKeyExchangeResult(key, kSecKeyAlgorithmECDHKeyExchangeStandard, peer, params, shared.ptr)
                    shared.value?.let { CFRelease(it) }
                    data?.let { releasing(it) { bytesOf(it) } }
                }
            } ?: throw IllegalStateException("ECDH failed")
            return MacSyncCrypto.sha256(secret)
        } finally {
            CFRelease(pointData)
            CFRelease(attributes)
            if (params != null) CFRelease(params)
        }
    }

    override fun close() {
        privateKey?.let { CFRelease(it) }
        privateKey = null
    }
}

private object MacSyncCrypto : SyncCrypto {
    override fun newKeyPair(): SyncKeyPair = MacEcdhKeyPair()

    override fun seal(key: ByteArray, plain: ByteArray): ByteArray = DesktopOs.aesGcmSeal(key, plain)

    override fun open(key: ByteArray, sealed: ByteArray): ByteArray = DesktopOs.aesGcmOpen(key, sealed)

    override fun sha256(data: ByteArray): ByteArray = data.toByteString().sha256().toByteArray()
}

private object MacSyncFiles : SyncFiles {
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

    override fun reader(path: String, chunk: Int): SyncChunkReader = MacChunkReader(path, chunk)

    override fun writer(path: String): SyncChunkWriter = MacChunkWriter(path)
}

private class MacChunkReader(path: String, chunk: Int) : SyncChunkReader {
    private val handle: FileHandle = try {
        FileSystem.SYSTEM.openReadOnly(path.toPath())
    } catch (_: Exception) {
        throw IllegalStateException("cannot read ${MacSyncFiles.name(path)}")
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

private class MacChunkWriter(path: String) : SyncChunkWriter {
    private val handle: FileHandle = try {
        FileSystem.SYSTEM.openReadWrite(path.toPath()).also { it.resize(0) }
    } catch (_: Exception) {
        throw IllegalStateException("cannot write ${MacSyncFiles.name(path)}")
    }
    private var offset = 0L

    override fun write(bytes: ByteArray) {
        handle.write(offset, bytes, 0, bytes.size)
        offset += bytes.size
    }

    override fun close() = handle.close()
}
