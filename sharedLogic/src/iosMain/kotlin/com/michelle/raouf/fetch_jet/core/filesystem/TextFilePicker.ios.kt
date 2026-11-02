package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual object TextFilePicker {
    actual val isSupported: Boolean get() = true

    private var delegate: PickerDelegate? = null

    actual fun pick(onPicked: (String?) -> Unit) {
        val host = topViewController()
        if (host == null) {
            onPicked(null)
            return
        }
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePlainText),
            asCopy = true
        )
        val d = PickerDelegate { text ->
            delegate = null
            onPicked(text)
        }
        delegate = d
        picker.delegate = d
        picker.allowsMultipleSelection = false
        host.presentViewController(picker, animated = true, completion = null)
    }

    private fun readText(url: NSURL): String? {
        val secured = url.startAccessingSecurityScopedResource()
        return try {
            NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
        } catch (_: Throwable) {
            null
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
            onDone(url?.let { readText(it) })
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onDone(null)
        }
    }
}
