package com.michelle.raouf.fetch_jet.core.util

actual fun localUtcOffsetMs(): Long =
    java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
