package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal actual fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean {
    val signature = try {
        Base64.getDecoder().decode(signatureBase64.trim())
    } catch (_: IllegalArgumentException) {
        return false
    }
    return UPDATE_PUBLIC_KEYS.any { key ->
        try {
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            File(path).inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    verifier.update(buffer, 0, n)
                }
            }
            verifier.verify(signature)
        } catch (_: Exception) {
            false
        }
    }
}
