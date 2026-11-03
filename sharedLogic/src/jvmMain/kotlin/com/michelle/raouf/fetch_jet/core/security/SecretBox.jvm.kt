package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.update.DesktopOs
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM; the key is kept by the OS ([KeyVault] of each desktop OS). */
internal actual object SecretBox {
    private const val IV_BYTES = 12

    private val vault: KeyVault = when (DesktopOs.current) {
        DesktopOs.MAC -> MacKeyVault
        DesktopOs.WINDOWS -> WindowsKeyVault
        DesktopOs.LINUX -> LinuxKeyVault
    }

    private val key: SecretKeySpec by lazy {
        val bytes = vault.load() ?: ByteArray(32).also {
            SecureRandom().nextBytes(it)
            vault.store(it)
            check(vault.load()?.contentEquals(it) == true) { "key was not saved" }
        }
        SecretKeySpec(bytes, "AES")
    }

    actual fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    actual fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

    actual fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, sealed.copyOfRange(0, IV_BYTES))
        )
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }
}
