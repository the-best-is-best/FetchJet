package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal actual fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean {
    val signature = try {
        Base64.decode(signatureBase64.trim())
    } catch (_: Throwable) {
        return false
    }
    val hash = try {
        DesktopOs.sha256OfFile(path)
    } catch (_: Throwable) {
        null
    } ?: return false
    return UPDATE_PUBLIC_KEYS.any { key ->
        try {
            DesktopOs.ecdsaP256Verify(Base64.decode(key), hash, signature)
        } catch (_: Throwable) {
            false
        }
    }
}
