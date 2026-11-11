package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.core.browser.AddExtensionResult
import com.michelle.raouf.fetch_jet.core.browser.BrowserInfo
import com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
import com.michelle.raouf.fetch_jet.core.browser.EDGE_EXTENSION_ID
import com.michelle.raouf.fetch_jet.core.browser.ExtensionStore
import com.michelle.raouf.fetch_jet.core.browser.FIREFOX_ADDON_URL
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.ProxyKind
import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import com.michelle.raouf.fetch_jet.core.power.SpeedSchedule
import com.michelle.raouf.fetch_jet.core.power.SpeedWindow
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import com.michelle.raouf.fetch_jet.core.update.UPDATE_REPOSITORY
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EDGE_STORE_PAGE = "https://microsoftedge.microsoft.com/addons/detail/$EDGE_EXTENSION_ID"

internal fun Presenter.settingsOpenSection(index: Int) {
    val target = if (index < 0) null else (visibleSettingsSections().getOrNull(index) ?: return)
    settingsLocal.update {
        if (it.section == target) it
        else SettingsLocal(section = target, generation = it.generation + 1, poke = it.poke + 1)
    }
}

internal fun Presenter.openExternal(target: String) {
    if (headless) return
    scope.launch(Dispatchers.IO) { DesktopOs.openExternal(target) }
}

private fun Presenter.openFolder(path: String) {
    if (headless || path.isBlank()) return
    scope.launch(Dispatchers.IO) { pathResolver.openDirectoryInExplorer(path) }
}

private fun Presenter.echoed(id: String, typed: String, kept: String) {
    val echo = settingsEcho.of(id)
    if (kept == typed) {
        echo.fromUi(typed)
    } else {
        echo.force()
        settingsPoke()
    }
}

private fun Presenter.editProxy(change: ProxyDraft.() -> ProxyDraft) {
    settingsPage { copy(proxy = currentProxyDraft(this).change(), proxyJustSaved = false) }
}

internal fun Presenter.settingToggle(id: String, on: Boolean) {
    when (id) {
        "watchClipboard" -> vm.onEvent(DashboardUiEvent.OnMonitorClipboardChanged(on))
        "clipboardVideoPages" -> vm.onEvent(DashboardUiEvent.OnClipboardVideoPagesChanged(on))
        "startAtLogin" -> changeStartAtLogin(on)
        "startHidden" -> {
            StartupPreferences.startHidden = on
            settingsPage { copy(startHidden = on) }
        }

        "schedule" -> SpeedSchedule.setEnabled(on)
        "sortIntoFolders" -> vm.onEvent(DashboardUiEvent.OnSortIntoFoldersChanged(on))
        "visibleToDevices" -> BehaviorSettings.setVisibleToDevices(on)
        "leech" -> vm.onEvent(DashboardUiEvent.OnDisableTorrentUploadToggled(on))
        "autoCheck" -> UpdateManager.setAutoCheck(on)
        "autoInstall" -> UpdateManager.setAutoInstall(on)
    }
}

private fun Presenter.changeStartAtLogin(want: Boolean) {
    val generation = settingsLocal.value.generation
    settingsPage { copy(startupBusy = true, startupError = null) }
    scope.launch {
        val failure = StartupLauncher.setEnabled(want).exceptionOrNull()
        val enabled = StartupLauncher.isEnabled()
        if (settingsLocal.value.generation == generation) settingsPage {
            copy(
                startupEnabled = enabled,
                startupBusy = false,
                startupError = failure?.let { LocalizationManager.Strings.startupCouldNotChange(it.message.toString()) },
            )
        }
    }
}

private fun isPresetId(id: String): Boolean = id.startsWith("preset.") || (id.startsWith("window.") && id.endsWith(".cap"))

private fun isTimeId(id: String): Boolean = id.startsWith("window.") && (id.endsWith(".from") || id.endsWith(".to"))

private fun windowIndex(id: String): Int? = id.split('.').getOrNull(1)?.toIntOrNull()

