package com.michelle.raouf.fetch_jet.core.util

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun localUtcOffsetMs(): Long =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()).toLong() * 1000L
