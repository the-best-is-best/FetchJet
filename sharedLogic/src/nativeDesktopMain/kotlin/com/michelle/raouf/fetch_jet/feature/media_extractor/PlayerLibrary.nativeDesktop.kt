package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.StateFlow

internal object PlayerLibraryPin {
    const val VERSION = "0.42.0"
    const val REPOSITORY = "the-best-is-best/fetchjet-tools"
    const val BUILD = "1"
    const val RELEASE_TAG = "libmpv-$VERSION-$BUILD"
    const val WINDOWS_X64_SHA256 = "3a5c5baaa1636997c96e2a66f4d7f0e16ae2656dd54410387e1a07ca5ae035bc"
    const val LINUX_X64_SHA256 = "aaea068d908f84b53da0c0c036f5ae026331a15ad00f941d54f949bfe78d5b3e"
    const val MACOS_ARM64_SHA256 = "e40a2597528eac520ad1a3cf864bff2be54a78c3ab2660ed207d01ca091fe3c7"
}

internal val playerLibrary = OnDemandLibrary(
    name = "libmpv",
    stem = "libmpv",
    envPrefix = "FETCHJET_LIBMPV",
    pinned = {
        val (file, platform, sha) = when (DesktopOs.name) {
            "Windows" -> Triple("libmpv-2.dll", "windows-x64", PlayerLibraryPin.WINDOWS_X64_SHA256)
            "macOS" -> Triple("libmpv.2.dylib", "macos-arm64", PlayerLibraryPin.MACOS_ARM64_SHA256)
            else -> Triple("libmpv.so.2", "linux-x64", PlayerLibraryPin.LINUX_X64_SHA256)
        }
        OnDemandAsset(
            file,
            "https://github.com/${PlayerLibraryPin.REPOSITORY}/releases/download/${PlayerLibraryPin.RELEASE_TAG}/" +
                "libmpv-$platform-${PlayerLibraryPin.VERSION}.gz",
            sha,
        )
    },
    systemPaths = {
        when (DesktopOs.name) {
            "Linux" -> listOf("/usr/lib", "/usr/lib64", "/usr/lib/x86_64-linux-gnu", "/usr/local/lib").map { "$it/libmpv.so.2" }
            "macOS" -> listOf("/opt/homebrew/lib/libmpv.2.dylib", "/usr/local/lib/libmpv.2.dylib")
            else -> emptyList()
        }
    },
)

fun getExistingPlayerLibrary(): String? = playerLibrary.existing()

fun isPlayerLibraryOutdated(): Boolean = playerLibrary.isOutdated()

fun isPlayerLibraryReady(): Boolean = playerLibrary.isReady()

fun isPlayerLibraryAvailable(): Boolean = playerLibrary.isAvailable()

suspend fun downloadPlayerLibrary(onProgress: (ToolDownloadProgress) -> Unit = {}): String? = playerLibrary.download(onProgress)

fun DashboardViewModel.ensurePlayerLibrary(toast: Boolean = true, onReady: () -> Unit) = playerLibrary.ensure(this, toast, onReady)

fun playerLibraryStatus(): StateFlow<OnDemandStatus> = playerLibrary.status
