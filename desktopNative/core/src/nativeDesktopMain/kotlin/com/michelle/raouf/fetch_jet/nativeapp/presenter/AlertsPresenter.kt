package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.nativeapp.dto.AlertDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AlertKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class VmAlerts(
    val removeId: String?,
    val removeIds: List<String>,
    val removeDerived: Int = 0,
    val clipboardUrl: String?,
    val fallbackUrl: String?,
    val missingName: String?,
    val restartIsClip: Boolean?,
    val password: com.michelle.raouf.fetch_jet.presentation.dashboard.ArchivePasswordAsk?,
    val notice: Boolean = false,
    val recovery: String? = null,
)

private data class OtherAlerts(
    val pairing: BridgePairing.Request?,
    val secondsLeft: Int?,
    val pending: WhenDoneAction,
    val error: String?,
    val language: String,
)

internal fun Presenter.startAlerts() {
    val fromVm = combine(vm.uiState, local) { state, ui ->
        val missing = ui.missingFileTaskId?.let { id -> state.findTask(id) }
        val restart = ui.restartPauseTaskId?.let { id -> state.findTask(id) }
        VmAlerts(
            removeId = state.removeConfirmationTaskId,
            removeIds = state.removeConfirmationTaskIds,
            removeDerived = state.removeDerivedCount,
            clipboardUrl = state.clipboardOfferUrl,
            fallbackUrl = state.browserFallbackUrl,
            missingName = missing?.fileName,
            restartIsClip = restart?.let { it.engineType == EngineType.MEDIA_STREAM },
            password = state.archivePassword,
            notice = state.hidden.encryptNoticeNeeded,
            recovery = state.hidden.recoveryCode,
        )
    }.distinctUntilChanged()
    val others = combine(
        BridgePairing.pending,
        WhenDone.secondsLeft,
        WhenDone.pending,
        WhenDone.error,
        AppLanguage.current,
    ) { pairing, seconds, pending, error, language -> OtherAlerts(pairing, seconds, pending, error, language) }
    val dialogs = settingsLocal.map { it.browserDialog }.distinctUntilChanged()
    scope.launch {
        combine(fromVm, others, dialogs, hiddenAlertFlow()) { a, b, dialog, pin -> buildAlerts(a, b, dialog, pin) }.collect { alerts.publish(it) }
    }
}

private fun browserAlert(dialog: BrowserDialog): AlertDto {
    val strings = LocalizationManager.Strings
    return when (dialog) {
        is BrowserDialog.Missing -> AlertDto(
            kind = AlertKind.EXTENSION_MISSING.ordinal,
            title = strings.browserMissingTitle(dialog.browser.name),
            text = strings.browserMissingMessage(dialog.browser.name),
            confirmLabel = strings.downloadBrowser(dialog.browser.name),
            dismissLabel = strings.close,
            round = true,
        )

        is BrowserDialog.Steps -> AlertDto(
            kind = AlertKind.EXTENSION_STEPS.ordinal,
            title = strings.loadUnpackedTitle(dialog.browser.name),
            text = strings.loadUnpackedSteps(dialog.browser.name, dialog.page),
            mono = dialog.folder,
            hint = strings.loadUnpackedUpdates,
            confirmLabel = strings.copyPath,
            extraLabel = strings.detailsOpenFolder,
            dismissLabel = strings.close,
            flatDismiss = true,
            round = true,
        )

        is BrowserDialog.FromFile -> AlertDto(
            kind = AlertKind.EXTENSION_FROM_FILE.ordinal,
            title = strings.installFromFileTitle(dialog.browser.name),
            text = strings.installFromFileSteps(dialog.browser.name, dialog.fileName),
            confirmLabel = strings.openBrowser(dialog.browser.name),
            dismissLabel = strings.close,
            round = true,
        )

        is BrowserDialog.Failure -> AlertDto(
            kind = AlertKind.EXTENSION_FAILED.ordinal,
            title = strings.addExtension,
            text = dialog.reason,
            confirmLabel = strings.close,
            round = true,
        )
    }
}

