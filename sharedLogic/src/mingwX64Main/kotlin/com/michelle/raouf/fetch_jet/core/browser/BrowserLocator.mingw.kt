package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached

internal actual object BrowserLocator {
    private fun roots(): List<String> = listOf(
        DesktopOs.getEnv("ProgramFiles") ?: "C:\\Program Files",
        DesktopOs.getEnv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
        DesktopOs.getEnv("LOCALAPPDATA").orEmpty(),
    ).filter { it.isNotBlank() }

    actual fun hasCandidates(paths: BrowserPaths): Boolean = paths.windows.isNotEmpty()

    actual fun find(paths: BrowserPaths): String? =
        paths.windows.firstNotNullOfOrNull { relative ->
            roots().map { DesktopOs.join(it, relative) }.firstOrNull { DesktopOs.fileInfo(it)?.isDirectory == false }
        }

    actual fun start(executable: String, args: List<String>): Boolean =
        DesktopOs.startDetached(listOf(executable) + args)
}
