package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winDpapiProtect
import com.michelle.raouf.fetch_jet.platform.win.winDpapiUnprotect
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winMove
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object WindowsKeyVault {
    private val file: String get() = WinPaths.settings() + "\\secrets.key"

    fun load(): ByteArray? {
        if (!winExists(file)) return null
        val text = winReadText(file)?.trim() ?: error("secrets.key cannot be read")
        return winDpapiUnprotect(Base64.decode(text))
    }

    fun store(key: ByteArray) {
        val temp = "$file.tmp"
        winDeleteFile(temp)
        check(winWriteText(temp, Base64.encode(winDpapiProtect(key)))) { "secrets.key cannot be written" }
        if (!winMove(temp, file, replace = true)) {
            winDeleteFile(file)
            check(winMove(temp, file, replace = true)) { "secrets.key cannot be saved" }
        }
    }
}

internal actual object SecretBox {
    private val key: ByteArray by lazy {
        WindowsKeyVault.load() ?: winRandomBytes(32).also {
            WindowsKeyVault.store(it)
            check(WindowsKeyVault.load()?.contentEquals(it) == true) { "key was not saved" }
        }
    }

    actual fun seal(plain: ByteArray): ByteArray = winAesGcmSeal(key, plain)

    actual fun open(sealed: ByteArray): ByteArray = winAesGcmOpen(key, sealed)

    actual fun randomBytes(n: Int): ByteArray = winRandomBytes(n)
}