private fun buildAlerts(a: VmAlerts, b: OtherAlerts, dialog: BrowserDialog?, pin: AlertDto?): List<AlertDto> {
    val strings = LocalizationManager.Strings
    val list = ArrayList<AlertDto>(2)
    if (a.removeId != null || a.removeIds.isNotEmpty()) {
        val count = if (a.removeId != null) 1 else a.removeIds.size
        list += AlertDto(
            kind = AlertKind.REMOVE.ordinal,
            title = strings.removeDialogTitle(count),
            titleSmall = true,
            text = strings.removeDialogMessage(count),
            hint = com.michelle.raouf.fetch_jet.presentation.dashboard.removeDerivedText(a.removeDerived),
            checkLabel = strings.removeDialogAlsoDeleteFiles,
            confirmLabel = strings.remove,
            confirmDanger = true,
            dismissLabel = strings.cancel,
        )
    }
    if (a.missingName != null) {
        list += AlertDto(
            kind = AlertKind.MISSING_FILE.ordinal,
            title = strings.fileMissingTitle,
            titleSmall = true,
            text = strings.fileMissingMessage(a.missingName),
            confirmLabel = strings.detailsRemoveFromList,
            confirmDanger = true,
            dismissLabel = strings.close,
        )
    }
    val seconds = b.secondsLeft
    if (seconds != null) {
        list += AlertDto(
            kind = AlertKind.WHEN_DONE_COUNTDOWN.ordinal,
            title = strings.whenDoneTitle,
            text = when (b.pending) {
                WhenDoneAction.SLEEP -> strings.whenDoneSleepIn(seconds)
                WhenDoneAction.SHUT_DOWN -> strings.whenDoneShutDownIn(seconds)
                else -> strings.whenDoneExitIn(seconds)
            },
            progressShown = true,
            progress = (seconds / WhenDone.COUNTDOWN_SECONDS.toFloat()).coerceIn(0f, 1f),
            confirmLabel = strings.cancel,
            dismissLabel = strings.whenDoneNow,
            locked = true,
        )
    }
    val error = b.error
    if (error != null) {
        list += AlertDto(
            kind = AlertKind.WHEN_DONE_ERROR.ordinal,
            title = strings.whenDoneGroup,
            text = strings.whenDoneFailed(error),
            confirmLabel = strings.ok,
        )
    }
    val clipboardUrl = a.clipboardUrl
    if (clipboardUrl != null) {
        val name = if (clipboardUrl.startsWith("magnet:", ignoreCase = true)) strings.homeMagnetLink
        else com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(clipboardUrl).let { shown ->
            shown.substringBefore('?').substringAfterLast('/').ifBlank { shown }
        }
        list += AlertDto(
            kind = AlertKind.CLIPBOARD_OFFER.ordinal,
            title = strings.homeClipboardOfferTitle,
            strong = name,
            detail = com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(clipboardUrl),
            hint = strings.homeClipboardOfferHint,
            confirmLabel = strings.homeDownload,
            dismissLabel = strings.homeNotNow,
        )
    }
    val pairing = b.pairing
    if (pairing != null) {
        list += AlertDto(
            kind = AlertKind.BRIDGE_PAIRING.ordinal,
            title = strings.bridgePairTitle,
            text = if (pairing.legacy) strings.bridgePairOldText else strings.bridgePairText,
            detail = pairing.origin,
            confirmLabel = strings.bridgePairBlock,
            dismissLabel = strings.bridgePairAllow,
        )
    }
    val fallbackUrl = a.fallbackUrl
    if (fallbackUrl != null) {
        list += AlertDto(
            kind = AlertKind.BROWSER_FALLBACK.ordinal,
            title = strings.homeBrowserFallbackTitle,
            text = strings.homeBrowserFallbackText,
            detail = fallbackUrl.substringAfterLast('/').substringBefore('?').ifBlank { fallbackUrl },
            confirmLabel = strings.homeDownloadInBrowser,
            dismissLabel = strings.homeNoDiscard,
        )
    }
    val restartIsClip = a.restartIsClip
    if (restartIsClip != null) {
        list += AlertDto(
            kind = AlertKind.RESTART_PAUSE.ordinal,
            title = strings.homeCantResumeTitle,
            text = if (restartIsClip) strings.homeCantResumeClipText else strings.homeCantResumeServerText,
            confirmLabel = strings.homePauseAnyway,
            dismissLabel = strings.homeKeepDownloading,
        )
    }
    a.password?.let { ask ->
        list += AlertDto(
            kind = AlertKind.ARCHIVE_PASSWORD.ordinal,
            title = strings.archivePasswordTitle,
            titleSmall = true,
            text = ask.name,
            hint = if (ask.wrong) strings.archivePasswordWrong else strings.archivePasswordHint,
            inputLabel = strings.archivePasswordLabel,
            inputSecret = true,
            confirmLabel = strings.rowMenuExtract,
            dismissLabel = strings.cancel,
        )
    }
    if (a.notice) {
        list += AlertDto(
            kind = AlertKind.HIDDEN_NOTICE.ordinal,
            title = strings.hiddenNoticeTitle,
            titleSmall = true,
            text = strings.hiddenNoticeBody,
            confirmLabel = strings.hiddenNoticeAccept,
            dismissLabel = strings.cancel,
        )
    }
    if (dialog != null) list += browserAlert(dialog)
    if (pin != null) list += pin
    a.recovery?.let { list += hiddenRecoveryAlert(it) }
    return list
}