private fun Presenter.presetModel(id: String): Pair<Long, String>? {
    val state = vm.uiState.value
    return when (id) {
        "preset.fast" -> state.speedPresetFastBytes to state.speedPresetFastUnit
        "preset.medium" -> state.speedPresetMediumBytes to state.speedPresetMediumUnit
        "preset.low" -> state.speedPresetLowBytes to state.speedPresetLowUnit
        else -> windowIndex(id)?.let { SpeedSchedule.windows.value.getOrNull(it) }?.let { it.capBytes to it.capUnit }
    }
}

private fun Presenter.presetChanged(id: String, bytes: Long, unit: String) {
    when (id) {
        "preset.fast" -> vm.onEvent(DashboardUiEvent.OnSpeedPresetChanged(SpeedPreset.FAST, bytes, unit))
        "preset.medium" -> vm.onEvent(DashboardUiEvent.OnSpeedPresetChanged(SpeedPreset.MEDIUM, bytes, unit))
        "preset.low" -> vm.onEvent(DashboardUiEvent.OnSpeedPresetChanged(SpeedPreset.LOW, bytes, unit))
        else -> {
            val index = windowIndex(id) ?: return
            val window = SpeedSchedule.windows.value.getOrNull(index) ?: return
            SpeedSchedule.updateWindow(index, window.copy(capBytes = bytes, capUnit = unit))
        }
    }
}

private fun Presenter.presetUnitPicked(id: String, unit: String) {
    val (bytes, modelUnit) = presetModel(id) ?: return
    val text = presetTextOf(settingsLocal.value.page, id, bytes, modelUnit)
    settingsPage { copy(presetText = presetText + (id to text), presetUnit = presetUnit + (id to unit)) }
    presetChanged(id, speedPresetToBytes(text.toDoubleOrNull() ?: 0.0, unit), unit)
}

private fun Presenter.presetTyped(id: String, typed: String) {
    val (_, modelUnit) = presetModel(id) ?: return
    val unit = presetUnitOf(settingsLocal.value.page, id, modelUnit)
    val kept = typed.filter { it.isDigit() || it == '.' }
    echoed(id, typed, kept)
    settingsPage { copy(presetText = presetText + (id to kept), presetUnit = presetUnit + (id to unit)) }
    presetChanged(id, speedPresetToBytes(kept.toDoubleOrNull() ?: 0.0, unit), unit)
}

private fun windowMinutes(id: String): Int? {
    val window = windowIndex(id)?.let { SpeedSchedule.windows.value.getOrNull(it) } ?: return null
    return if (id.endsWith(".from")) window.startMinutes else window.endMinutes
}

private fun timeChanged(id: String, minutes: Int) {
    val index = windowIndex(id) ?: return
    val window = SpeedSchedule.windows.value.getOrNull(index) ?: return
    SpeedSchedule.updateWindow(
        index,
        if (id.endsWith(".from")) window.copy(startMinutes = minutes) else window.copy(endMinutes = minutes),
    )
}

private fun Presenter.timeTyped(id: String, typed: String) {
    val minutes = windowMinutes(id) ?: return
    val pm = timePmOf(settingsLocal.value.page, id, minutes)
    val kept = typed.filter { it.isDigit() || it == ':' }.take(5)
    echoed(id, typed, kept)
    settingsPage { copy(timeText = timeText + (id to kept), timePm = timePm + (id to pm)) }
    parseHm12(kept, pm)?.let { timeChanged(id, it) }
}

private fun Presenter.timeToggled(id: String) {
    val minutes = windowMinutes(id) ?: return
    val page = settingsLocal.value.page
    val text = timeTextOf(page, id, minutes)
    val next = !timePmOf(page, id, minutes)
    settingsPage { copy(timeText = timeText + (id to text), timePm = timePm + (id to next)) }
    parseHm12(text, next)?.let { timeChanged(id, it) }
}

