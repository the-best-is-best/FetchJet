package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.core.browser.BrowserInfo
import com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
import com.michelle.raouf.fetch_jet.core.browser.ExtensionStore
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.ProxyKind
import com.michelle.raouf.fetch_jet.core.network.ProxySettings
import com.michelle.raouf.fetch_jet.core.power.SpeedSchedule
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.feature.device_sync.PairedDevice
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingsNavDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingsSectionDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ToneCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DEVICE_SYNC_READY = true

internal enum class SettingsSection(val glyph: String) {
    GENERAL("◐"),
    DOWNLOADS("↓"),
    BROWSER("⧉"),
    DEVICES("⇄"),
    NETWORK("◎"),
    MEDIA("▶"),
    TORRENT("⇅"),
    UPDATES("↻"),
}

internal fun SettingsSection.title(): String {
    val s = LocalizationManager.Strings
    return when (this) {
        SettingsSection.GENERAL -> s.sectionGeneral
        SettingsSection.DOWNLOADS -> s.sectionDownloads
        SettingsSection.BROWSER -> s.sectionBrowser
        SettingsSection.DEVICES -> s.sectionDevices
        SettingsSection.NETWORK -> s.sectionNetwork
        SettingsSection.MEDIA -> s.sectionMedia
        SettingsSection.TORRENT -> s.sectionTorrent
        SettingsSection.UPDATES -> s.sectionUpdates
    }
}

internal fun SettingsSection.blurb(): String {
    val s = LocalizationManager.Strings
    return when (this) {
        SettingsSection.GENERAL -> s.sectionGeneralBlurb
        SettingsSection.DOWNLOADS -> s.sectionDownloadsBlurb
        SettingsSection.BROWSER -> s.sectionBrowserBlurb
        SettingsSection.DEVICES -> s.sectionDevicesBlurb
        SettingsSection.NETWORK -> s.sectionNetworkBlurb
        SettingsSection.MEDIA -> s.sectionMediaBlurb
        SettingsSection.TORRENT -> s.sectionTorrentBlurb
        SettingsSection.UPDATES -> s.sectionUpdatesBlurb
    }
}

internal data class ProxyDraft(
    val base: ProxySettings,
    val kind: ProxyKind,
    val host: String,
    val port: String,
    val user: String,
    val password: String,
) {
    fun settings() = ProxySettings(kind, host.trim(), port.trim().toIntOrNull() ?: 0, user.trim(), password)

    companion object {
        fun from(saved: ProxySettings) = ProxyDraft(
            base = saved,
            kind = saved.kind,
            host = saved.host,
            port = if (saved.port > 0) saved.port.toString() else "",
            user = saved.user,
            password = saved.password,
        )
    }
}

internal sealed interface BrowserDialog {
    data class Missing(val browser: BrowserInfo) : BrowserDialog
    data class Steps(val browser: BrowserInfo, val folder: String, val page: String) : BrowserDialog
    data class FromFile(val browser: BrowserInfo, val fileName: String) : BrowserDialog
    data class Failure(val reason: String) : BrowserDialog
}

internal data class SettingsPage(
    val presetText: Map<String, String> = emptyMap(),
    val presetUnit: Map<String, String> = emptyMap(),
    val timeText: Map<String, String> = emptyMap(),
    val timePm: Map<String, Boolean> = emptyMap(),
    val proxy: ProxyDraft? = null,
    val proxyJustSaved: Boolean = false,
    val loginSite: String = "",
    val loginUser: String = "",
    val loginPassword: String = "",
    val startupBusy: Boolean = true,
    val startupEnabled: Boolean = false,
    val startHidden: Boolean = true,
    val startupError: String? = null,
    val defaultApp: Boolean? = null,
    val defaultAppBusy: Boolean = false,
    val browsers: List<BrowserInfo> = emptyList(),
    val browserBusy: String? = null,
    val storeVersions: Map<String, String> = emptyMap(),
    val storeAsked: Set<String> = emptySet(),
    val githubXpi: ExtensionStore.SignedXpi? = null,
    val limitUnit: String? = null,
    val limitText: String? = null,
    val limitCustomAt: Long? = null,
)

