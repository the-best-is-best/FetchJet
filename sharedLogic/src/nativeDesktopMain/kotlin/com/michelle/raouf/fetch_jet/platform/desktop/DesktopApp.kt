package com.michelle.raouf.fetch_jet.platform.desktop

import kotlin.concurrent.Volatile

expect object DesktopApp {
    var homeOverride: String?
    var automaticDownloads: Boolean
    val appHome: String
    val kindName: String
    val isDevelopmentBuild: Boolean
    val exeDir: String
    val launcherPath: String?
    fun overrideKind(name: String)
    fun looksLikeLoginStart(): Boolean
}

object HostHooks {
    @Volatile
    var pickFile: ((title: String, filterName: String, pattern: String) -> String?)? = null

    @Volatile
    var pickFolder: ((title: String, initialPath: String?) -> String?)? = null

    @Volatile
    var requestExit: (() -> Unit)? = null

    @Volatile
    var showWindow: (() -> Unit)? = null
}