internal fun Presenter.settingPick(id: String, index: Int) {
    when {
        id == "language" -> {
            val code = languageCodes().getOrNull(index) ?: return
            if (code != AppLanguage.choice) vm.onEvent(DashboardUiEvent.OnLanguageChanged(code))
        }

        id == "theme" -> {
            val mode = THEME_MODES.getOrNull(index) ?: return
            if (mode != vm.uiState.value.themeMode) vm.onEvent(DashboardUiEvent.OnThemeModeChanged(mode))
        }

        id == "speedUnit" -> SPEED_UNITS.getOrNull(index)?.let { vm.onEvent(DashboardUiEvent.OnSpeedDisplayUnitChanged(it)) }
        id == "whenDone" -> WhenDoneAction.entries.getOrNull(index)?.let { WhenDone.choose(it) }
        id == "connections" ->
            CONNECTION_COUNTS.getOrNull(index)?.let { vm.onEvent(DashboardUiEvent.OnDefaultConnectionsChanged(it)) }

        id == "queue" -> QUEUE_COUNTS.getOrNull(index)?.let { QueueManagerRepository.getInstance().setMaxConcurrentDownloads(it) }
        id == "proxyKind" -> ProxyKind.entries.getOrNull(index)?.let { kind -> editProxy { copy(kind = kind) } }
        id == "uploadLimit" -> uploadLimitPicked(index)
        id == "uploadLimit.custom" -> speedPresetUnits.getOrNull(index)?.let { uploadLimitUnit(it) }
        isPresetId(id) -> speedPresetUnits.getOrNull(index)?.let { presetUnitPicked(id, it) }
    }
}

internal fun Presenter.settingText(id: String, text: String) {
    when {
        id == "destination" -> {
            settingsEcho.of(id).fromUi(text)
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(text))
        }

        id == "proxyHost" -> {
            settingsEcho.of(id).fromUi(text)
            editProxy { copy(host = text) }
        }

        id == "proxyPort" -> {
            val digits = text.filter { it.isDigit() }.take(5)
            echoed(id, text, digits)
            editProxy { copy(port = digits) }
        }

        id == "proxyUser" -> {
            settingsEcho.of(id).fromUi(text)
            editProxy { copy(user = text) }
        }

        id == "proxyPassword" -> {
            settingsEcho.of(id).fromUi(text)
            editProxy { copy(password = text) }
        }

        id == "loginSite" -> {
            settingsEcho.of(id).fromUi(text)
            settingsPage { copy(loginSite = text) }
        }

        id == "loginUser" -> {
            settingsEcho.of(id).fromUi(text)
            settingsPage { copy(loginUser = text) }
        }

        id == "loginPassword" -> {
            settingsEcho.of(id).fromUi(text)
            settingsPage { copy(loginPassword = text) }
        }

        id == "uploadLimit.custom" -> uploadLimitTyped(text)
        isPresetId(id) -> presetTyped(id, text)
        isTimeId(id) -> timeTyped(id, text)
    }
}

private fun Presenter.uploadLimit(): Long = vm.uiState.value.globalUploadSpeedLimitBytes

private fun Presenter.uploadLimitPicked(index: Int) {
    val speed = uploadLimit()
    val page = settingsLocal.value.page
    if (index in UPLOAD_PRESETS.indices) {
        settingsPage { copy(limitCustomAt = null, limitUnit = limitUnitOf(page, speed), limitText = limitTextOf(page, speed)) }
        vm.onEvent(DashboardUiEvent.OnGlobalUploadSpeedLimitChanged(UPLOAD_PRESETS[index]))
        return
    }
    if (index != UPLOAD_PRESETS.size) return
    val unit = limitUnitOf(page, speed)
    val text = limitTextOf(page, speed)
    if (text.isBlank()) {
        val wanted = limitBytes(2.0, unit)
        settingsEcho.of("uploadLimit.custom").force()
        settingsPage { copy(limitUnit = unit, limitText = "2", limitCustomAt = null) }
        vm.onEvent(DashboardUiEvent.OnGlobalUploadSpeedLimitChanged(wanted))
    } else {
        settingsPage { copy(limitUnit = unit, limitText = text, limitCustomAt = speed) }
    }
}

private fun Presenter.uploadLimitUnit(unit: String) {
    val speed = uploadLimit()
    val text = limitTextOf(settingsLocal.value.page, speed)
    val wanted = limitBytes(text.toDoubleOrNull() ?: 0.0, unit)
    settingsPage { copy(limitUnit = unit, limitText = text, limitCustomAt = if (wanted == speed) limitCustomAt else null) }
    vm.onEvent(DashboardUiEvent.OnGlobalUploadSpeedLimitChanged(wanted))
}