internal data class SettingsLocal(
    val section: SettingsSection? = null,
    val generation: Int = 0,
    val poke: Int = 0,
    val page: SettingsPage = SettingsPage(),
    val browserDialog: BrowserDialog? = null,
)

internal class EchoFields {
    private val fields = AtomicReference<Map<String, EchoField>>(emptyMap())

    fun of(id: String): EchoField {
        while (true) {
            val map = fields.value
            map[id]?.let { return it }
            val created = EchoField()
            if (fields.compareAndSet(map, map + (id to created))) return created
        }
    }

    fun forceAll(prefix: String = "") {
        for ((id, field) in fields.value) if (id.startsWith(prefix)) field.force()
    }
}

internal data class SettingsSlice(
    val open: Boolean,
    val themeMode: String,
    val speedDisplayUnit: String,
    val monitorClipboard: Boolean,
    val clipboardVideoPages: Boolean,
    val destinationPath: String,
    val sortIntoFolders: Boolean,
    val defaultConnections: Int,
    val lowBytes: Long,
    val lowUnit: String,
    val mediumBytes: Long,
    val mediumUnit: String,
    val fastBytes: Long,
    val fastUnit: String,
    val ytDlpStatus: String,
    val isYtDlpInstalled: Boolean,
    val isFfmpegInstalled: Boolean,
    val jsRuntimeName: String,
    val isJsRuntimeReady: Boolean,
    val toolsDirectoryPath: String,
    val isDownloadingYtDlp: Boolean,
    val toolDownloadName: String?,
    val toolDownloadFraction: Float?,
    val pairedDevices: List<PairedDevice>,
    val blockedDevices: List<PairedDevice>,
    val disableTorrentUpload: Boolean,
    val uploadLimit: Long,
    val torrentEngineStatus: String,
    val isTorrentEngineReady: Boolean,
) {
    val unitKey: String
        get() = when (speedDisplayUnit) {
            "BYTES_PER_SEC", "BOTH" -> "MB/s"
            "BITS_PER_SEC" -> "Mbps"
            else -> speedDisplayUnit
        }
    val toolsReady: Boolean get() = isYtDlpInstalled && isFfmpegInstalled
}

internal fun DashboardUiState.settingsSlice() = SettingsSlice(
    open = selectedTab == DashboardTab.SETTINGS,
    themeMode = themeMode,
    speedDisplayUnit = speedDisplayUnit,
    monitorClipboard = monitorClipboard,
    clipboardVideoPages = clipboardVideoPages,
    destinationPath = destinationPath,
    sortIntoFolders = sortIntoFolders,
    defaultConnections = defaultConnections,
    lowBytes = speedPresetLowBytes,
    lowUnit = speedPresetLowUnit,
    mediumBytes = speedPresetMediumBytes,
    mediumUnit = speedPresetMediumUnit,
    fastBytes = speedPresetFastBytes,
    fastUnit = speedPresetFastUnit,
    ytDlpStatus = ytDlpStatus,
    isYtDlpInstalled = isYtDlpInstalled,
    isFfmpegInstalled = isFfmpegInstalled,
    jsRuntimeName = jsRuntimeName,
    isJsRuntimeReady = isJsRuntimeReady,
    toolsDirectoryPath = toolsDirectoryPath,
    isDownloadingYtDlp = isDownloadingYtDlp,
    toolDownloadName = toolDownloadName,
    toolDownloadFraction = toolDownloadFraction,
    pairedDevices = pairedDevices,
    blockedDevices = blockedDevices,
    disableTorrentUpload = disableTorrentUpload,
    uploadLimit = globalUploadSpeedLimitBytes,
    torrentEngineStatus = torrentEngineStatus,
    isTorrentEngineReady = isTorrentEngineReady,
)

internal fun Presenter.visibleSettingsSections(): List<SettingsSection> = SettingsSection.entries.filter {
    when (it) {
        SettingsSection.TORRENT -> AppFeatures.torrent || debugSections.value
        SettingsSection.UPDATES -> updateState().supported
        SettingsSection.DEVICES -> DEVICE_SYNC_READY || debugSections.value
        else -> true
    }
}

