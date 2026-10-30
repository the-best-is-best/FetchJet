package com.michelle.raouf.fetch_jet.core.platform

/**
 * true when this is the Microsoft Store (MSIX) build: Windows installs it under
 * C:\Program Files\WindowsApps\. The Store updates it, so the in-app updater, the registry
 * based "start with Windows" and the automatic Edge setup are turned off there.
 */
object WindowsStorePackage {
    val isInstalled: Boolean by lazy {
        val path = System.getProperty("jpackage.app-path")
            ?: System.getProperty("compose.application.resources.dir")
            ?: System.getProperty("user.dir").orEmpty()
        path.contains("\\WindowsApps\\", ignoreCase = true)
    }
}