private fun Presenter.uploadLimitTyped(typed: String) {
    val speed = uploadLimit()
    val unit = limitUnitOf(settingsLocal.value.page, speed)
    val kept = typed.filter { it.isDigit() || it == '.' }
    echoed("uploadLimit.custom", typed, kept)
    val wanted = limitBytes(kept.toDoubleOrNull() ?: 0.0, unit)
    settingsPage { copy(limitUnit = unit, limitText = kept, limitCustomAt = if (wanted == speed) limitCustomAt else null) }
    vm.onEvent(DashboardUiEvent.OnGlobalUploadSpeedLimitChanged(wanted))
}

private fun Presenter.chooseSaveFolder() {
    val pick = HostHooks.pickFolder ?: return
    val current = vm.uiState.value.destinationPath
    scope.launch(Dispatchers.IO) {
        val chosen = try {
            pick("Select Download Directory", current)
        } catch (_: Throwable) {
            null
        }
        if (!chosen.isNullOrBlank()) withContext(Dispatchers.Main) {
            settingsEcho.of("destination").force()
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(chosen))
        }
    }
}

private fun Presenter.removeWindow(index: Int) {
    SpeedSchedule.removeWindow(index)
    settingsEcho.forceAll("window.")
    settingsLocal.update { ui ->
        ui.copy(
            poke = ui.poke + 1,
            page = ui.page.copy(
                presetText = ui.page.presetText.filterKeys { !it.startsWith("window.") },
                presetUnit = ui.page.presetUnit.filterKeys { !it.startsWith("window.") },
                timeText = emptyMap(),
                timePm = emptyMap(),
            ),
        )
    }
}

private fun Presenter.saveProxy() {
    val saved = NetworkSettings.proxy.value
    val draft = currentProxyDraft(settingsLocal.value.page)
    val wanted = draft.settings()
    val invalid = draft.kind != ProxyKind.NONE && !wanted.isOn
    if (wanted == saved || invalid) return
    NetworkSettings.setProxy(
        if (draft.kind == ProxyKind.NONE) saved.copy(kind = ProxyKind.NONE)
        else wanted.copy(password = if (wanted.user.isEmpty()) "" else wanted.password)
    )
    settingsEcho.forceAll("proxy")
    settingsPage { copy(proxy = null, proxyJustSaved = true) }
}

private fun Presenter.addLogin() {
    val page = settingsLocal.value.page
    if (!SiteLogin.cleanSite(page.loginSite).contains('.') || page.loginUser.isBlank()) return
    NetworkSettings.saveLogin(SiteLogin(page.loginSite, page.loginUser, page.loginPassword))
    settingsEcho.forceAll("login")
    settingsPage { copy(loginSite = "", loginUser = "", loginPassword = "") }
}

internal fun Presenter.showBrowserDialog(dialog: BrowserDialog?) {
    settingsLocal.update { it.copy(browserDialog = dialog) }
}

private fun Presenter.handleExtensionResult(browser: BrowserInfo, result: AddExtensionResult) {
    when (result) {
        AddExtensionResult.NotInstalled -> showBrowserDialog(BrowserDialog.Missing(browser))
        is AddExtensionResult.LoadUnpacked -> {
            DesktopOs.setClipboardText(result.folder)
            showBrowserDialog(BrowserDialog.Steps(browser, result.folder, result.page))
        }

        is AddExtensionResult.InstallFromFile -> showBrowserDialog(BrowserDialog.FromFile(browser, result.fileName))
        is AddExtensionResult.Failed -> showBrowserDialog(BrowserDialog.Failure(result.reason))
        AddExtensionResult.Opened -> Unit
    }
}

