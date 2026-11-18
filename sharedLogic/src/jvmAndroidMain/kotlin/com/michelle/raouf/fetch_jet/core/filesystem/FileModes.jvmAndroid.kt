package com.michelle.raouf.fetch_jet.core.filesystem

import java.io.File

actual fun markExecutable(path: String) {
    File(path).setExecutable(true, false)
}
