package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encrypts small secrets (proxy / site passwords, a task's Cookie header) before they are
 * written to the settings folder. The key never leaves the device: Android Keystore, iOS
 * Keychain, macOS Keychain, Windows DPAPI, Linux Secret Service (or a 600 key file).
 * A copied settings file therefore does not reveal them, and does not open on another device.
 */
object Secrets {
    private const val PREFIX = "fjenc1:"

    fun isProtected(stored: String): Boolean = stored.startsWith(PREFIX)

    /** A random secret as hex ([bytes] long before encoding). */
    fun randomHex(bytes: Int = 32): String =
        SecretBox.randomBytes(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** The text to store. Falls back to [plain] when the platform key is not available. */
    @OptIn(ExperimentalEncodingApi::class)
    fun protect(plain: String): String {
        if (plain.isEmpty() || isProtected(plain)) return plain
        return try {
            PREFIX + Base64.encode(SecretBox.seal(plain.encodeToByteArray()))
        } catch (t: Throwable) {
            AppLog.e("Secrets: could not encrypt (${t.message}), stored as is")
            plain
        }
    }

    /** The plain text, or null when [stored] is encrypted and cannot be opened here. */
    @OptIn(ExperimentalEncodingApi::class)
    fun reveal(stored: String): String? {
        if (!isProtected(stored)) return stored
        return try {
            SecretBox.open(Base64.decode(stored.removePrefix(PREFIX))).decodeToString()
        } catch (t: Throwable) {
            AppLog.e("Secrets: could not decrypt (${t.message})")
            null
        }
    }
}

/** Platform part: encryption with a key kept by the OS. */
internal expect object SecretBox {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray

    /** Bytes from the OS's secure random source. */
    fun randomBytes(n: Int): ByteArray
}
