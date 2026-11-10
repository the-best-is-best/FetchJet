package com.michelle.raouf.fetch_jet.core.util

import com.michelle.raouf.fetch_jet.platform.win.winLocalUtcOffsetMs

actual fun localUtcOffsetMs(): Long = winLocalUtcOffsetMs()
