package com.michelle.raouf.fetch_jet.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** The secrets key when the Keychain is not available: Library/Application Support, no backup. */
@OptIn(ExperimentalForeignApi::class)
internal object KeyFile {
    private val path: String
        get() {
            val dir = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory, NSUserDomainMask, true
            ).first() as String
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            return "$dir/fetchjet-secrets.key"
        }

    fun load(): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        if (data.length.toInt() != 64) return null
        val out = ByteArray(64)
        out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return out
    }

    fun store(key: ByteArray) {
        val file = path
        val data = key.usePinned { NSData.dataWithBytes(it.addressOf(0), key.size.convert()) }
        check(data.writeToFile(file, true)) { "key file not written" }
        NSFileManager.defaultManager.setAttributes(
            mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
            file, null
        )
        NSURL.fileURLWithPath(file)
            .setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
    }
}
