package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

actual fun platformUserIdleMs(): Long? = DesktopOs.userIdleMs()
