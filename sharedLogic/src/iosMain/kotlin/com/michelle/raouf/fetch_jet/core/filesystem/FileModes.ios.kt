package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.chmod

@OptIn(ExperimentalForeignApi::class)
actual fun markExecutable(path: String) {
    chmod(path, 0x1EDu)
}
