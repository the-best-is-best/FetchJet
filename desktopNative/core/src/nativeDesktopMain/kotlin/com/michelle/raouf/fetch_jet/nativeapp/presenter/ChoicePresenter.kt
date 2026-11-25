package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.nativeapp.dto.BadgeToneCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.LabeledLineDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun Presenter.startChoices() {
    val source = vm.uiState.map { it.duplicateTaskAlert to it.fileConflictAlert }.distinctUntilChanged()
    scope.launch {
        combine(source, AppLanguage.current, mainSetupAsk, playerAsk) { pair, _, setup, player -> listOf(pair.first, pair.second, setup, player) }.collect { values ->
            val duplicate = values[0] as com.michelle.raouf.fetch_jet.domain.model.DownloadTask?
            val conflict = values[1] as com.michelle.raouf.fetch_jet.presentation.dashboard.FileConflictData?
            val setup = values[2] as com.michelle.raouf.fetch_jet.core.setup.MainDownloaderState?
            val player = values[3] as PlayerAsk?
            val strings = LocalizationManager.Strings
            val list = ArrayList<ChoiceDialogDto>(3)
            if (duplicate != null) {
                val name = duplicate.fileName.ifBlank { com.michelle.raouf.fetch_jet.feature.http_downloader.displayUrl(duplicate.url) }
                list += ChoiceDialogDto(
                    kind = ChoiceDialogKind.DUPLICATE.ordinal,
                    icon = "🔄",
                    title = strings.duplicateDialogTitle,
                    subtitle = strings.duplicateDialogSubtitle,
                    name = name,
                    nameRtl = startsRightToLeft(name),
                    badge = strings.duplicateDialogStatus(duplicate.status.name),
                    badgeTone = when (duplicate.status) {
                        DownloadStatus.COMPLETED -> BadgeToneCode.COMPLETED
                        DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> BadgeToneCode.RUNNING
                        DownloadStatus.PAUSED -> BadgeToneCode.PAUSED
                        DownloadStatus.ERROR -> BadgeToneCode.ERROR
                        else -> BadgeToneCode.OTHER
                    }.ordinal,
                    percent = if (duplicate.progress > 0f) "${(duplicate.progress * 100).toInt()}%" else "",
                    barShown = duplicate.progress > 0f && duplicate.status != DownloadStatus.COMPLETED,
                    progress = duplicate.progress.coerceIn(0f, 1f),
                    question = strings.duplicateDialogQuestion,
                    primaryIcon = "▶",
                    primaryLabel = strings.duplicateDialogResumeExisting,
                    secondaryIcon = "🔄",
                    secondaryLabel = strings.duplicateDialogRedownload,
                    cancelLabel = strings.cancel,
                )
            }
            if (conflict != null) {
                list += ChoiceDialogDto(
                    kind = ChoiceDialogKind.FILE_CONFLICT.ordinal,
                    icon = "⚠️",
                    danger = true,
                    title = strings.overwriteDialogTitle,
                    subtitle = strings.overwriteDialogSubtitle,
                    lines = listOf(
                        LabeledLineDto(strings.overwriteDialogFileNameLabel, conflict.fileName, strong = true),
                        LabeledLineDto(strings.overwriteDialogFolderLabel, conflict.destinationPath),
                        LabeledLineDto(strings.overwriteDialogNewCopyLabel, conflict.suggestedNewName, accent = true, strong = true),
                    ),
                    question = strings.overwriteDialogQuestion,
                    primaryIcon = "📝",
                    primaryLabel = strings.overwriteDialogSaveAsCopy(conflict.suggestedNewName),
                    secondaryIcon = "⚠️",
                    secondaryLabel = strings.overwriteDialogOverwriteExisting,
                    secondaryDanger = true,
                    cancelLabel = strings.cancel,
                )
            }
            if (setup != null) list += mainDownloaderDialog(setup)
            if (player != null) list += playerAskDialog(player)
            choices.publish(list)
        }
    }
}

internal fun Presenter.choicePrimary(kind: Int) {
    when (ChoiceDialogKind.entries.getOrNull(kind)) {
        ChoiceDialogKind.DUPLICATE -> vm.onEvent(DashboardUiEvent.OnResumeExistingDuplicateClicked)
        ChoiceDialogKind.FILE_CONFLICT -> vm.uiState.value.fileConflictAlert?.let {
            vm.onEvent(DashboardUiEvent.OnSaveAsCopyClicked(it.suggestedNewName))
        }

        ChoiceDialogKind.MAIN_DOWNLOADER -> mainSetupAccept()
        ChoiceDialogKind.PLAYER_LIBRARY -> playerAskDownload()
        ChoiceDialogKind.NONE, null -> Unit
    }
}

internal fun Presenter.choiceSecondary(kind: Int) {
    when (ChoiceDialogKind.entries.getOrNull(kind)) {
        ChoiceDialogKind.DUPLICATE -> vm.onEvent(DashboardUiEvent.OnRestartDuplicateClicked)
        ChoiceDialogKind.FILE_CONFLICT -> vm.onEvent(DashboardUiEvent.OnConfirmOverwriteClicked)
        ChoiceDialogKind.MAIN_DOWNLOADER -> Unit
        ChoiceDialogKind.PLAYER_LIBRARY -> playerAskSystem()
        ChoiceDialogKind.NONE, null -> Unit
    }
}

internal fun Presenter.choiceDismiss(kind: Int) {
    when (ChoiceDialogKind.entries.getOrNull(kind)) {
        ChoiceDialogKind.DUPLICATE -> vm.onEvent(DashboardUiEvent.OnDismissDuplicateDialog)
        ChoiceDialogKind.FILE_CONFLICT -> vm.onEvent(DashboardUiEvent.OnDismissFileConflictDialog)
        ChoiceDialogKind.MAIN_DOWNLOADER -> mainSetupDecline()
        ChoiceDialogKind.PLAYER_LIBRARY -> playerAsk.value = null
        ChoiceDialogKind.NONE, null -> Unit
    }
}

internal fun Presenter.choiceCheck(kind: Int, checked: Boolean) {
    when (ChoiceDialogKind.entries.getOrNull(kind)) {
        ChoiceDialogKind.PLAYER_LIBRARY -> Unit
        else -> Unit
    }
}
