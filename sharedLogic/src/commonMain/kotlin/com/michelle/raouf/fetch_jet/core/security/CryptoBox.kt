package com.michelle.raouf.fetch_jet.core.security

/**
 * Authenticated encryption with a *shared* key (AES-256-GCM), for data sent between two paired
 * devices (ROADMAP-1.3.0 item 4). Unlike [SecretBox] (a device-only key for data at rest), the
 * key here is the pairing secret both devices hold, so one device can open what the other sealed.
 *
 * Output layout: 12-byte random nonce ++ ciphertext ++ 16-byte GCM tag. The key must be 32 bytes.
 */
internal expect object CryptoBox {
    fun seal(key: ByteArray, plain: ByteArray): ByteArray

    /** Throws if the tag does not verify (wrong key, or the message was tampered with). */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray
}
