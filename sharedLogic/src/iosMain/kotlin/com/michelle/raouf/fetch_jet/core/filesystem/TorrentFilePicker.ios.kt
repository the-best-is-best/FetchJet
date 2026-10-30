package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual object TorrentFilePicker {
    actual val isSupported: Boolean get() = true

    // Kept alive while the picker is on screen (the controller holds its delegate weakly).
    private var delegate: PickerDelegate? = null

    actual fun pick(onPicked: (String?) -> Unit) {
        val host = topViewController()
        if (host == null) {
            onPicked(null)
            return
        }
        val type = UTType.typeWithFilenameExtension("torrent") ?: UTTypeData
        val picker =
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(type), asCopy = true)
        val d = PickerDelegate { path ->
            delegate = null
            onPicked(path)
        }
        delegate = d
        picker.delegate = d
        picker.allowsMultipleSelection = false
        host.presentViewController(picker, animated = true, completion = null)
    }

    /** Copies a picked / "Open in FetchJet" document into Documents/opened-torrents. */
    fun importTorrent(url: NSURL): String? {
        val secured = url.startAccessingSecurityScopedResource()
        try {
            val fm = NSFileManager.defaultManager
            val docs = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .firstOrNull() as? NSURL ?: return null
            val dir = docs.URLByAppendingPathComponent("opened-torrents") ?: return null
            fm.createDirectoryAtURL(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            val rawName = url.lastPathComponent ?: "shared.torrent"
            val name = if (rawName.lowercase().endsWith(".torrent")) rawName else "$rawName.torrent"
            var dest = dir.URLByAppendingPathComponent(name) ?: return null
            if (dest.path?.let { fm.fileExistsAtPath(it) } == true) {
                val stamp = NSDate().timeIntervalSince1970.toLong()
                dest =
                    dir.URLByAppendingPathComponent(name.removeSuffix(".torrent") + "-$stamp.torrent")
                        ?: return null
            }
            val ok = fm.copyItemAtURL(url, toURL = dest, error = null)
            return if (ok) dest.path else null
        } catch (_: Throwable) {
            return null
        } finally {
            if (secured) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun topViewController(): UIViewController? {
        val app = UIApplication.sharedApplication
        val window = app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
        var top = window?.rootViewController ?: return null
        while (true) {
            top = top.presentedViewController ?: break
        }
        return top
    }

    private class PickerDelegate(val onDone: (String?) -> Unit) : NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            onDone(url?.let { importTorrent(it) })
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onDone(null)
        }
    }
}
