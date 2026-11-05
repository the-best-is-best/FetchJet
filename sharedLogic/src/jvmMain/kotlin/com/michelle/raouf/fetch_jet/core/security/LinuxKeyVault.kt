package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.security.KeyVaultTools.ACCOUNT
import com.michelle.raouf.fetch_jet.core.security.KeyVaultTools.SERVICE
import com.michelle.raouf.fetch_jet.core.update.DesktopInstall
import java.io.File

/**
 * The Secret Service keyring (GNOME Keyring / KWallet) through `secret-tool`; without it, a
 * key file only this user can read. `secrets.where` remembers which one holds the key, so a
 * keyring that is locked for a moment does not lead to a new key (and lost passwords).
 */
internal object LinuxKeyVault : KeyVault {
    private val file get() = File(KeyVaultTools.settingsDir, "secrets.key")
    private val where get() = File(KeyVaultTools.settingsDir, "secrets.where")

    override fun load(): ByteArray? {
        if (file.exists()) return KeyVaultTools.unhex(file.readText())
        if (!where.exists()) return null
        val r = KeyVaultTools.run("secret-tool", "lookup", "service", SERVICE, "account", ACCOUNT)
        check(r.exit == 0 && r.out.isNotEmpty()) { "keyring did not answer (${r.exit})" }
        return KeyVaultTools.unhex(r.out)
    }

    override fun store(key: ByteArray) {
        val hex = KeyVaultTools.hex(key)
        if (DesktopInstall.hasCommand("secret-tool")) {
            val saved = try {
                KeyVaultTools.run(
                    "secret-tool",
                    "store",
                    "--label=FetchJet",
                    "service",
                    SERVICE,
                    "account",
                    ACCOUNT,
                    input = hex
                ).exit == 0 &&
                        KeyVaultTools.run(
                            "secret-tool",
                            "lookup",
                            "service",
                            SERVICE,
                            "account",
                            ACCOUNT
                        ).out == hex
            } catch (_: Throwable) {
                false
            }
            if (saved) {
                where.writeText("secret-service\n"); return
            }
        }
        KeyVaultTools.writePrivate(file, hex)
    }
}
