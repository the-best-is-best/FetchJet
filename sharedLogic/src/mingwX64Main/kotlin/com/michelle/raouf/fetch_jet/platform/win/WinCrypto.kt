@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.memset
import platform.windows.BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO
import platform.windows.BCryptBuffer
import platform.windows.BCryptBufferDesc
import platform.windows.BCryptCreateHash
import platform.windows.BCryptDecrypt
import platform.windows.BCryptDeriveKey
import platform.windows.BCryptDestroyHash
import platform.windows.BCryptDestroyKey
import platform.windows.BCryptDestroySecret
import platform.windows.BCryptEncrypt
import platform.windows.BCryptExportKey
import platform.windows.BCryptFinalizeKeyPair
import platform.windows.BCryptFinishHash
import platform.windows.BCryptGenRandom
import platform.windows.BCryptGenerateKeyPair
import platform.windows.BCryptGenerateSymmetricKey
import platform.windows.BCryptHashData
import platform.windows.BCryptImportKeyPair
import platform.windows.BCryptOpenAlgorithmProvider
import platform.windows.BCryptSecretAgreement
import platform.windows.BCryptSetProperty
import platform.windows.BCryptVerifySignature

class WinCryptoException(val where: String, val status: Int) :
    Exception("$where failed: 0x${status.toUInt().toString(16)}")

private const val STATUS_AUTH_TAG_MISMATCH = -0x3FFF5FFE
private const val STATUS_INVALID_SIGNATURE = -0x3FFF6000
private const val RNG_SYSTEM_PREFERRED = 2u
private const val ECDSA_PUBLIC_P256_MAGIC = 0x31534345u
private const val ECDH_PUBLIC_P256_MAGIC = 0x314B4345u
private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BYTES = 16
private const val ECC_BLOB = "ECCPUBLICBLOB"

private fun ok(status: Int, where: String) {
    if (status != 0) throw WinCryptoException(where, status)
}

private fun openAlgorithm(id: String): COpaquePointer = memScoped {
    val handle = alloc<COpaquePointerVar>()
    ok(BCryptOpenAlgorithmProvider(handle.ptr, id, null, 0u), "BCryptOpenAlgorithmProvider($id)")
    handle.value ?: throw WinCryptoException("BCryptOpenAlgorithmProvider($id)", -1)
}

private val shaProvider: COpaquePointer by lazy { openAlgorithm("SHA256") }

private val aesGcmProvider: COpaquePointer by lazy {
    val provider = openAlgorithm("AES")
    memScoped {
        val mode = "ChainingModeGCM"
        ok(
            BCryptSetProperty(
                provider, "ChainingMode", mode.wcstr.ptr.reinterpret(), ((mode.length + 1) * 2).toUInt(), 0u
            ),
            "BCryptSetProperty(ChainingMode)"
        )
    }
    provider
}

private val ecdsaProvider: COpaquePointer by lazy { openAlgorithm("ECDSA_P256") }

private val ecdhProvider: COpaquePointer by lazy { openAlgorithm("ECDH_P256") }

fun winRandomBytes(count: Int): ByteArray {
    val out = ByteArray(count)
    if (count == 0) return out
    out.usePinned {
        ok(BCryptGenRandom(null, it.addressOf(0).reinterpret(), count.toUInt(), RNG_SYSTEM_PREFERRED), "BCryptGenRandom")
    }
    return out
}

class WinSha256 {
    private var hash: COpaquePointer? = memScoped {
        val handle = alloc<COpaquePointerVar>()
        ok(BCryptCreateHash(shaProvider, handle.ptr, null, 0u, null, 0u, 0u), "BCryptCreateHash")
        handle.value
    }

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        if (length <= 0) return
        bytes.usePinned {
            ok(BCryptHashData(hash, it.addressOf(offset).reinterpret(), length.toUInt(), 0u), "BCryptHashData")
        }
    }

    fun digest(): ByteArray {
        val out = ByteArray(32)
        out.usePinned { ok(BCryptFinishHash(hash, it.addressOf(0).reinterpret(), 32u, 0u), "BCryptFinishHash") }
        close()
        return out
    }

    fun close() {
        hash?.let { BCryptDestroyHash(it) }
        hash = null
    }
}

fun winSha256(bytes: ByteArray): ByteArray {
    val hash = WinSha256()
    try {
        hash.update(bytes)
        return hash.digest()
    } finally {
        hash.close()
    }
}

fun winSha256OfFile(path: String, bufferBytes: Int = 256 * 1024): ByteArray? {
    val handle = winOpenFile(path, write = false, disposition = WIN_OPEN_EXISTING) ?: return null
    val hash = WinSha256()
    try {
        val buffer = ByteArray(bufferBytes)
        var offset = 0L
        while (true) {
            val read = winReadAt(handle, offset, buffer)
            if (read < 0) return null
            if (read == 0) break
            hash.update(buffer, 0, read)
            offset += read
        }
        return hash.digest()
    } finally {
        hash.close()
        winCloseFile(handle)
    }
}

