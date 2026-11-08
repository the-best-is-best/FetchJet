package com.michelle.raouf.fetch_jet.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual object CryptoBox {
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    actual fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return nonce + cipher.doFinal(plain)
    }

    actual fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_LEN) { "sealed message too short" }
        val nonce = sealed.copyOfRange(0, NONCE_LEN)
        val body = sealed.copyOfRange(NONCE_LEN, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return cipher.doFinal(body)
    }
}
