package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.setup.DefaultTorrentHandler
import com.michelle.raouf.fetch_jet.core.setup.MainDownloaderSetup
import com.michelle.raouf.fetch_jet.core.setup.MainDownloaderState
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.LabeledLineDto
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun Presenter.startMainDownloaderSetup() {
    if (headless || !DesktopApp.automaticDownloads) return
    scope.launch {
        delay(1500)
        mainSetupAsk.value = MainDownloaderSetup.stateToAsk()
    }
}

internal fun mainDownloaderDialog(state: MainDownloaderState): ChoiceDialogDto {
    val s = LocalizationManager.Strings
    val steps = buildList {
        if (state.torrentToDo) add(s.mainSetupTorrents)
        if (state.clipboardToDo) add(s.mainSetupClipboard)
        if (state.startupToDo) add(s.mainSetupStartup)
        if (state.keepReadyToDo) add(s.mainSetupKeepReady)
        if (state.extensionToDo) add(s.mainSetupExtension)
    }
    val note = if (state.torrentToDo && DefaultTorrentHandler.opensSystemPage) s.defaultAppSystemPageHint + "\n" + s.mainSetupLater
    else s.mainSetupLater
    return ChoiceDialogDto(
        kind = ChoiceDialogKind.MAIN_DOWNLOADER.ordinal,
        icon = "⚡",
        title = s.mainSetupTitle,
        subtitle = s.mainSetupText,
        lines = steps.map { LabeledLineDto("✓", it, accent = false, strong = true) },
        question = note,
        primaryIcon = "⚡",
        primaryLabel = s.mainSetupAccept,
        cancelLabel = s.homeNotNow,
    )
}

internal fun Presenter.mainSetupAccept() {
    val state = mainSetupAsk.value ?: return
    mainSetupAsk.value = null
    scope.launch {
        val result = MainDownloaderSetup.applyAll(state)
        if (result.openExtensionPage) openSettingsSection(SettingsSection.BROWSER)
        refreshDefaultApp()
    }
}

internal fun Presenter.mainSetupDecline() {
    MainDownloaderSetup.markAsked()
    mainSetupAsk.value = null
}

internal fun Presenter.openSettingsSection(section: SettingsSection) {
    vm.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.SETTINGS))
    val index = visibleSettingsSections().indexOf(section)
    if (index >= 0) settingsOpenSection(index)
}

internal suspend fun Presenter.refreshDefaultApp() {
    if (!MainDownloaderSetup.available) return
    val value = DefaultTorrentHandler.isDefault()
    settingsPage { copy(defaultApp = value) }
}

internal fun Presenter.makeDefaultApp() {
    if (headless || settingsLocal.value.page.defaultAppBusy) return
    settingsPage { copy(defaultAppBusy = true) }
    scope.launch {
        DefaultTorrentHandler.makeDefault()
        refreshDefaultApp()
        settingsPage { copy(defaultAppBusy = false) }
        repeat(60) {
            if (settingsLocal.value.page.defaultApp == true) return@launch
            delay(2000)
            refreshDefaultApp()
        }
    }
}
