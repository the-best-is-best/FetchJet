package com.michelle.raouf.fetch_jet.core.filesystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.URLPathAllowedCharacterSet
import platform.Foundation.create
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewControllerDelegateProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private var activePreviewController: QLPreviewController? = null
private var activeDataSource: QuickLookDataSource? = null
private var activeDelegate: QuickLookDelegate? = null

private class QuickLookPreviewItem(
    private val url: NSURL,
    private val titleString: String
) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL = url
    override fun previewItemTitle(): String = titleString
}

private class QuickLookDataSource(
    private val item: QLPreviewItemProtocol
) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long {
        return 1L
    }

    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: Long
    ): QLPreviewItemProtocol {
        return item
    }
}

private class QuickLookDelegate(
    private val onDismiss: () -> Unit
) : NSObject(), QLPreviewControllerDelegateProtocol {
    override fun previewControllerDidDismiss(controller: QLPreviewController) {
        onDismiss()
    }
}

private fun getTopViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    var keyWindow: UIWindow? = null
    for (scene in scenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        if (windowScene.activationState == UISceneActivationStateForegroundActive || keyWindow == null) {
            for (w in windowScene.windows) {
                val win = w as? UIWindow ?: continue
                if (win.isKeyWindow()) {
                    keyWindow = win
                    break
                }
            }
            if (keyWindow == null) {
                keyWindow = windowScene.windows.firstOrNull() as? UIWindow
            }
            if (keyWindow != null && windowScene.activationState == UISceneActivationStateForegroundActive) {
                break
            }
        }
    }

    var topController = keyWindow?.rootViewController
        ?: UIApplication.sharedApplication.keyWindow?.rootViewController
    while (topController?.presentedViewController != null) {
        val presented = topController.presentedViewController
        if (presented != null && !presented.isBeingDismissed()) {
            topController = presented
        } else {
            break
        }
    }
    return topController
}