internal fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> LocalizationManager.Strings.settingsThemeLight
    "dark" -> LocalizationManager.Strings.settingsThemeDark
    else -> LocalizationManager.Strings.settingsThemeSystem
}

internal fun folderName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

internal fun proxyKindLabel(kind: ProxyKind): String = when (kind) {
    ProxyKind.NONE -> LocalizationManager.Strings.netProxyOff
    ProxyKind.HTTP -> "HTTP"
    ProxyKind.SOCKS5 -> "SOCKS5"
}

private fun networkSummary(proxy: ProxySettings, logins: Int): String {
    val text = if (proxy.isOn) "${proxyKindLabel(proxy.kind)} ${proxy.host}:${proxy.port}" else LocalizationManager.Strings.netProxyOff
    return if (logins == 0) text else text + " · " + LocalizationManager.Strings.netLoginsCount(logins)
}

private fun Presenter.sectionHint(section: SettingsSection, v: SettingsSlice): Pair<String, ToneCode> {
    val s = LocalizationManager.Strings
    return when (section) {
        SettingsSection.GENERAL -> themeModeLabel(v.themeMode) + " · " + v.unitKey to ToneCode.MUTED
        SettingsSection.DOWNLOADS -> {
            val max = QueueManagerRepository.getInstance().maxConcurrentDownloads.value
            folderName(v.destinationPath) + " · " + (if (max <= 0) s.summaryNoQueueLimit else s.summaryAtATime(max)) to ToneCode.MUTED
        }

        SettingsSection.NETWORK -> {
            val proxy = NetworkSettings.proxy.value
            networkSummary(proxy, NetworkSettings.logins.value.size) to (if (proxy.isOn) ToneCode.COMPLETED else ToneCode.MUTED)
        }

        SettingsSection.MEDIA ->
            if (v.toolsReady) s.settingsReady to ToneCode.COMPLETED else s.summarySetupNeeded to ToneCode.PAUSED

        SettingsSection.TORRENT ->
            (if (v.disableTorrentUpload) s.summaryUploadingOff else s.summaryUploadingOn) to ToneCode.MUTED

        SettingsSection.BROWSER -> "Edge · Firefox · Chrome" to ToneCode.MUTED
        SettingsSection.DEVICES -> {
            val visible = BehaviorSettings.visibleToDevices.value
            (if (visible) s.summaryVisible else s.summaryHidden) +
                (if (v.pairedDevices.isNotEmpty()) " · ${v.pairedDevices.size}" else "") to
                (if (visible) ToneCode.COMPLETED else ToneCode.MUTED)
        }

        SettingsSection.UPDATES -> {
            val update = updateState()
            update.available?.let { s.summaryVersionAvailable(it.version) to ToneCode.COMPLETED }
                ?: (s.summaryVersion(update.currentVersion) to ToneCode.MUTED)
        }
    }
}

internal fun Presenter.settingsPage(change: SettingsPage.() -> SettingsPage) {
    settingsLocal.update { it.copy(page = it.page.change()) }
}

internal fun Presenter.settingsPoke() {
    settingsLocal.update { it.copy(poke = it.poke + 1) }
}

internal fun Presenter.currentSettingsSection(): SettingsSection {
    val sections = visibleSettingsSections()
    return settingsLocal.value.section?.takeIf { it in sections } ?: sections.first()
}

private data class PageKey(val open: Boolean, val section: SettingsSection, val generation: Int)