private inline fun <T> withAesKey(key: ByteArray, block: (COpaquePointer) -> T): T {
    require(key.size == 32) { "the key must be 32 bytes" }
    val handle = memScoped {
        val out = alloc<COpaquePointerVar>()
        key.usePinned {
            ok(
                BCryptGenerateSymmetricKey(
                    aesGcmProvider, out.ptr, null, 0u, it.addressOf(0).reinterpret(), key.size.toUInt(), 0u
                ),
                "BCryptGenerateSymmetricKey"
            )
        }
        out.value ?: throw WinCryptoException("BCryptGenerateSymmetricKey", -1)
    }
    try {
        return block(handle)
    } finally {
        BCryptDestroyKey(handle)
    }
}

private fun gcm(key: ByteArray, nonce: ByteArray, input: ByteArray, tag: ByteArray, encrypt: Boolean): ByteArray {
    require(nonce.size == GCM_NONCE_BYTES) { "the nonce must be 12 bytes" }
    val output = ByteArray(input.size)
    withAesKey(key) { handle ->
        memScoped {
            val info = alloc<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>()
            memset(info.ptr, 0, sizeOf<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>().convert())
            info.cbSize = sizeOf<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>().toUInt()
            info.dwInfoVersion = 1u
            val done = alloc<UIntVar>()
            nonce.usePinned { pinnedNonce ->
                tag.usePinned { pinnedTag ->
                    info.pbNonce = pinnedNonce.addressOf(0).reinterpret()
                    info.cbNonce = nonce.size.toUInt()
                    info.pbTag = pinnedTag.addressOf(0).reinterpret()
                    info.cbTag = tag.size.toUInt()
                    val status = if (input.isEmpty()) {
                        if (encrypt) BCryptEncrypt(handle, null, 0u, info.ptr, null, 0u, null, 0u, done.ptr, 0u)
                        else BCryptDecrypt(handle, null, 0u, info.ptr, null, 0u, null, 0u, done.ptr, 0u)
                    } else {
                        input.usePinned { pinnedIn ->
                            output.usePinned { pinnedOut ->
                                val source = pinnedIn.addressOf(0).reinterpret<UByteVar>()
                                val target = pinnedOut.addressOf(0).reinterpret<UByteVar>()
                                val size = input.size.toUInt()
                                if (encrypt) BCryptEncrypt(handle, source, size, info.ptr, null, 0u, target, size, done.ptr, 0u)
                                else BCryptDecrypt(handle, source, size, info.ptr, null, 0u, target, size, done.ptr, 0u)
                            }
                        }
                    }
                    if (status == STATUS_AUTH_TAG_MISMATCH) throw WinCryptoException("AES-GCM tag check", status)
                    ok(status, if (encrypt) "BCryptEncrypt" else "BCryptDecrypt")
                }
            }
        }
    }
    return output
}

fun winAesGcmSeal(key: ByteArray, plain: ByteArray, nonce: ByteArray = winRandomBytes(GCM_NONCE_BYTES)): ByteArray {
    val tag = ByteArray(GCM_TAG_BYTES)
    val cipher = gcm(key, nonce, plain, tag, encrypt = true)
    return nonce + cipher + tag
}

fun winAesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray {
    require(sealed.size >= GCM_NONCE_BYTES + GCM_TAG_BYTES) { "sealed message too short" }
    val nonce = sealed.copyOfRange(0, GCM_NONCE_BYTES)
    val cipher = sealed.copyOfRange(GCM_NONCE_BYTES, sealed.size - GCM_TAG_BYTES)
    val tag = sealed.copyOfRange(sealed.size - GCM_TAG_BYTES, sealed.size)
    return gcm(key, nonce, cipher, tag, encrypt = false)
}

private val P256_SPKI_PREFIX = "3059301306072a8648ce3d020106082a8648ce3d030107034200".hexToBytes()

fun winP256PointFromSpki(spki: ByteArray): ByteArray? {
    if (spki.size != P256_SPKI_PREFIX.size + 65) return null
    for (i in P256_SPKI_PREFIX.indices) if (spki[i] != P256_SPKI_PREFIX[i]) return null
    if (spki[P256_SPKI_PREFIX.size] != 4.toByte()) return null
    return spki.copyOfRange(P256_SPKI_PREFIX.size + 1, spki.size)
}

fun winP256SpkiFromPoint(point: ByteArray): ByteArray {
    require(point.size == 64) { "a P-256 point is 64 bytes" }
    return P256_SPKI_PREFIX + byteArrayOf(4) + point
}

