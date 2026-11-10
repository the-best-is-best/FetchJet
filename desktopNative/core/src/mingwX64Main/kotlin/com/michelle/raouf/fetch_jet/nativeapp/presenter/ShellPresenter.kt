package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.notification.DesktopTaskProgress
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager
import com.michelle.raouf.fetch_jet.core.power.SystemPower
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.nativeapp.dto.ShellDto
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

internal fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMs)
}

internal fun resolveDark(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

internal fun whenDoneLabel(action: WhenDoneAction): String = when (action) {
    WhenDoneAction.NOTHING -> LocalizationManager.Strings.whenDoneNothing
    WhenDoneAction.EXIT -> LocalizationManager.Strings.whenDoneExit
    WhenDoneAction.SLEEP -> LocalizationManager.Strings.whenDoneSleep
    WhenDoneAction.SHUT_DOWN -> LocalizationManager.Strings.whenDoneShutDown
}

private fun trayTooltip(active: Map<String, DesktopTaskProgress>): String {
    val strings = LocalizationManager.Strings
    if (active.isEmpty()) return strings.desktopTrayIdleTooltip
    val lines = active.values.take(6).map { task ->
        val cleanName = if (task.fileName.length > 25) task.fileName.take(22) + "..." else task.fileName
        "$cleanName: ${task.progressPercent}%"
    }
    val extra = if (active.size > 6) "\n" + strings.desktopTrayMore(active.size - 6) else ""
    return strings.desktopTrayDownloadsHeader + "\n" + lines.joinToString("\n") + extra
}

internal fun Presenter.startShell() {
    val look = combine(
        vm.uiState.map { it.themeMode }.distinctUntilChanged(),
        local.map { it.systemDark }.distinctUntilChanged(),
        AppLanguage.current,
    ) { themeMode, systemDark, _ -> resolveDark(themeMode, systemDark) }
    scope.launch {
        combine(
            look,
            toast,
            WhenDone.action,
            DownloadNotificationManager.activeTasks.throttleLatest(1000),
        ) { dark, toastState, whenDone, active ->
            val strings = LocalizationManager.Strings
            ShellDto(
                windowTitle = "FetchJet - " + strings.desktopWindowTagline,
                dark = dark,
                trayTooltip = trayTooltip(active),
                trayShowLabel = strings.desktopTrayShowApp,
                trayCloseLabel = strings.desktopTrayCloseApp,
                whenDoneSupported = SystemPower.isSupported,
                whenDoneLabel = strings.whenDoneGroup,
                whenDoneOptions = WhenDoneAction.entries.map { whenDoneLabel(it) },
                whenDoneIndex = whenDone.ordinal,
                toastText = toastState.text,
                toastShown = toastState.shown,
            )
        }.collect { shell.publish(it) }
    }
}
