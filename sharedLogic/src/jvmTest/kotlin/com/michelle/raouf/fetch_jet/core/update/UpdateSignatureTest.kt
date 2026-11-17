package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class UpdateSignatureTest {
    private val file = File.createTempFile("fetchjet-update", ".bin").apply { writeBytes(ByteArray(4096) { it.toByte() }) }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    private fun signWithStrangerKey(bytes: ByteArray): String {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val signer = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    @Test
    fun anEmptyOrBrokenSignatureIsRefused() {
        assertFalse(verifyUpdateSignature(file.path, ""))
        assertFalse(verifyUpdateSignature(file.path, "not base64 at all !!"))
        assertFalse(verifyUpdateSignature(file.path, Base64.getEncoder().encodeToString(ByteArray(72))))
    }

    @Test
    fun aSignatureFromAnotherKeyIsRefused() {
        assertFalse(verifyUpdateSignature(file.path, signWithStrangerKey(file.readBytes())))
    }

    @Test
    fun aMissingFileIsRefused() {
        assertFalse(verifyUpdateSignature(file.path + ".missing", signWithStrangerKey(ByteArray(1))))
    }
}
