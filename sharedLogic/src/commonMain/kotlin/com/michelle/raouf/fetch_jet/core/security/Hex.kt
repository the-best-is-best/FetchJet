package com.michelle.raouf.fetch_jet.core.security

internal fun hexOf(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
