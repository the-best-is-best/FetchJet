package com.michelle.raouf.fetch_jet.ios

import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.core.settings.ClipboardLinks
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.getClipboardText
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEffect
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface IosCancelable {
    fun cancel()
}

private class JobCancelable(private val jobs: List<Job>) : IosCancelable {
    override fun cancel() = jobs.forEach { it.cancel() }
}

class IosAppHost {
    private val scope = MainScope()
    val viewModel: DashboardViewModel

    init {
        LocalizationManager.start()
        viewModel = DashboardViewModel()
        watchClipboard()
    }

    val state: DashboardUiState get() = viewModel.uiState.value

    fun watch(
        onState: (DashboardUiState) -> Unit,
        onEffect: (DashboardUiEffect) -> Unit
    ): IosCancelable {
        val a = scope.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { onState(it) }
        }
        val b = scope.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiEffect.collect { onEffect(it) }
        }
        return JobCancelable(listOf(a, b))
    }

    fun watchLanguage(onChange: (String, Boolean) -> Unit): IosCancelable {
        val job = scope.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            AppLanguage.current.collect { onChange(it, AppLanguage.isRtl) }
        }
        return JobCancelable(listOf(job))
    }

    fun send(event: DashboardUiEvent) = viewModel.onEvent(event)

    fun strings(): AppLocalization = LocalizationManager.Strings

    val language: String get() = AppLanguage.current.value
    val isRtl: Boolean get() = AppLanguage.isRtl
    val languageChoice: String get() = AppLanguage.choice
    fun languages(): List<AppLocalization> = appLanguages

    val torrentEnabled: Boolean get() = AppFeatures.torrent
    val mediaEnabled: Boolean get() = AppFeatures.mediaExtractor

    fun isDark(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    fun localizeStep(text: String): String =
        com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep(text)

    fun ltr(text: String): String = com.michelle.raouf.fetch_jet.ios.ltr(text)
    fun formatBytes(bytes: Long): String = com.michelle.raouf.fetch_jet.ios.formatBytes(bytes)
    fun formatSizeDisplay(bytes: Long): String = com.michelle.raouf.fetch_jet.ios.formatSizeDisplay(bytes)
    fun formatSpeed(bytesPerSec: Long, unit: String): String =
        com.michelle.raouf.fetch_jet.ios.formatSpeed(bytesPerSec, unit)
    fun decodeUnicodeText(input: String): String = com.michelle.raouf.fetch_jet.ios.decodeUnicodeText(input)
    fun displayUrl(url: String): String = com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(url)
    fun formatSecondsToHhMm(totalSeconds: Long): String =
        com.michelle.raouf.fetch_jet.ios.formatSecondsToHhMm(totalSeconds)
    fun parseHhMmToSeconds(hhMm: String): Long = com.michelle.raouf.fetch_jet.ios.parseHhMmToSeconds(hhMm)
    fun formatSecondsToDisplayFriendly(totalSeconds: Long): String =
        com.michelle.raouf.fetch_jet.ios.formatSecondsToDisplayFriendly(totalSeconds)

    /** false = the file is gone from disk (show the "missing file" dialog). */
    fun openDownloadedFile(fullPath: String): Boolean {
        val resolver = PathResolver()
        if (!resolver.fileExists(fullPath)) return false
        if (!resolver.openFile(fullPath)) resolver.openFileLocation(fullPath)
        return true
    }

    fun taskFilePath(task: DownloadTask): String {
        val dest = task.destinationPath.trimEnd('/', '\\')
        val sep = if (dest.contains('\\')) "\\" else "/"
        return com.michelle.raouf.fetch_jet.ios.decodeUnicodeText("$dest$sep${task.fileName}").trim()
    }

    fun openTaskFile(task: DownloadTask): Boolean = openDownloadedFile(taskFilePath(task))

    private var clipboardFirstRun = true

    private fun watchClipboard() {
        scope.launch {
            viewModel.uiState
                .map { it.monitorClipboard to it.clipboardVideoPages }
                .distinctUntilChanged()
                .collectLatest { (watch, videoPages) ->
                    if (!watch) {
                        clipboardFirstRun = false
                        return@collectLatest
                    }
                    var last: String? =
                        if (clipboardFirstRun) runCatching { getClipboardText() }.getOrNull() else null
                    clipboardFirstRun = false
                    while (true) {
                        delay(800)
                        val text = runCatching { getClipboardText() }.getOrNull()
                        if (text != null && text != last) {
                            last = text
                            ClipboardLinks.downloadableUrl(text, videoPages)
                                ?.let { viewModel.onEvent(DashboardUiEvent.OnClipboardLink(it)) }
                        }
                    }
                }
        }
    }
}