fun winEcdsaDerToRaw(der: ByteArray, partBytes: Int = 32): ByteArray? {
    var at = 0
    fun readLength(): Int {
        if (at >= der.size) return -1
        val first = der[at++].toInt() and 0xFF
        if (first < 0x80) return first
        val count = first and 0x7F
        if (count == 0 || count > 2 || at + count > der.size) return -1
        var value = 0
        repeat(count) { value = (value shl 8) or (der[at++].toInt() and 0xFF) }
        return value
    }

    fun readInteger(out: ByteArray, outOffset: Int): Boolean {
        if (at >= der.size || der[at++] != 2.toByte()) return false
        val length = readLength()
        if (length <= 0 || at + length > der.size) return false
        var start = at
        var size = length
        while (size > 1 && der[start] == 0.toByte()) {
            start++
            size--
        }
        if (size > partBytes) return false
        der.copyInto(out, outOffset + partBytes - size, start, start + size)
        at += length
        return true
    }

    if (der.isEmpty() || der[at++] != 0x30.toByte()) return null
    val total = readLength()
    if (total < 0 || at + total != der.size) return null
    val raw = ByteArray(partBytes * 2)
    if (!readInteger(raw, 0) || !readInteger(raw, partBytes) || at != der.size) return null
    return raw
}

private fun eccBlob(magic: UInt, point: ByteArray): ByteArray {
    val blob = ByteArray(8 + point.size)
    for (i in 0 until 4) blob[i] = ((magic shr (8 * i)) and 0xFFu).toByte()
    blob[4] = 32
    point.copyInto(blob, 8)
    return blob
}

private fun importPublic(provider: COpaquePointer, magic: UInt, point: ByteArray): COpaquePointer? = memScoped {
    val blob = eccBlob(magic, point)
    val handle = alloc<COpaquePointerVar>()
    val status = blob.usePinned {
        BCryptImportKeyPair(provider, null, ECC_BLOB, handle.ptr, it.addressOf(0).reinterpret(), blob.size.toUInt(), 0u)
    }
    if (status != 0) null else handle.value
}

fun winEcdsaP256Verify(publicSpki: ByteArray, sha256: ByteArray, signatureDer: ByteArray): Boolean {
    val point = winP256PointFromSpki(publicSpki) ?: return false
    val raw = winEcdsaDerToRaw(signatureDer) ?: return false
    if (sha256.size != 32) return false
    val key = importPublic(ecdsaProvider, ECDSA_PUBLIC_P256_MAGIC, point) ?: return false
    try {
        val status = sha256.usePinned { hash ->
            raw.usePinned { signature ->
                BCryptVerifySignature(
                    key, null, hash.addressOf(0).reinterpret(), 32u,
                    signature.addressOf(0).reinterpret(), raw.size.toUInt(), 0u
                )
            }
        }
        if (status != 0 && status != STATUS_INVALID_SIGNATURE) return false
        return status == 0
    } finally {
        BCryptDestroyKey(key)
    }
}

class WinEcdhP256 {
    private var key: COpaquePointer? = memScoped {
        val handle = alloc<COpaquePointerVar>()
        ok(BCryptGenerateKeyPair(ecdhProvider, handle.ptr, 256u, 0u), "BCryptGenerateKeyPair")
        ok(BCryptFinalizeKeyPair(handle.value, 0u), "BCryptFinalizeKeyPair")
        handle.value
    }

    val publicSpki: ByteArray = memScoped {
        val blob = ByteArray(8 + 64)
        val written = alloc<UIntVar>()
        blob.usePinned {
            ok(
                BCryptExportKey(key, null, ECC_BLOB, it.addressOf(0).reinterpret(), blob.size.toUInt(), written.ptr, 0u),
                "BCryptExportKey"
            )
        }
        winP256SpkiFromPoint(blob.copyOfRange(8, 72))
    }

    fun deriveSha256(peerSpki: ByteArray): ByteArray {
        val point = winP256PointFromSpki(peerSpki) ?: throw IllegalArgumentException("not a P-256 public key")
        val peer = importPublic(ecdhProvider, ECDH_PUBLIC_P256_MAGIC, point)
            ?: throw IllegalArgumentException("the peer public key is not on the curve")
        try {
            return memScoped {
                val secret = alloc<COpaquePointerVar>()
                ok(BCryptSecretAgreement(key, peer, secret.ptr, 0u), "BCryptSecretAgreement")
                try {
                    val algorithm = "SHA256"
                    val buffer = alloc<BCryptBuffer>()
                    buffer.cbBuffer = ((algorithm.length + 1) * 2).toUInt()
                    buffer.BufferType = 0u
                    buffer.pvBuffer = algorithm.wcstr.ptr
                    val parameters = alloc<BCryptBufferDesc>()
                    parameters.ulVersion = 0u
                    parameters.cBuffers = 1u
                    parameters.pBuffers = buffer.ptr
                    val out = ByteArray(32)
                    val written = alloc<UIntVar>()
                    out.usePinned {
                        ok(
                            BCryptDeriveKey(
                                secret.value, "HASH", parameters.ptr, it.addressOf(0).reinterpret(), 32u, written.ptr, 0u
                            ),
                            "BCryptDeriveKey"
                        )
                    }
                    out
                } finally {
                    BCryptDestroySecret(secret.value)
                }
            }
        } finally {
            BCryptDestroyKey(peer)
        }
    }

    fun close() {
        key?.let { BCryptDestroyKey(it) }
        key = null
    }
}