private var alertTyped = HashMap<Int, String>()

internal fun Presenter.clearTyped(kind: Int) {
    alertTyped.remove(kind)
}

internal fun Presenter.alertText(kind: Int, text: String) {
    alertTyped[kind] = text
}

internal fun Presenter.alertExtra(kind: Int) {
    when (AlertKind.entries.getOrNull(kind)) {
        AlertKind.EXTENSION_STEPS -> browserDialogExtra()
        AlertKind.HIDDEN_PIN -> hiddenSwitchType()
        else -> Unit
    }
}

internal fun Presenter.alertLink(kind: Int) {
    if (AlertKind.entries.getOrNull(kind) == AlertKind.HIDDEN_PIN) hiddenLink()
}

internal fun Presenter.alertConfirm(kind: Int, checked: Boolean) {
    when (AlertKind.entries.getOrNull(kind)) {
        AlertKind.REMOVE -> vm.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(checked))
        AlertKind.MISSING_FILE -> {
            val id = local.value.missingFileTaskId
            local.update { it.copy(missingFileTaskId = null) }
            if (id != null) {
                vm.onEvent(DashboardUiEvent.OnRemoveClicked(id))
                vm.onEvent(DashboardUiEvent.OnConfirmRemoveClicked(false))
            }
        }

        AlertKind.WHEN_DONE_COUNTDOWN -> WhenDone.cancel()
        AlertKind.WHEN_DONE_ERROR -> WhenDone.dismissError()
        AlertKind.CLIPBOARD_OFFER -> vm.onEvent(DashboardUiEvent.OnClipboardOfferAccepted)
        AlertKind.BRIDGE_PAIRING -> BridgePairing.block()
        AlertKind.BROWSER_FALLBACK -> vm.onEvent(DashboardUiEvent.OnConfirmBrowserFallback)
        AlertKind.RESTART_PAUSE -> {
            val id = local.value.restartPauseTaskId
            local.update { it.copy(restartPauseTaskId = null) }
            if (id != null) vm.onEvent(DashboardUiEvent.OnPauseClicked(id))
        }

        AlertKind.EXTENSION_MISSING, AlertKind.EXTENSION_STEPS, AlertKind.EXTENSION_FROM_FILE, AlertKind.EXTENSION_FAILED ->
            browserDialogConfirm()

        AlertKind.ARCHIVE_PASSWORD -> {
            val ask = vm.uiState.value.archivePassword ?: return
            val typed = alertTyped.remove(kind).orEmpty()
            if (typed.isEmpty()) return
            vm.onEvent(DashboardUiEvent.OnArchivePasswordEntered(ask.taskId, typed))
        }

        AlertKind.HIDDEN_PIN -> hiddenConfirm(alertTyped.remove(kind).orEmpty())
        AlertKind.HIDDEN_NOTICE -> hiddenNoticeAccept()
        AlertKind.HIDDEN_RECOVERY -> hiddenRecoveryCopy()

        AlertKind.NONE, null -> Unit
    }
}

internal fun Presenter.alertDismiss(kind: Int, fromScrim: Boolean) {
    when (AlertKind.entries.getOrNull(kind)) {
        AlertKind.REMOVE -> vm.onEvent(DashboardUiEvent.OnDismissRemoveDialog)
        AlertKind.MISSING_FILE -> local.update { it.copy(missingFileTaskId = null) }
        AlertKind.WHEN_DONE_COUNTDOWN -> if (!fromScrim) WhenDone.runNow()
        AlertKind.WHEN_DONE_ERROR -> WhenDone.dismissError()
        AlertKind.CLIPBOARD_OFFER -> vm.onEvent(DashboardUiEvent.OnClipboardOfferDismissed)
        AlertKind.BRIDGE_PAIRING -> BridgePairing.answer(!fromScrim)
        AlertKind.BROWSER_FALLBACK -> vm.onEvent(DashboardUiEvent.OnDismissBrowserFallback)
        AlertKind.RESTART_PAUSE -> local.update { it.copy(restartPauseTaskId = null) }
        AlertKind.EXTENSION_MISSING, AlertKind.EXTENSION_STEPS, AlertKind.EXTENSION_FROM_FILE, AlertKind.EXTENSION_FAILED ->
            showBrowserDialog(null)

        AlertKind.ARCHIVE_PASSWORD -> {
            alertTyped.remove(kind)
            vm.onEvent(DashboardUiEvent.OnArchivePasswordDismissed)
        }

        AlertKind.HIDDEN_PIN -> hiddenDismiss()
        AlertKind.HIDDEN_NOTICE -> hiddenNoticeDismiss()
        AlertKind.HIDDEN_RECOVERY -> hiddenRecoveryClose()

        AlertKind.NONE, null -> Unit
    }
}
