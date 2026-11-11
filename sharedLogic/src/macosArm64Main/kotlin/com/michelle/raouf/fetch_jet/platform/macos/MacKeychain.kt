package com.michelle.raouf.fetch_jet.platform.macos

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlinx.coroutines.runBlocking

object MacKeychain {
    private const val TOOL = "/usr/bin/security"
    private const val SERVICE = "FetchJet"
    private const val ACCOUNT = "native-secrets-wrap"
    private const val NOT_FOUND = 44
    private const val PLAIN: Byte = 0
    private const val SEALED: Byte = 1

    private val wrapKey: ByteArray? by lazy {
        try {
            load() ?: MacCrypto.randomBytes(32).let { key ->
                store(key)
                load()?.takeIf { it.contentEquals(key) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun protect(data: ByteArray): ByteArray {
        val key = wrapKey ?: return byteArrayOf(PLAIN) + data
        return byteArrayOf(SEALED) + MacCrypto.aesGcmSeal(key, data)
    }

    fun unprotect(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "empty secret" }
        val body = data.copyOfRange(1, data.size)
        return when (data[0]) {
            PLAIN -> body
            SEALED -> MacCrypto.aesGcmOpen(wrapKey ?: error("Keychain key is not available"), body)
            else -> error("unknown secret format")
        }
    }

    private fun load(): ByteArray? {
        val result = runBlocking {
            DesktopOs.runProcess(listOf(TOOL, "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"), timeoutMs = 15_000)
        }
        if (result.timedOut) error("Keychain timed out")
        return when (result.exitCode) {
            0 -> unhex(result.stdout.trim())
            NOT_FOUND -> null
            else -> error("Keychain answered ${result.exitCode}")
        }
    }

    private fun store(key: ByteArray) {
        val line = "add-generic-password -U -s $SERVICE -a $ACCOUNT -w ${hex(key)}\n"
        val result = runBlocking {
            DesktopOs.runProcess(listOf(TOOL, "-i"), stdinBytes = line.encodeToByteArray(), timeoutMs = 15_000)
        }
        if (result.timedOut) error("Keychain write timed out")
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun unhex(text: String): ByteArray? {
        if (text.length != 64) return null
        return ByteArray(32) { text.substring(it * 2, it * 2 + 2).toIntOrNull(16)?.toByte() ?: return null }
    }
}
