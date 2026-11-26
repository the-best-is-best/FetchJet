package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_extractor.OnDemandStatus
import com.michelle.raouf.fetch_jet.feature.media_extractor.ensurePlayerLibrary
import com.michelle.raouf.fetch_jet.feature.media_extractor.playerLibraryStatus
import com.michelle.raouf.fetch_jet.feature.pdf_reader.ensurePdfLibrary
import com.michelle.raouf.fetch_jet.feature.pdf_reader.pdfLibraryStatus
import com.michelle.raouf.fetch_jet.nativeapp.dto.BadgeToneCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

internal class LibraryWait(
    val pdf: Boolean,
    val name: String,
    val secondaryIcon: String,
    val secondaryLabel: () -> String,
    val open: () -> Unit,
    val other: () -> Unit,
)

internal data class LibraryWaitView(val wait: LibraryWait, val status: OnDemandStatus)

internal val libraryWait = MutableStateFlow<LibraryWait?>(null)

internal fun libraryWaitFlow(): Flow<LibraryWaitView?> =
    combine(libraryWait, playerLibraryStatus(), pdfLibraryStatus()) { wait, player, pdf ->
        wait?.let { LibraryWaitView(it, if (it.pdf) pdf else player) }
    }

internal fun Presenter.waitForLibrary(wait: LibraryWait) {
    val ready: () -> Unit = {
        if (libraryWait.value === wait) {
            libraryWait.value = null
            wait.open()
        }
    }
    libraryWait.value = wait
    if (wait.pdf) vm.ensurePdfLibrary(toast = false, onReady = ready) else vm.ensurePlayerLibrary(toast = false, onReady = ready)
}

internal fun libraryWaitDialog(view: LibraryWaitView): ChoiceDialogDto {
    val s = LocalizationManager.Strings
    val wait = view.wait
    val failed = view.status.error != null && !view.status.running
    val percent = view.status.percent
    val about = if (wait.pdf) s.pdfAskText else s.playerAskText
    return ChoiceDialogDto(
        kind = ChoiceDialogKind.LIBRARY_DOWNLOAD.ordinal,
        icon = if (wait.pdf) "📄" else "🎬",
        danger = failed,
        title = if (wait.pdf) s.pdfModeOn else s.playerModeOn,
        name = wait.name,
        nameRtl = startsRightToLeft(wait.name),
        badge = if (failed) s.sysDownloadFailed else s.sysStatusDownloading,
        badgeTone = (if (failed) BadgeToneCode.ERROR else BadgeToneCode.RUNNING).ordinal,
        percent = if (failed || percent == null) "" else "$percent%",
        barShown = !failed,
        progress = if (failed) 0f else (percent ?: 0) / 100f,
        question = if (failed) s.sysMediaToolsDownloadFailed + "\n" + view.status.error else about,
        primaryIcon = if (failed) "↻" else "",
        primaryLabel = if (failed) s.retry else "",
        secondaryIcon = wait.secondaryIcon,
        secondaryLabel = wait.secondaryLabel(),
        cancelLabel = s.cancel,
    )
}

internal fun Presenter.libraryWaitRetry() {
    val wait = libraryWait.value ?: return
    waitForLibrary(wait)
}

internal fun Presenter.libraryWaitOther() {
    val wait = libraryWait.value ?: return
    libraryWait.value = null
    wait.other()
}

internal fun libraryWaitCancel() {
    libraryWait.value = null
}
