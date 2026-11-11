@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreServices.LSCopyDefaultHandlerForURLScheme
import platform.CoreServices.LSCopyDefaultRoleHandlerForContentType
import platform.CoreServices.LSSetDefaultHandlerForURLScheme
import platform.CoreServices.LSSetDefaultRoleHandlerForContentType
import platform.CoreServices.UTTypeCreatePreferredIdentifierForTag
import platform.CoreServices.kLSRolesAll
import platform.CoreServices.kUTTagClassFilenameExtension
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSString

/** macOS: LaunchServices. The user's tap is the consent; macOS asks nothing more for these types. */
actual object DefaultTorrentHandler {
    actual val supported: Boolean get() = DesktopApp.launcherPath != null && ownBundleId() != null

    actual val opensSystemPage: Boolean = false

    private fun ownBundleId(): String? = NSBundle.mainBundle.bundleIdentifier?.takeIf { it.isNotBlank() }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun cf(text: String): CFStringRef? = CFBridgingRetain(text as NSString)?.reinterpret()

    private fun take(ref: CFStringRef?): String? = ref?.let { CFBridgingRelease(it) as? String }

    private fun <T> withTorrentType(block: (CFStringRef?) -> T): T {
        val ext = cf("torrent")
        val type = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, ext, null)
        try {
            return block(type)
        } finally {
            ext?.let { CFRelease(it) }
            type?.let { CFRelease(it) }
        }
    }

    private fun currentHandlers(): Pair<String?, String?> {
        val scheme = cf(MAGNET_SCHEME)
        val magnet = try {
            take(LSCopyDefaultHandlerForURLScheme(scheme))
        } finally {
            scheme?.let { CFRelease(it) }
        }
        val torrent = withTorrentType { type -> take(LSCopyDefaultRoleHandlerForContentType(type, kLSRolesAll)) }
        return magnet to torrent
    }

    actual suspend fun isDefault(): Boolean? = withContext(Dispatchers.IO) {
        val own = ownBundleId() ?: return@withContext null
        val (magnet, torrent) = currentHandlers()
        magnet.equals(own, ignoreCase = true) && torrent.equals(own, ignoreCase = true)
    }

    actual suspend fun makeDefault(): Boolean = withContext(Dispatchers.IO) {
        val own = ownBundleId() ?: return@withContext false
        val id = cf(own)
        val scheme = cf(MAGNET_SCHEME)
        try {
            val a = LSSetDefaultHandlerForURLScheme(scheme, id)
            val b = withTorrentType { type -> LSSetDefaultRoleHandlerForContentType(type, kLSRolesAll, id) }
            a == 0 && b == 0
        } finally {
            id?.let { CFRelease(it) }
            scheme?.let { CFRelease(it) }
        }
    }
}
