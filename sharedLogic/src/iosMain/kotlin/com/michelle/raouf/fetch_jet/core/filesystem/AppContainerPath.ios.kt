package com.michelle.raouf.fetch_jet.core.filesystem

import platform.Foundation.NSHomeDirectory

private val containerRoot = Regex("^.*?/Containers/Data/Application/[0-9A-Fa-f-]{36}")

/**
 * The container root itself is never a download folder (Files does not show it): an older
 * Add dialog saved it by taking "Documents" for a type folder. It becomes Documents again.
 */
actual fun relocateAppPath(path: String): String {
    val home = NSHomeDirectory().trimEnd('/')
    val old = containerRoot.find(path)?.value ?: return path
    val moved = if (old == home) path else home + path.substring(old.length)
    return if (moved.trimEnd('/') == home) "$home/Documents" else moved
}
