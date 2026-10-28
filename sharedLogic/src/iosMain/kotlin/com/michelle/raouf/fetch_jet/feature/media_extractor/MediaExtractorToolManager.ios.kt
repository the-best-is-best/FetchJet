@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import io.ktor.client.request.get
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.posix.X_OK
import platform.posix.access
import platform.posix.chmod

fun getIosToolsDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val docs = (paths.firstOrNull() as? String) ?: ""
    val toolsDir = "$docs/tools"
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(toolsDir)) {
        try {
            fileManager.createDirectoryAtPath(toolsDir, true, null, null)
        } catch (_: Exception) {
        }
    }
    return toolsDir
}

/**
 * Looks for cookies.txt in multiple standard accessible paths so users can drop a cookies.txt
 * file into their Documents or FetchJet folder via the iOS Files app to bypass age restrictions
 * and bot verification, matching Android's FileUtil.getCookieFile.
 */
fun getIosCookieFile(): String? {
    val fileManager = NSFileManager.defaultManager
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val docs = (paths.firstOrNull() as? String) ?: ""
    val libPaths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true)
    val lib = (libPaths.firstOrNull() as? String) ?: ""
    val toolsDir = "$docs/tools"

    val dirCandidates = listOf(
        docs,
        "$docs/FetchJet",
        "$docs/Kortex",
        "$docs/Downloads",
        "$docs/settings",
        toolsDir,
        lib,
        NSBundle.mainBundle.bundlePath,
        NSBundle.mainBundle.resourcePath ?: ""
    )

    val fileNames = listOf(
        "cookies.txt",
        "cookie.txt",
        "cookies.txt.txt",
        "youtube.com_cookies.txt",
        "youtube_cookies.txt",
        "Cookies.txt",
        "Cookie.txt"
    )

    // 1. Check known directories and file names
    for (dir in dirCandidates) {
        if (dir.isBlank()) continue
        for (name in fileNames) {
            val p = "$dir/$name"
            if (fileManager.fileExistsAtPath(p)) {
                val attrs = fileManager.attributesOfItemAtPath(p, null)
                val size = (attrs?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
                if (size > 0L) {
                    return p
                }
            }
        }
    }

    // 2. Scan docs directory for any .txt or .cookies file containing Netscape cookie header
    val docsFiles = fileManager.contentsOfDirectoryAtPath(docs, null) as? List<String>
    if (docsFiles != null) {
        for (item in docsFiles) {
            if (item.endsWith(".txt", ignoreCase = true) || item.endsWith(
                    ".cookies",
                    ignoreCase = true
                )
            ) {
                val fullPath = "$docs/$item"
                val attrs = fileManager.attributesOfItemAtPath(fullPath, null)
                val size = (attrs?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
                if (size in 10..5_000_000) {
                    val content =
                        NSString.stringWithContentsOfFile(fullPath, NSUTF8StringEncoding, null)
                    if (content != null && (content.contains("youtube.com") || content.contains("google.com") || content.contains(
                            "Netscape HTTP Cookie File"
                        ))
                    ) {
                        return fullPath
                    }
                }
            }
        }
    }

    return null
}

@OptIn(ExperimentalForeignApi::class)
fun isExecutable(path: String): Boolean {
    return access(path, X_OK) == 0
}

fun getExistingYtDlpBinary(): String? {
    val fileManager = NSFileManager.defaultManager
    val toolsDir = getIosToolsDirectory()

    // 1. App tools directory (Documents/tools/yt-dlp)
    val inTools = "$toolsDir/yt-dlp"
    if (fileManager.fileExistsAtPath(inTools)) {
        chmod(inTools, 493.toUShort())
        return inTools
    }

    // 2. App bundle resources -> copy to toolsDir with 0755 permissions (like Android's initYTDLP)
    val bundlePath = NSBundle.mainBundle.bundlePath
    val inBundle = "$bundlePath/yt-dlp"
    val resourcePath = NSBundle.mainBundle.resourcePath
    val inResource = if (resourcePath != null) "$resourcePath/yt-dlp" else null
    val bundleYtDlp = when {
        fileManager.fileExistsAtPath(inBundle) -> inBundle
        inResource != null && fileManager.fileExistsAtPath(inResource) -> inResource
        else -> null
    }

    if (bundleYtDlp != null) {
        try {
            if (fileManager.fileExistsAtPath(inTools)) {
                fileManager.removeItemAtPath(inTools, null)
            }
            fileManager.copyItemAtPath(bundleYtDlp, inTools, null)
            chmod(inTools, 493.toUShort())
            return inTools
        } catch (_: Exception) {
            chmod(bundleYtDlp, 493.toUShort())
            return bundleYtDlp
        }
    }

    // 3. Known system & sideload / jailbreak paths (TrollStore, Dopamine, Palera1n, Procursus)
    val knownPaths = listOf(
        "/var/jb/usr/bin/yt-dlp",
        "/usr/local/bin/yt-dlp",
        "/usr/bin/yt-dlp",
        "/opt/procursus/bin/yt-dlp",
        "/var/jb/usr/local/bin/yt-dlp",
        "/opt/homebrew/bin/yt-dlp"
    )
    for (p in knownPaths) {
        if (fileManager.fileExistsAtPath(p)) return p
    }

    return null
}

fun getExistingPythonBinary(): String? {
    val fileManager = NSFileManager.defaultManager
    val toolsDir = getIosToolsDirectory()

    val candidates = listOf(
        "$toolsDir/python3",
        "${NSBundle.mainBundle.bundlePath}/python3",
        "${NSBundle.mainBundle.resourcePath}/python3",
        "${NSBundle.mainBundle.bundlePath}/Frameworks/Python.framework/python3",
        "/var/jb/usr/bin/python3",
        "/usr/local/bin/python3",
        "/usr/bin/python3",
        "/opt/procursus/bin/python3",
        "/opt/homebrew/bin/python3",
        "/var/jb/usr/local/bin/python3"
    )
    for (p in candidates) {
        if (fileManager.fileExistsAtPath(p)) return p
    }
    return null
}

fun getExistingFfmpegBinary(): String? {
    val fileManager = NSFileManager.defaultManager
    val toolsDir = getIosToolsDirectory()

    val candidates = listOf(
        "$toolsDir/ffmpeg",
        "${NSBundle.mainBundle.bundlePath}/ffmpeg",
        "${NSBundle.mainBundle.resourcePath}/ffmpeg",
        "/var/jb/usr/bin/ffmpeg",
        "/usr/local/bin/ffmpeg",
        "/usr/bin/ffmpeg",
        "/opt/procursus/bin/ffmpeg",
        "/opt/homebrew/bin/ffmpeg",
        "/var/jb/usr/local/bin/ffmpeg"
    )
    for (p in candidates) {
        if (fileManager.fileExistsAtPath(p)) return p
    }
    return null
}

actual fun getMediaExtractorToolStatus(): String {
    val bridge = IosMediaBridgeRegistry.bridge
        ?: return "Media engine bridge not registered"
    return bridge.statusText()
}

actual fun isMediaExtractorToolInstalled(): Boolean =
    IosMediaBridgeRegistry.bridge?.isAvailable() == true

actual fun isFfmpegToolInstalled(): Boolean =
    IosMediaBridgeRegistry.bridge?.isAvailable() == true

actual fun getMediaToolsDirectory(): String {
    return getIosToolsDirectory()
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadMediaExtractorTool(): Boolean = withContext(Dispatchers.IO) {
    // "Download Tools" on iOS: ask the Swift bridge to boot the embedded Python engine and
    // fetch/update the yt_dlp module. A standalone yt-dlp binary cannot run on iOS, so
    // there is nothing else to download here.
    val bridge = IosMediaBridgeRegistry.bridge ?: return@withContext false
    bridge.prepareEngine() == null
}
