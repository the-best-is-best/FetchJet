package com.michelle.raouf.fetch_jet.core.security

import java.io.File
import java.util.Base64

/** The key protected by DPAPI for the current Windows user, in `.fetch_jet/secrets.key`. */
internal object WindowsKeyVault : KeyVault {
    private val file get() = File(KeyVaultTools.settingsDir, "secrets.key")

    override fun load(): ByteArray? {
        if (!file.exists()) return null
        return Base64.getDecoder().decode(dpapi("Unprotect", file.readText().trim()))
    }

    override fun store(key: ByteArray) {
        KeyVaultTools.writePrivate(file, dpapi("Protect", Base64.getEncoder().encodeToString(key)))
    }

    private fun dpapi(method: String, base64: String): String {
        val script = "Add-Type -AssemblyName System.Security; " +
                "\$b = [Convert]::FromBase64String([Console]::In.ReadLine()); " +
                "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::$method(" +
                "\$b, \$null, [Security.Cryptography.DataProtectionScope]::CurrentUser))"
        val r = KeyVaultTools.run(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script,
            input = base64 + "\n"
        )
        check(r.exit == 0 && r.out.isNotEmpty()) { "DPAPI $method failed (${r.exit})" }
        return r.out.lines().last().trim()
    }
}
