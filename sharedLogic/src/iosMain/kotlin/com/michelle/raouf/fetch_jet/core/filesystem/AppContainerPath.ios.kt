package com.michelle.raouf.fetch_jet.core.filesystem

import platform.Foundation.NSHomeDirectory

private val containerRoot = Regex("^.*?/Containers/Data/Application/[0-9A-Fa-f-]{36}")

actual fun relocateAppPath(path: String): String {
    val home = NSHomeDirectory().trimEnd('/')
    val old = containerRoot.find(path)?.value ?: return path
    return if (old == home) path else home + path.substring(old.length)
}