@OptIn(ExperimentalForeignApi::class)
actual class PathResolver actual constructor() {
    actual fun getDownloadsDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        return (paths.firstOrNull() as? String) ?: ""
    }

    actual fun getDocumentsDirectory(): String {
        return getDownloadsDirectory()
    }

    actual fun getDesktopDirectory(): String {
        return getDownloadsDirectory()
    }

    actual fun getSettingsDirectory(): String {
        val docs = getDownloadsDirectory()
        val dir = "$docs/settings"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(dir)) {
            try {
                fileManager.createDirectoryAtPath(dir, true, null, null)
            } catch (_: Exception) {
            }
        }
        return dir
    }

    actual fun getTempDirectory(): String {
        val temp = NSTemporaryDirectory()
        val sub = "$temp/temp"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(sub)) {
            try {
                fileManager.createDirectoryAtPath(sub, true, null, null)
            } catch (_: Exception) {
            }
        }
        return sub
    }

    actual fun sanitizeFileName(fileName: String): String {
        val decoded = decodeUnicodeEscapes(fileName)
        val cleaned = decoded.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val ext = cleaned.substringAfterLast(".", "")
        val nameWithoutExt = if (ext.isNotEmpty()) cleaned.substringBeforeLast(".") else cleaned
        val truncatedName =
            if (nameWithoutExt.length > 80) nameWithoutExt.take(80).trimEnd() else nameWithoutExt
        return if (ext.isNotEmpty()) "$truncatedName.$ext" else truncatedName
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
        return regex.replace(input) { m ->
            try {
                val codePoint = m.groupValues[1].toInt(16)
                codePoint.toChar().toString()
            } catch (_: Exception) {
                m.value
            }
        }
    }

    actual fun chooseDirectory(initialPath: String?): String? {
        return null
    }

    actual fun isOpenFolderSupported(): Boolean = true

    /** Opens the Files app at [path] (must be inside this app's Documents / on-device storage). */
    actual fun openDirectoryInExplorer(path: String): Boolean = openInFilesApp(path)

    /** Opens the Files app at the folder that contains [filePath]. */
    actual fun openFileLocation(filePath: String): Boolean {
        val parent = filePath.trimEnd('/').substringBeforeLast('/', "")
        return openInFilesApp(parent.ifBlank { filePath })
    }

    /**
     * iOS has no public "reveal in Finder", but the Files app registers the
     * `shareddocuments://<absolute path>` scheme (needs UIFileSharingEnabled +
     * LSSupportsOpeningDocumentsInPlace in Info.plist, both set).
     */
    private fun openInFilesApp(path: String): Boolean {
        var clean = path.trim().removePrefix("file://")
        if (clean.isBlank() || !NSFileManager.defaultManager.fileExistsAtPath(clean)) return false
        // Files wants the real container path (/private/var/... not /var/...) and a trailing
        // slash for directories; otherwise it falls back to the "On My iPhone" root.
        NSURL.fileURLWithPath(clean).URLByResolvingSymlinksInPath?.path?.let { clean = it }
        if (!clean.endsWith("/")) clean += "/"
        val encoded =
            NSString.create(string = clean).stringByAddingPercentEncodingWithAllowedCharacters(
                NSCharacterSet.URLPathAllowedCharacterSet
            ) ?: return false
        val url = NSURL.URLWithString("shareddocuments://$encoded") ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return false
        dispatch_async(dispatch_get_main_queue()) {
            app.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
        return true
    }

    actual fun openFile(filePath: String): Boolean {
        return try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(filePath)) return false
            val fileUrl = NSURL.fileURLWithPath(filePath)

            dispatch_async(dispatch_get_main_queue()) {
                val topController = getTopViewController() ?: return@dispatch_async

                val previewController = QLPreviewController()
                val previewItem = QuickLookPreviewItem(fileUrl, fileUrl.lastPathComponent ?: "File")
                val dataSource = QuickLookDataSource(previewItem)
                val delegate = QuickLookDelegate {
                    activePreviewController = null
                    activeDataSource = null
                    activeDelegate = null
                }

                previewController.dataSource = dataSource
                previewController.delegate = delegate

                activePreviewController = previewController
                activeDataSource = dataSource
                activeDelegate = delegate

                topController.presentViewController(
                    previewController,
                    animated = true,
                    completion = null
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    actual fun getAvailableDirectories(): List<Pair<String, String>> {
        val docs = getDownloadsDirectory()
        val fileManager = NSFileManager.defaultManager
        val list = listOf(
            "Documents" to docs,
            "Downloads" to "$docs/Downloads",
            "Media" to "$docs/Media",
            "Movies" to "$docs/Movies",
            "Music" to "$docs/Music"
        )
        list.forEach { (_, path) ->
            if (!fileManager.fileExistsAtPath(path)) {
                try {
                    fileManager.createDirectoryAtPath(path, true, null, null)
                } catch (_: Exception) {
                }
            }
        }
        return list
    }

    actual fun listSubdirectories(path: String): List<String> {
        return listSubdirectories(path, false)
    }

    actual fun listSubdirectories(path: String, includeHidden: Boolean): List<String> {
        return try {
            val fileManager = NSFileManager.defaultManager
            val contents = fileManager.contentsOfDirectoryAtPath(path, null) ?: return emptyList()
            val subdirs = mutableListOf<String>()
            for (item in contents) {
                val name = item as? String ?: continue
                if (!includeHidden && name.startsWith(".")) continue
                val full = "$path/$name"
                val attrs = fileManager.attributesOfItemAtPath(full, null)
                val type = attrs?.get(NSFileType) as? String
                if (type == NSFileTypeDirectory) {
                    subdirs.add(name)
                }
            }
            subdirs.sortedWith(String.CASE_INSENSITIVE_ORDER)
        } catch (_: Exception) {
            emptyList()
        }
    }

    actual fun createSubdirectory(parentPath: String, name: String): String? {
        return try {
            val safeName = sanitizeFileName(name)
            if (safeName.isBlank()) return null
            val fullPath = "$parentPath/$safeName"
            val fileManager = NSFileManager.defaultManager
            val created = fileManager.createDirectoryAtPath(fullPath, true, null, null)
            if (created) fullPath else null
        } catch (_: Exception) {
            null
        }
    }

    actual fun getParentDirectory(path: String): String? {
        return try {
            val cleaned = path.trimEnd('/')
            val idx = cleaned.lastIndexOf('/')
            if (idx > 0) cleaned.substring(0, idx) else null
        } catch (_: Exception) {
            null
        }
    }
}