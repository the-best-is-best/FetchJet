package com.michelle.raouf.fetch_jet.core.startup

/** Phones do not start apps at login; the setting is hidden there. */
actual object StartupLauncher {
    actual val isSupported: Boolean = false
    actual suspend fun isEnabled(): Boolean = false
    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("not available on this platform"))
}
