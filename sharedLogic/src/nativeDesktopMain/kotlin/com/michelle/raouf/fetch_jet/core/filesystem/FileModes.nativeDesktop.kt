package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

actual fun markExecutable(path: String) {
    DesktopOs.makeExecutable(path)
}
