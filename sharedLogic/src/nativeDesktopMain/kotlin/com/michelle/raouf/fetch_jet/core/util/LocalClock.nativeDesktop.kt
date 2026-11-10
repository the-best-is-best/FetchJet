package com.michelle.raouf.fetch_jet.core.util

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

actual fun localUtcOffsetMs(): Long = DesktopOs.localUtcOffsetMs()
