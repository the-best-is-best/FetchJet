package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlinx.coroutines.runBlocking

internal actual object SharedSecretKey {
    private const val TOOL = "/usr/bin/security"
    private const val SERVICE = "FetchJet"
    private const val ACCOUNT = "secrets-key"

    actual fun load(): ByteArray? {
        val result = runBlocking {
            DesktopOs.runProcess(listOf(TOOL, "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"), timeoutMs = 15_000)
        }
        if (result.timedOut || result.exitCode != 0) return null
        return parseHexKey(result.stdout)
    }

    fun parseHexKey(text: String): ByteArray? {
        val hex = text.trim()
        if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte() }
    }
}
