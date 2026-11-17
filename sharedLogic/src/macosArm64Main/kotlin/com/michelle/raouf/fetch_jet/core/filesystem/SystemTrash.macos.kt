@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

internal actual fun osMoveToTrash(path: String): Boolean =
    NSFileManager.defaultManager.trashItemAtURL(NSURL.fileURLWithPath(path), null, null)
