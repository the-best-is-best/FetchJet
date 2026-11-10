@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.RTLD_LAZY
import platform.posix.RTLD_LOCAL
import platform.posix.dlopen
import platform.posix.dlsym

class LinuxCryptoException(message: String) : Exception(message)

private typealias Fn0 = CFunction<() -> COpaquePointer?>
private typealias FnFree = CFunction<(COpaquePointer?) -> Unit>
private typealias FnInit = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, Int) -> Int>
private typealias FnUpdate = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, Int) -> Int>
private typealias FnFinal = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>
private typealias FnCtrl = CFunction<(COpaquePointer?, Int, Int, COpaquePointer?) -> Int>
private typealias FnD2i = CFunction<(COpaquePointer?, COpaquePointer?, Long) -> COpaquePointer?>
private typealias FnCtxNew = CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>
private typealias FnInt1 = CFunction<(COpaquePointer?) -> Int>
private typealias FnCtxNewId = CFunction<(Int, COpaquePointer?) -> COpaquePointer?>
private typealias FnCtxInt = CFunction<(COpaquePointer?, Int) -> Int>
private typealias FnKeygen = CFunction<(COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>
private typealias FnI2d = CFunction<(COpaquePointer?, CPointer<CPointerVar<ByteVar>>?) -> Int>
private typealias FnSetPeer = CFunction<(COpaquePointer?, COpaquePointer?) -> Int>
private typealias FnDerive = CFunction<(COpaquePointer?, COpaquePointer?, CPointer<ULongVar>?) -> Int>
private typealias FnVerify = CFunction<(COpaquePointer?, COpaquePointer?, ULong, COpaquePointer?, ULong) -> Int>

object LinuxCrypto {
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val CTRL_GCM_GET_TAG = 0x10
    private const val CTRL_GCM_SET_TAG = 0x11

    private val library: COpaquePointer? by lazy {
        listOf("libcrypto.so.3", "libcrypto.so")
            .firstNotNullOfOrNull { dlopen(it, RTLD_LAZY or RTLD_LOCAL) }
    }

    val isAvailable: Boolean get() = library != null

    private fun symbol(name: String): COpaquePointer =
        dlsym(library ?: throw LinuxCryptoException("libcrypto is not available"), name)
            ?: throw LinuxCryptoException("libcrypto has no $name")

    private val cipherCtxNew by lazy { symbol("EVP_CIPHER_CTX_new").reinterpret<Fn0>() }
    private val cipherCtxFree by lazy { symbol("EVP_CIPHER_CTX_free").reinterpret<FnFree>() }
    private val aes128Gcm by lazy { symbol("EVP_aes_128_gcm").reinterpret<Fn0>() }
    private val aes256Gcm by lazy { symbol("EVP_aes_256_gcm").reinterpret<Fn0>() }
    private val cipherInit by lazy { symbol("EVP_CipherInit_ex").reinterpret<FnInit>() }
    private val cipherUpdate by lazy { symbol("EVP_CipherUpdate").reinterpret<FnUpdate>() }
    private val cipherFinal by lazy { symbol("EVP_CipherFinal_ex").reinterpret<FnFinal>() }
    private val cipherCtrl by lazy { symbol("EVP_CIPHER_CTX_ctrl").reinterpret<FnCtrl>() }
    private val d2iPubkey by lazy { symbol("d2i_PUBKEY").reinterpret<FnD2i>() }
    private val pkeyFree by lazy { symbol("EVP_PKEY_free").reinterpret<FnFree>() }
    private val pkeyCtxNew by lazy { symbol("EVP_PKEY_CTX_new").reinterpret<FnCtxNew>() }
    private val pkeyCtxFree by lazy { symbol("EVP_PKEY_CTX_free").reinterpret<FnFree>() }
    private val verifyInit by lazy { symbol("EVP_PKEY_verify_init").reinterpret<FnInt1>() }
    private val verify by lazy { symbol("EVP_PKEY_verify").reinterpret<FnVerify>() }
    private val pkeyCtxNewId by lazy { symbol("EVP_PKEY_CTX_new_id").reinterpret<FnCtxNewId>() }
    private val keygenInit by lazy { symbol("EVP_PKEY_keygen_init").reinterpret<FnInt1>() }
    private val setCurve by lazy { symbol("EVP_PKEY_CTX_set_ec_paramgen_curve_nid").reinterpret<FnCtxInt>() }
    private val keygen by lazy { symbol("EVP_PKEY_keygen").reinterpret<FnKeygen>() }
    private val i2dPubkey by lazy { symbol("i2d_PUBKEY").reinterpret<FnI2d>() }
    private val deriveInit by lazy { symbol("EVP_PKEY_derive_init").reinterpret<FnInt1>() }
    private val deriveSetPeer by lazy { symbol("EVP_PKEY_derive_set_peer").reinterpret<FnSetPeer>() }
    private val derive by lazy { symbol("EVP_PKEY_derive").reinterpret<FnDerive>() }

    private const val KEY_TYPE_EC = 408
    private const val CURVE_P256 = 415

    class EcdhKey internal constructor(private var key: COpaquePointer?) {
        val publicSpki: ByteArray = memScoped {
            val size = i2dPubkey(key, null)
            if (size <= 0) throw LinuxCryptoException("i2d_PUBKEY failed")
            val out = ByteArray(size)
            out.usePinned { pinned ->
                val cursor = alloc<CPointerVar<ByteVar>>()
                cursor.value = pinned.addressOf(0)
                if (i2dPubkey(key, cursor.ptr) != size) throw LinuxCryptoException("i2d_PUBKEY failed")
            }
            out
        }

        fun sharedSecret(peerSpki: ByteArray): ByteArray {
            val peer = memScoped {
                peerSpki.usePinned { pinned ->
                    val cursor = alloc<CPointerVar<ByteVar>>()
                    cursor.value = pinned.addressOf(0)
                    d2iPubkey(null, cursor.ptr, peerSpki.size.toLong())
                }
            } ?: throw LinuxCryptoException("not a valid public key")
            try {
                val ctx = pkeyCtxNew(key, null) ?: throw LinuxCryptoException("EVP_PKEY_CTX_new failed")
                try {
                    if (deriveInit(ctx) != 1) throw LinuxCryptoException("EVP_PKEY_derive_init failed")
                    if (deriveSetPeer(ctx, peer) != 1) throw LinuxCryptoException("the peer public key is not on the curve")
                    return memScoped {
                        val length = alloc<ULongVar>()
                        if (derive(ctx, null, length.ptr) != 1) throw LinuxCryptoException("EVP_PKEY_derive failed")
                        val out = ByteArray(length.value.toInt())
                        out.usePinned { if (derive(ctx, it.addressOf(0), length.ptr) != 1) throw LinuxCryptoException("EVP_PKEY_derive failed") }
                        out.copyOf(length.value.toInt())
                    }
                } finally {
                    pkeyCtxFree(ctx)
                }
            } finally {
                pkeyFree(peer)
            }
        }

        fun close() {
            key?.let { pkeyFree(it) }
            key = null
        }
    }

    fun newEcdhP256(): EcdhKey {
        val ctx = pkeyCtxNewId(KEY_TYPE_EC, null) ?: throw LinuxCryptoException("EVP_PKEY_CTX_new_id failed")
        try {
            if (keygenInit(ctx) != 1) throw LinuxCryptoException("EVP_PKEY_keygen_init failed")
            if (setCurve(ctx, CURVE_P256) != 1) throw LinuxCryptoException("P-256 is not available")
            val key = memScoped {
                val out = alloc<COpaquePointerVar>()
                if (keygen(ctx, out.ptr) != 1) throw LinuxCryptoException("EVP_PKEY_keygen failed")
                out.value
            }
            return EcdhKey(key)
        } finally {
            pkeyCtxFree(ctx)
        }
    }

    fun aesGcmSeal(key: ByteArray, plain: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        val tag = ByteArray(TAG_BYTES)
        val cipher = gcm(key, nonce, plain, tag, encrypt = true)
        return nonce + cipher + tag
    }

    fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size >= NONCE_BYTES + TAG_BYTES) { "sealed message too short" }
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val cipher = sealed.copyOfRange(NONCE_BYTES, sealed.size - TAG_BYTES)
        val tag = sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size)
        return gcm(key, nonce, cipher, tag, encrypt = false)
    }

    private fun gcm(key: ByteArray, nonce: ByteArray, input: ByteArray, tag: ByteArray, encrypt: Boolean): ByteArray {
        val type = when (key.size) {
            16 -> aes128Gcm()
            32 -> aes256Gcm()
            else -> throw LinuxCryptoException("AES key must be 16 or 32 bytes")
        }
        val ctx = cipherCtxNew() ?: throw LinuxCryptoException("EVP_CIPHER_CTX_new failed")
        try {
            val output = ByteArray(input.size + 16)
            var total = 0
            memScoped {
                val written = alloc<IntVar>()
                key.usePinned { k ->
                    nonce.usePinned { n ->
                        if (cipherInit(ctx, type, null, k.addressOf(0), n.addressOf(0), if (encrypt) 1 else 0) != 1) {
                            throw LinuxCryptoException("EVP_CipherInit_ex failed")
                        }
                    }
                }
                if (input.isNotEmpty()) {
                    input.usePinned { inp ->
                        output.usePinned { out ->
                            if (cipherUpdate(ctx, out.addressOf(0), written.ptr, inp.addressOf(0), input.size) != 1) {
                                throw LinuxCryptoException("EVP_CipherUpdate failed")
                            }
                        }
                    }
                    total = written.value
                }
                if (!encrypt) {
                    tag.usePinned { t ->
                        if (cipherCtrl(ctx, CTRL_GCM_SET_TAG, TAG_BYTES, t.addressOf(0)) != 1) {
                            throw LinuxCryptoException("GCM tag cannot be set")
                        }
                    }
                }
                val finalOk = output.usePinned { out -> cipherFinal(ctx, out.addressOf(total), written.ptr) }
                if (finalOk != 1) throw LinuxCryptoException(if (encrypt) "EVP_CipherFinal_ex failed" else "authentication failed")
                total += written.value
                if (encrypt) {
                    tag.usePinned { t ->
                        if (cipherCtrl(ctx, CTRL_GCM_GET_TAG, TAG_BYTES, t.addressOf(0)) != 1) {
                            throw LinuxCryptoException("GCM tag cannot be read")
                        }
                    }
                }
            }
            return output.copyOf(total)
        } finally {
            cipherCtxFree(ctx)
        }
    }

    fun ecdsaVerify(publicSpki: ByteArray, digest: ByteArray, signatureDer: ByteArray): Boolean {
        if (publicSpki.isEmpty() || signatureDer.isEmpty()) return false
        val key = memScoped {
            publicSpki.usePinned { pinned ->
                val cursor = alloc<CPointerVar<ByteVar>>()
                cursor.value = pinned.addressOf(0)
                d2iPubkey(null, cursor.ptr, publicSpki.size.toLong())
            }
        } ?: return false
        try {
            val ctx = pkeyCtxNew(key, null) ?: return false
            try {
                if (verifyInit(ctx) != 1) return false
                val result = signatureDer.usePinned { sig ->
                    digest.usePinned { hash ->
                        verify(ctx, sig.addressOf(0), signatureDer.size.toULong(), hash.addressOf(0), digest.size.toULong())
                    }
                }
                return result == 1
            } finally {
                pkeyCtxFree(ctx)
            }
        } finally {
            pkeyFree(key)
        }
    }
}

internal fun CPointer<ByteVar>.asOpaque(): COpaquePointer = this.reinterpret()