private fun Presenter.browserClicked(id: String) {
    val ui = settingsLocal.value
    if (ui.page.browserBusy != null) return
    val browser = ui.page.browsers.firstOrNull { it.id == id } ?: return
    val generation = ui.generation
    when (browserPlan(browser, ui.page).step) {
        BrowserStep.MISSING -> showBrowserDialog(BrowserDialog.Missing(browser))
        BrowserStep.FROM_GITHUB -> {
            val xpi = ui.page.githubXpi ?: return
            if (headless) return
            settingsPage { copy(browserBusy = id) }
            scope.launch(Dispatchers.IO) {
                val bytes = ExtensionStore.download(xpi)
                if (settingsLocal.value.generation == generation) settingsPage { copy(browserBusy = null) }
                if (bytes == null) showBrowserDialog(BrowserDialog.Failure(LocalizationManager.Strings.extensionDownloadFailed))
                else handleExtensionResult(browser, BrowserIntegration.installXpi(id, bytes, xpi.fileName))
            }
        }

        BrowserStep.OPEN_STORE -> openExternal(if (id == "edge") EDGE_STORE_PAGE else FIREFOX_ADDON_URL)
        BrowserStep.ADD -> {
            if (headless) return
            scope.launch(Dispatchers.IO) { handleExtensionResult(browser, BrowserIntegration.addExtension(id)) }
        }
    }
}

internal fun Presenter.browserDialogConfirm() {
    when (val dialog = settingsLocal.value.browserDialog) {
        is BrowserDialog.Missing -> {
            openExternal(dialog.browser.downloadUrl)
            showBrowserDialog(null)
        }

        is BrowserDialog.FromFile -> {
            if (!headless) scope.launch(Dispatchers.IO) { BrowserIntegration.openBrowser(dialog.browser.id) }
            showBrowserDialog(null)
        }

        is BrowserDialog.Steps -> if (!headless) DesktopOs.setClipboardText(dialog.folder)
        is BrowserDialog.Failure -> showBrowserDialog(null)
        null -> Unit
    }
}

internal fun Presenter.browserDialogExtra() {
    val dialog = settingsLocal.value.browserDialog as? BrowserDialog.Steps ?: return
    if (!headless) scope.launch(Dispatchers.IO) { BrowserIntegration.openFolder(dialog.folder) }
}

internal fun Presenter.settingClick(id: String) {
    when {
        id == "chooseFolder" -> chooseSaveFolder()
        id == "openFolder" -> openFolder(vm.uiState.value.destinationPath)
        id == "useDownloads" -> {
            settingsEcho.of("destination").force()
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(pathResolver.getDownloadsDirectory()))
        }

        id == "addWindow" -> SpeedSchedule.addWindow(
            SpeedWindow(startMinutes = 8 * 60, endMinutes = 18 * 60, capBytes = 1L * 1024 * 1024, capUnit = "MB/s")
        )

        id.startsWith("window.") && id.endsWith(".remove") -> windowIndex(id)?.let { removeWindow(it) }
        id.startsWith("window.") && id.endsWith(".pm") -> timeToggled(id.removeSuffix(".pm"))
        id == "proxySave" -> saveProxy()
        id == "loginAdd" -> addLogin()
        id.startsWith("login.remove.") -> NetworkSettings.removeLogin(id.removePrefix("login.remove."))
        id == "toolsFolder" -> openFolder(vm.uiState.value.toolsDirectoryPath)
        id == "tools" -> vm.onEvent(DashboardUiEvent.OnDownloadYtDlpClicked)
        id == "updateSee" -> updateShow()
        id == "updateCheck" -> UpdateManager.checkNow()
        id == "releaseNotes" -> openExternal("https://github.com/$UPDATE_REPOSITORY/releases")
        id == "defaultApp" -> makeDefaultApp()
        id == "openBrowserPage" -> openSettingsSection(SettingsSection.BROWSER)
        id == "openStartupSettings" -> StartupLauncher.managedBySystemUri?.let { openExternal(it) }
        id.startsWith("browser.") -> browserClicked(id.removePrefix("browser."))
        id.startsWith("revoke.") -> BridgePairing.revokeAllowed(id.removePrefix("revoke."))
        id.startsWith("allowAgain.") -> BridgePairing.unblock(id.removePrefix("allowAgain."))
        id.startsWith("unpair.") -> vm.onEvent(DashboardUiEvent.OnUnpairDeviceClicked(id.removePrefix("unpair.")))
        id.startsWith("unblock.") -> vm.onEvent(DashboardUiEvent.OnUnblockDeviceClicked(id.removePrefix("unblock.")))
    }
}
