package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.feature.archive_extract.GzipSource
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEffect
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.presentation.dashboard.refreshToolStatus
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

internal object PlayerLibraryPin {
    const val VERSION = "0.42.0"
    const val REPOSITORY = "the-best-is-best/fetchjet-tools"
    const val RELEASE_TAG = "libmpv-$VERSION"
    const val WINDOWS_X64_SHA256 = "ecdd46e7d604e493227aca10e669063ce8825cb90e7a687d80ae671e274528c3"
    const val LINUX_X64_SHA256 = ""
    const val MACOS_ARM64_SHA256 = "53944aff2a7ece0baeec869c5e2aa67037ff093e2122fb5f526c01c46518fde5"
}

internal class PlayerLibraryAsset(val fileName: String, val url: String, val sha256: String)

internal fun playerLibraryAsset(): PlayerLibraryAsset {
    val (file, platform, sha) = when (DesktopOs.name) {
        "Windows" -> Triple("libmpv-2.dll", "windows-x64", PlayerLibraryPin.WINDOWS_X64_SHA256)
        "macOS" -> Triple("libmpv.2.dylib", "macos-arm64", PlayerLibraryPin.MACOS_ARM64_SHA256)
        else -> Triple("libmpv.so.2", "linux-x64", PlayerLibraryPin.LINUX_X64_SHA256)
    }
    val url = "https://github.com/${PlayerLibraryPin.REPOSITORY}/releases/download/${PlayerLibraryPin.RELEASE_TAG}/" +
        "libmpv-$platform-${PlayerLibraryPin.VERSION}.gz"
    val overrideUrl = DesktopOs.getEnv("FETCHJET_LIBMPV_URL")?.takeIf { it.isNotBlank() }
    val overrideSha = DesktopOs.getEnv("FETCHJET_LIBMPV_SHA256")?.takeIf { it.isNotBlank() }
    return if (overrideUrl != null && overrideSha != null) {
        PlayerLibraryAsset(file, overrideUrl, overrideSha.lowercase())
    } else {
        PlayerLibraryAsset(file, url, sha)
    }
}

private val systemPlayerLibraryPaths: List<String>
    get() = when (DesktopOs.name) {
        "Linux" -> listOf("/usr/lib", "/usr/lib64", "/usr/lib/x86_64-linux-gnu", "/usr/local/lib").map { "$it/libmpv.so.2" }
        "macOS" -> listOf("/opt/homebrew/lib/libmpv.2.dylib", "/usr/local/lib/libmpv.2.dylib")
        else -> emptyList()
    }

private fun isFilePath(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

fun getExistingPlayerLibrary(): String? =
    DesktopOs.join(getToolsDir(), playerLibraryAsset().fileName).takeIf { isFilePath(it) }

private fun nextToExecutable(): Boolean =
    DesktopOs.join(DesktopApp.exeDir, playerLibraryAsset().fileName).let { isFilePath(it) }

fun isPlayerLibraryReady(): Boolean =
    getExistingPlayerLibrary() != null || nextToExecutable() || systemPlayerLibraryPaths.any { isFilePath(it) }

fun isPlayerLibraryAvailable(): Boolean = isPlayerLibraryReady() || playerLibraryAsset().sha256.isNotEmpty()

internal suspend fun installPlayerLibrary(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
    val asset = playerLibraryAsset()
    if (asset.sha256.isEmpty()) throw IllegalStateException("libmpv is not available in this build yet (checksum not pinned)")
    val download = DesktopOs.join(toolsDir, "libmpv.download")
    val unpacked = DesktopOs.join(toolsDir, "${asset.fileName}.tmp")
    val target = DesktopOs.join(toolsDir, asset.fileName)
    try {
        onProgress(ToolDownloadProgress("libmpv", 0f))
        val actual = downloadTo(asset.url, download) { onProgress(ToolDownloadProgress("libmpv", it)) }
        if (actual != asset.sha256) {
            throw IllegalStateException("libmpv checksum mismatch (expected ${asset.sha256}, got $actual)")
        }
        GzipSource(AppFileSystem.source(download.toPath()).buffer()).buffer().use { input ->
            AppFileSystem.sink(unpacked.toPath()).buffer().use { out -> out.writeAll(input) }
        }
        check(DesktopOs.move(unpacked, target, replace = true)) { "could not replace ${asset.fileName} (is it in use?)" }
    } finally {
        DesktopOs.deleteFile(download)
        DesktopOs.deleteFile(unpacked)
    }
}

suspend fun downloadPlayerLibrary(onProgress: (ToolDownloadProgress) -> Unit = {}): String? =
    withContext(Dispatchers.IO) {
        try {
            installPlayerLibrary(getToolsDir(), onProgress)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("libmpv could not be installed: ${e.message}")
            e.message ?: "error"
        }
    }

private object PlayerLibraryInstall {
    @Volatile
    var running = false

    @Volatile
    var pending: (() -> Unit)? = null
}

fun DashboardViewModel.ensurePlayerLibrary(onReady: () -> Unit) {
    if (isPlayerLibraryReady()) {
        onReady()
        return
    }
    PlayerLibraryInstall.pending = onReady
    if (PlayerLibraryInstall.running) return
    PlayerLibraryInstall.running = true
    scope.launch {
        var error: String? = null
        try {
            error = toolsMutex.withLock { downloadPlayerLibrary(::reportToolProgress) }
        } finally {
            _uiState.update { it.copy(isDownloadingYtDlp = false, toolDownloadName = null, toolDownloadFraction = null) }
            PlayerLibraryInstall.running = false
            if (error != null) PlayerLibraryInstall.pending = null
        }
        refreshToolStatus()
        val open = PlayerLibraryInstall.pending
        PlayerLibraryInstall.pending = null
        if (error == null && isPlayerLibraryReady()) {
            open?.invoke()
        } else {
            _uiEffect.emit(DashboardUiEffect.ShowToast(LocalizationManager.Strings.sysMediaToolsDownloadFailed))
        }
    }
}
