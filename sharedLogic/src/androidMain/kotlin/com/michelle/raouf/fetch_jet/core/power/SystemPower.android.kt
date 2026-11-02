package com.michelle.raouf.fetch_jet.core.power

actual object SystemPower {
    actual val isSupported: Boolean = false
    actual fun sleep(): Result<Unit> =
        Result.failure(UnsupportedOperationException("not available on this platform"))

    actual fun shutDown(): Result<Unit> =
        Result.failure(UnsupportedOperationException("not available on this platform"))
}
