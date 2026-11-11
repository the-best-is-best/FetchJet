package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.join
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object DesktopKeyVault {
    private val file: String get() = DesktopOs.join(DesktopPaths.settings(), "secrets.key")

    fun exists(): Boolean = DesktopOs.exists(file)

    fun load(): ByteArray? {
        if (!DesktopOs.exists(file)) return null
        val text = DesktopOs.readText(file)?.trim() ?: error("secrets.key cannot be read")
        return DesktopOs.unprotectSecret(Base64.decode(text))
    }

    fun store(key: ByteArray) {
        val temp = "$file.tmp"
        DesktopOs.deleteFile(temp)
        check(DesktopOs.writeText(temp, "")) { "secrets.key cannot be written" }
        DesktopOs.restrictToOwner(temp)
        check(DesktopOs.writeText(temp, Base64.encode(DesktopOs.protectSecret(key)))) { "secrets.key cannot be written" }
        if (!DesktopOs.move(temp, file, replace = true)) {
            DesktopOs.deleteFile(file)
            check(DesktopOs.move(temp, file, replace = true)) { "secrets.key cannot be saved" }
        }
    }
}

internal expect object SharedSecretKey {
    fun load(): ByteArray?
}

internal actual object SecretBox {
    private val sharedKey: ByteArray? by lazy {
        try {
            SharedSecretKey.load()?.takeIf { it.size == 32 }
        } catch (_: Throwable) {
            null
        }
    }

    private val key: ByteArray by lazy {
        DesktopKeyVault.load() ?: DesktopOs.randomBytes(32).also {
            DesktopKeyVault.store(it)
            check(DesktopKeyVault.load()?.contentEquals(it) == true) { "key was not saved" }
        }
    }

    actual fun seal(plain: ByteArray): ByteArray = DesktopOs.aesGcmSeal(sharedKey ?: key, plain)

    actual fun open(sealed: ByteArray): ByteArray {
        sharedKey?.let { shared ->
            try {
                return DesktopOs.aesGcmOpen(shared, sealed)
            } catch (_: Throwable) {
                if (!DesktopKeyVault.exists()) throw IllegalStateException("the secret was sealed with another key")
            }
        }
        return DesktopOs.aesGcmOpen(key, sealed)
    }

    actual fun randomBytes(n: Int): ByteArray = DesktopOs.randomBytes(n)
}