internal fun Presenter.startSettings() {
    val slices = vm.uiState.map { it.settingsSlice() }.distinctUntilChanged()
    val open = slices.map { it.open }.distinctUntilChanged()
    scope.launch {
        combine(open, AppLanguage.current) { _, _ -> Unit }.drop(1).collect {
            settingsLocal.update { SettingsLocal(generation = it.generation + 1, poke = it.poke + 1) }
        }
    }
    scope.launch {
        combine(open, settingsLocal) { isOpen, ui ->
            val sections = visibleSettingsSections()
            PageKey(isOpen, ui.section?.takeIf { it in sections } ?: sections.first(), ui.generation)
        }.distinctUntilChanged().collectLatest { key ->
            if (key.open) loadSettingsPage(key.section, key.generation)
        }
    }
    val triggers = merge(
        slices.throttleLatest(100).map { },
        settingsLocal.map { },
        UpdateManager.state.map { },
        debugUpdate.map { },
        debugSections.map { },
        QueueManagerRepository.getInstance().maxConcurrentDownloads.map { },
        BehaviorSettings.visibleToDevices.map { },
        NetworkSettings.proxy.map { },
        NetworkSettings.logins.map { },
        SpeedSchedule.enabled.map { },
        SpeedSchedule.windows.map { },
        SpeedSchedule.activeCap.map { },
        WhenDone.action.map { },
        BridgePairing.seenBrowsers.map { },
        BridgePairing.seenVersions.map { },
        BridgePairing.legacyAllowed.map { },
        BridgePairing.blocked.map { },
        AppLanguage.current.map { },
    )
    scope.launch {
        triggers.conflate().collect { publishSettings() }
    }
}

private fun Presenter.samePage(generation: Int): Boolean = settingsLocal.value.generation == generation

private suspend fun Presenter.loadSettingsPage(section: SettingsSection, generation: Int) {
    when (section) {
        SettingsSection.GENERAL -> {
            if (samePage(generation)) refreshDefaultApp()
            if (!StartupLauncher.isSupported && StartupLauncher.managedBySystemUri == null) return
            val hidden = withContext(Dispatchers.IO) { StartupPreferences.startHidden }
            val enabled = if (StartupLauncher.managedBySystemUri == null) StartupLauncher.isEnabled() else false
            if (samePage(generation)) settingsPage { copy(startHidden = hidden, startupEnabled = enabled, startupBusy = false) }
        }

        SettingsSection.BROWSER -> {
            val browsers = withContext(Dispatchers.IO) { BrowserIntegration.browsers() }
            if (!samePage(generation)) return
            settingsPage { copy(browsers = browsers) }
            if (!DesktopApp.automaticDownloads) return
            coroutineScope {
                combine(BridgePairing.seenBrowsers, BridgePairing.seenVersions) { seen, versions -> seen to versions }
                    .collect { (seen, versions) ->
                        for (browser in browsers) {
                            if (!samePage(generation)) return@collect
                            if (browser.id in settingsLocal.value.page.storeAsked) continue
                            if (!browserNeedsStoreLookup(browser, seen.keys, versions)) continue
                            settingsPage { copy(storeAsked = storeAsked + browser.id) }
                            launch(Dispatchers.IO) {
                                val version = ExtensionStore.versionFor(browser.id)
                                val xpi = if (isFirefoxBrowser(browser.id)) ExtensionStore.githubXpi() else null
                                if (samePage(generation)) settingsPage {
                                    copy(
                                        storeVersions = if (version != null) storeVersions + (browser.id to version) else storeVersions,
                                        githubXpi = xpi ?: githubXpi,
                                    )
                                }
                            }
                        }
                    }
            }
        }

        SettingsSection.TORRENT -> {
            val speed = vm.uiState.value.globalUploadSpeedLimitBytes
            if (samePage(generation)) settingsPage { copy(limitUnit = limitUnitOf(this, speed), limitText = limitTextOf(this, speed)) }
        }

        else -> Unit
    }
}

private fun Presenter.publishSettings() {
    val v = vm.uiState.value.settingsSlice()
    if (!v.open) {
        settingsNav.publish(SettingsNavDto())
        settingsRows.publish(emptyList())
        return
    }
    val ui = settingsLocal.value
    val sections = visibleSettingsSections()
    val chosen = ui.section?.takeIf { it in sections }
    val current = chosen ?: sections.first()
    settingsNav.publish(
        SettingsNavDto(
            sections = sections.map { section ->
                val (hint, tone) = sectionHint(section, v)
                SettingsSectionDto(
                    glyph = section.glyph,
                    title = section.title(),
                    hint = hint,
                    hintTone = tone.ordinal,
                    selected = section == current,
                )
            },
            pageOpen = chosen != null,
            current = sections.indexOf(current),
            title = current.title(),
            blurb = current.blurb(),
        )
    )
    settingsRows.publish(buildSettingsRows(current, v, ui.page))
}
