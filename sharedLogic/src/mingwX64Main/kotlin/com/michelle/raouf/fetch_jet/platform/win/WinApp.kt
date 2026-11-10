package com.michelle.raouf.fetch_jet.platform.win

import kotlin.concurrent.Volatile

enum class WinInstallKind { DEVELOPMENT, INSTALLED, STORE }

object WinApp {
    const val INSTALLED_MARKER = "FetchJet.installed"
    const val HOME_FOLDER = ".fetch_jet"

    @Volatile
    var homeOverride: String? = null

    @Volatile
    var kindOverride: WinInstallKind? = null

    @Volatile
    var automaticDownloads: Boolean = winGetEnv("FETCHJET_NO_AUTO_DOWNLOADS") != "1"

    val exePath: String by lazy { winExecutablePath() }

    val exeDir: String by lazy { exePath.substringBeforeLast('\\', "") }

    private val detectedKind: WinInstallKind by lazy {
        when {
            exePath.contains("\\WindowsApps\\", ignoreCase = true) -> WinInstallKind.STORE
            exeDir.isNotEmpty() && winExists("$exeDir\\$INSTALLED_MARKER") -> WinInstallKind.INSTALLED
            winGetEnv("FETCHJET_INSTALLED") == "1" -> WinInstallKind.INSTALLED
            else -> WinInstallKind.DEVELOPMENT
        }
    }

    val kind: WinInstallKind get() = kindOverride ?: detectedKind

    val isStoreBuild: Boolean get() = kind == WinInstallKind.STORE

    val isDevelopmentBuild: Boolean get() = kind == WinInstallKind.DEVELOPMENT

    val launcherPath: String? get() = if (kind == WinInstallKind.DEVELOPMENT) null else exePath

    val appHome: String
        get() {
            homeOverride?.let { return it }
            winGetEnv("FETCHJET_HOME")?.takeIf { it.isNotBlank() }?.let { return it.replace('/', '\\').trimEnd('\\') }
            if (kind != WinInstallKind.DEVELOPMENT) return winUserProfile() + "\\" + HOME_FOLDER
            val local = winGetEnv("LOCALAPPDATA") ?: winUserProfile()
            return "$local\\FetchJetDev\\$HOME_FOLDER"
        }

    fun looksLikeLoginStart(): Boolean = isStoreBuild && winUptimeMs() < 120_000L
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
