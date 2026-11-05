package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.core.security.KeyVaultTools.ACCOUNT
import com.michelle.raouf.fetch_jet.core.security.KeyVaultTools.SERVICE

/** The login Keychain, through `/usr/bin/security`. */
internal object MacKeyVault : KeyVault {
    private const val NOT_FOUND = 44

    override fun load(): ByteArray? {
        val r = KeyVaultTools.run(
            "/usr/bin/security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"
        )
        return when (r.exit) {
            0 -> KeyVaultTools.unhex(r.out)
            NOT_FOUND -> null
            else -> error("Keychain answered ${r.exit}")
        }
    }

    override fun store(key: ByteArray) {
        // `security -i` reads the command from stdin, so the key is not visible in `ps`.
        KeyVaultTools.run(
            "/usr/bin/security", "-i",
            input = "add-generic-password -U -s $SERVICE -a $ACCOUNT -w ${KeyVaultTools.hex(key)}\n"
        )
    }
}
