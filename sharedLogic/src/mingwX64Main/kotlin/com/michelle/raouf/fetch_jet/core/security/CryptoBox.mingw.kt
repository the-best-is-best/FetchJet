package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal

internal actual object CryptoBox {
    actual fun seal(key: ByteArray, plain: ByteArray): ByteArray = winAesGcmSeal(key, plain)

    actual fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size > 12) { "sealed message too short" }
        return winAesGcmOpen(key, sealed)
    }
}
