package com.michelle.raouf.fetch_jet.core.security

/**
 * iOS placeholder. The real AES-256-GCM is added in the iOS phase of ROADMAP-1.3.0 item 4
 * (CryptoKit `AES.GCM` through a Swift bridge, same layout: 12-byte nonce ++ ciphertext ++ tag).
 * Until then the device-sync feature is not wired on iOS, so this is never reached at runtime.
 */
internal actual object CryptoBox {
    actual fun seal(key: ByteArray, plain: ByteArray): ByteArray =
        throw NotImplementedError("CryptoBox is not yet implemented on iOS (device sync, item 4)")

    actual fun open(key: ByteArray, sealed: ByteArray): ByteArray =
        throw NotImplementedError("CryptoBox is not yet implemented on iOS (device sync, item 4)")
}
