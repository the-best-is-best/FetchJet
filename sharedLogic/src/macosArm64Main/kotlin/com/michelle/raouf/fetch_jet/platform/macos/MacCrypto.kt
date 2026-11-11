@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.macos

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureDigestX962SHA256
import platform.Security.kSecRandomDefault

class MacCryptoException(message: String) : Exception(message)

private typealias FnGcmSeal = CFunction<
    (UInt, COpaquePointer?, ULong, COpaquePointer?, ULong, COpaquePointer?, ULong, COpaquePointer?, ULong, COpaquePointer?, COpaquePointer?, ULong) -> Int
>
private typealias FnGcmOpen = FnGcmSeal

object MacCrypto {
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val ALGORITHM_AES = 0u

    private val gcmSeal by lazy { macSymbol<FnGcmSeal>("CCCryptorGCMOneshotEncrypt") }
    private val gcmOpen by lazy { macSymbol<FnGcmOpen>("CCCryptorGCMOneshotDecrypt") }

    fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        if (count == 0) return out
        val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) }
        if (status != 0) throw MacCryptoException("SecRandomCopyBytes $status")
        return out
    }

    fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray {
        val seal = gcmSeal ?: throw MacCryptoException("AES-GCM is not available")
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = ByteArray(maxOf(plain.size, 1))
        val tag = ByteArray(TAG_BYTES)
        val input = if (plain.isEmpty()) ByteArray(1) else plain
        val status = key.usePinned { k ->
            nonce.usePinned { n ->
                input.usePinned { i ->
                    cipher.usePinned { c ->
                        tag.usePinned { t ->
                            seal(
                                ALGORITHM_AES, k.addressOf(0), key.size.convert(), n.addressOf(0), NONCE_BYTES.convert(),
                                null, 0u, i.addressOf(0), plain.size.convert(), c.addressOf(0), t.addressOf(0), TAG_BYTES.convert()
                            )
                        }
                    }
                }
            }
        }
        if (status != 0) throw MacCryptoException("AES-GCM seal failed ($status)")
        return nonce + cipher.copyOf(plain.size) + tag
    }

    fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray {
        val open = gcmOpen ?: throw MacCryptoException("AES-GCM is not available")
        if (sealed.size < NONCE_BYTES + TAG_BYTES) throw MacCryptoException("sealed data is too short")
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val body = sealed.copyOfRange(NONCE_BYTES, sealed.size - TAG_BYTES)
        val tag = sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size)
        val input = if (body.isEmpty()) ByteArray(1) else body
        val plain = ByteArray(maxOf(body.size, 1))
        val status = key.usePinned { k ->
            nonce.usePinned { n ->
                input.usePinned { i ->
                    plain.usePinned { p ->
                        tag.usePinned { t ->
                            open(
                                ALGORITHM_AES, k.addressOf(0), key.size.convert(), n.addressOf(0), NONCE_BYTES.convert(),
                                null, 0u, i.addressOf(0), body.size.convert(), p.addressOf(0), t.addressOf(0), TAG_BYTES.convert()
                            )
                        }
                    }
                }
            }
        }
        if (status != 0) throw MacCryptoException("AES-GCM open failed ($status)")
        return plain.copyOf(body.size)
    }

    fun ecdsaVerify(publicSpki: ByteArray, sha256: ByteArray, signatureDer: ByteArray): Boolean {
        if (publicSpki.size < 65) return false
        val point = publicSpki.copyOfRange(publicSpki.size - 65, publicSpki.size)
        if (point[0] != 0x04.toByte()) return false
        val attributes = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            ?: return false
        CFDictionaryAddValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        val keyData = cfData(point)
        val digest = cfData(sha256)
        val signature = cfData(signatureDer)
        try {
            return memScoped {
                val error = alloc<CFErrorRefVar>()
                val key = SecKeyCreateWithData(keyData, attributes, error.ptr)
                error.value?.let { CFRelease(it) }
                if (key == null) return@memScoped false
                try {
                    val verifyError = alloc<CFErrorRefVar>()
                    val ok = SecKeyVerifySignature(key, kSecKeyAlgorithmECDSASignatureDigestX962SHA256, digest, signature, verifyError.ptr)
                    verifyError.value?.let { CFRelease(it) }
                    ok
                } finally {
                    CFRelease(key)
                }
            }
        } finally {
            CFRelease(keyData)
            CFRelease(digest)
            CFRelease(signature)
            CFRelease(attributes)
        }
    }

    private fun cfData(bytes: ByteArray): CFDataRef {
        val source = if (bytes.isEmpty()) ByteArray(1) else bytes
        return source.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) }
            ?: throw MacCryptoException("out of memory")
    }
}
