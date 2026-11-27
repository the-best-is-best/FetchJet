package com.michelle.raouf.fetch_jet.platform.win

import kotlin.concurrent.Volatile

enum class WinInstallKind { DEVELOPMENT, INSTALLED, STORE, PORTABLE }

object WinApp {
    const val INSTALLED_MARKER = "FetchJet.installed"
    const val PORTABLE_MARKER = "FetchJet.portable"
    const val LEGACY_MARKER = "FetchJet.legacy"
    const val PORTABLE_DATA = "data"
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
            exeDir.isNotEmpty() && winExists("$exeDir\\$PORTABLE_MARKER") -> WinInstallKind.PORTABLE
            exeDir.isNotEmpty() && winExists("$exeDir\\$INSTALLED_MARKER") -> WinInstallKind.INSTALLED
            winGetEnv("FETCHJET_INSTALLED") == "1" -> WinInstallKind.INSTALLED
            else -> WinInstallKind.DEVELOPMENT
        }
    }

    val kind: WinInstallKind get() = kindOverride ?: detectedKind

    val isStoreBuild: Boolean get() = kind == WinInstallKind.STORE

    val isDevelopmentBuild: Boolean get() = kind == WinInstallKind.DEVELOPMENT

    val isPortable: Boolean get() = kind == WinInstallKind.PORTABLE

    val isLegacy: Boolean by lazy {
        winGetEnv("FETCHJET_LEGACY") == "1" || (exeDir.isNotEmpty() && winExists("$exeDir\\$LEGACY_MARKER"))
    }

    val launcherPath: String? get() = if (kind == WinInstallKind.DEVELOPMENT || kind == WinInstallKind.PORTABLE) null else exePath

    val appHome: String
        get() {
            homeOverride?.let { return it }
            winGetEnv("FETCHJET_HOME")?.takeIf { it.isNotBlank() }?.let { return it.replace('/', '\\').trimEnd('\\') }
            if (kind == WinInstallKind.PORTABLE) return "$exeDir\\$PORTABLE_DATA\\$HOME_FOLDER"
            if (kind != WinInstallKind.DEVELOPMENT) return winUserProfile() + "\\" + HOME_FOLDER
            val local = winGetEnv("LOCALAPPDATA") ?: winUserProfile()
            return "$local\\FetchJetDev\\$HOME_FOLDER"
        }

    fun looksLikeLoginStart(): Boolean = isStoreBuild && winUptimeMs() < 120_000L
}
