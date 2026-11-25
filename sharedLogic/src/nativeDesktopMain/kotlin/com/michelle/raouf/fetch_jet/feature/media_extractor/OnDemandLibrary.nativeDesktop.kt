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

internal class OnDemandAsset(val fileName: String, val url: String, val sha256: String)

internal class OnDemandLibrary(
    val name: String,
    private val stem: String,
    private val envPrefix: String,
    private val pinned: () -> OnDemandAsset,
    private val systemPaths: () -> List<String> = { emptyList() },
) {
    @Volatile
    private var running = false

    @Volatile
    private var pending: (() -> Unit)? = null

    fun asset(): OnDemandAsset {
        val base = pinned()
        val overrideUrl = DesktopOs.getEnv("${envPrefix}_URL")?.takeIf { it.isNotBlank() }
        val overrideSha = DesktopOs.getEnv("${envPrefix}_SHA256")?.takeIf { it.isNotBlank() }
        return if (overrideUrl != null && overrideSha != null) OnDemandAsset(base.fileName, overrideUrl, overrideSha.lowercase()) else base
    }

    private fun isFilePath(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

    fun existing(): String? = DesktopOs.join(getToolsDir(), asset().fileName).takeIf { isFilePath(it) }

    private fun nextToExecutable(): Boolean = isFilePath(DesktopOs.join(DesktopApp.exeDir, asset().fileName))

    private fun shaFile(): String = DesktopOs.join(getToolsDir(), "$stem.sha256")

    private fun installedSha(): String =
        runCatching { AppFileSystem.read(shaFile().toPath()) { readUtf8() }.trim().lowercase() }.getOrDefault("")

    fun isOutdated(): Boolean {
        if (existing() == null) return false
        val sha = asset().sha256
        return sha.isNotEmpty() && installedSha() != sha
    }

    fun isReady(): Boolean =
        if (existing() != null) !isOutdated()
        else nextToExecutable() || systemPaths().any { isFilePath(it) }

    fun isAvailable(): Boolean = isReady() || asset().sha256.isNotEmpty()

    suspend fun install(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
        val asset = asset()
        if (asset.sha256.isEmpty()) throw IllegalStateException("$name is not available in this build yet (checksum not pinned)")
        val download = DesktopOs.join(toolsDir, "$stem.download")
        val unpacked = DesktopOs.join(toolsDir, "${asset.fileName}.tmp")
        val target = DesktopOs.join(toolsDir, asset.fileName)
        try {
            onProgress(ToolDownloadProgress(name, 0f))
            val actual = downloadTo(asset.url, download) { onProgress(ToolDownloadProgress(name, it)) }
            if (actual != asset.sha256) {
                throw IllegalStateException("$name checksum mismatch (expected ${asset.sha256}, got $actual)")
            }
            GzipSource(AppFileSystem.source(download.toPath()).buffer()).buffer().use { input ->
                AppFileSystem.sink(unpacked.toPath()).buffer().use { out -> out.writeAll(input) }
            }
            check(DesktopOs.move(unpacked, target, replace = true)) { "could not replace ${asset.fileName} (is it in use?)" }
            AppFileSystem.write(shaFile().toPath()) { writeUtf8(asset.sha256) }
        } finally {
            DesktopOs.deleteFile(download)
            DesktopOs.deleteFile(unpacked)
        }
    }

    suspend fun download(onProgress: (ToolDownloadProgress) -> Unit = {}): String? =
        withContext(Dispatchers.IO) {
            try {
                install(getToolsDir(), onProgress)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("$name could not be installed: ${e.message}")
                e.message ?: "error"
            }
        }

    fun ensure(vm: DashboardViewModel, onReady: () -> Unit) {
        if (isReady()) {
            onReady()
            return
        }
        pending = onReady
        if (running) return
        running = true
        vm.scope.launch {
            var error: String? = null
            try {
                error = vm.toolsMutex.withLock { download(vm::reportToolProgress) }
            } finally {
                vm._uiState.update { it.copy(isDownloadingYtDlp = false, toolDownloadName = null, toolDownloadFraction = null) }
                running = false
                if (error != null) pending = null
            }
            vm.refreshToolStatus()
            val open = pending
            pending = null
            if (error == null && isReady()) {
                open?.invoke()
            } else {
                vm._uiEffect.emit(DashboardUiEffect.ShowToast(LocalizationManager.Strings.sysMediaToolsDownloadFailed))
            }
        }
    }
}
