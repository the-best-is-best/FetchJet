package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached

internal actual object BrowserLocator {
    private fun roots(): List<String> =
        listOfNotNull("/Applications", DesktopOs.getEnv("HOME")?.takeIf { it.isNotBlank() }?.let { "$it/Applications" })

    actual fun hasCandidates(paths: BrowserPaths): Boolean = paths.mac.isNotEmpty()

    actual fun find(paths: BrowserPaths): String? =
        paths.mac.firstNotNullOfOrNull { entry ->
            val app = entry.substringBefore('/')
            val binary = entry.substringAfter('/', app)
            roots().map { "$it/$app.app" }
                .firstOrNull { DesktopOs.fileInfo("$it/Contents/MacOS/$binary")?.isDirectory == false }
        }

    actual fun start(executable: String, args: List<String>): Boolean =
        DesktopOs.startDetached(listOf("/usr/bin/open", "-a", executable) + args)
}
