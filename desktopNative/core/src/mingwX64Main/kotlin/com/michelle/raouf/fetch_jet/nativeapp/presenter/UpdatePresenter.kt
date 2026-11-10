package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.update.MICROSOFT_STORE_PAGE
import com.michelle.raouf.fetch_jet.core.update.PlatformUpdater
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.nativeapp.dto.UpdateDialogDto
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Presenter.updateState(): UpdateState = debugUpdate.value ?: UpdateManager.state.value

private fun plainNotes(md: String): String = md.lineSequence()
    .map { line ->
        line.trimEnd()
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace("**", "")
            .replace(Regex("\\[([^\\]]+)]\\((https?://[^)]+)\\)"), "$1")
            .replace(Regex("<!--.*?-->"), "")
    }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

private fun installHint(assetName: String?): String {
    val s = LocalizationManager.Strings
    return when {
        assetName == null -> s.installHintReleasePage
        assetName.endsWith(".apk", true) -> s.installHintApk
        assetName.endsWith(".ipa", true) -> s.installHintIpa
        assetName.endsWith(".dmg", true) -> s.installHintDmg
        assetName.endsWith(".deb", true) -> s.installHintDeb
        else -> s.installHintOther
    }
}

private fun buildUpdateDialog(state: UpdateState): UpdateDialogDto {
    val release = state.available
    if (!state.supported || !state.showDialog || release == null) return UpdateDialogDto()
    val s = LocalizationManager.Strings
    val fromStore = PlatformUpdater.updatesFromStore
    val asset = if (fromStore) null else PlatformUpdater.pickAsset(release.assets)
    val downloading = state.downloadProgress >= 0f
    val ready = state.installerPath != null
    val status = when {
        downloading -> s.downloadingUpdate((state.downloadProgress * 100).toInt())
        ready -> s.updateDownloadedDesktop
        !state.canInstallInApp -> if (fromStore) s.storeInstallsItselfOpenNow else installHint(asset?.name)
        else -> ""
    }
    return UpdateDialogDto(
        open = true,
        title = s.updateNewVersionAvailable,
        versionLine = s.updateVersionLine(release.version, state.currentVersion),
        notesLabel = s.updateWhatsNew,
        notes = plainNotes(release.notesForDisplay).ifBlank { s.updateNoReleaseNotes },
        status = status,
        progressShown = downloading,
        progress = state.downloadProgress.coerceIn(0f, 1f),
        indeterminate = downloading && state.downloadProgress <= 0f,
        error = state.message?.takeIf { state.messageIsInstallError }.orEmpty(),
        confirmLabel = when {
            state.canInstallInApp && ready -> s.updateRestartAndUpdate
            state.canInstallInApp && asset != null -> if (downloading) s.updateDownloading else s.updateDownloadAndInstall
            fromStore -> s.updateOpenMicrosoftStore
            asset != null -> s.updateDownloadWithSize(if (asset.size > 0L) "(${formatBytes(asset.size)})" else "")
            else -> s.updateOpenReleasePage
        },
        confirmEnabled = !(state.canInstallInApp && !ready && asset != null && downloading),
        skipLabel = if (downloading) "" else s.updateSkipThisVersion,
        laterLabel = s.updateLater,
        locked = downloading,
    )
}

internal fun Presenter.startUpdates() {
    scope.launch {
        combine(UpdateManager.state, debugUpdate, AppLanguage.current) { real, fake, _ -> buildUpdateDialog(fake ?: real) }
            .collect { updateDialog.publish(it) }
    }
    if (WinApp.automaticDownloads) UpdateManager.onAppStart()
}

internal fun Presenter.updateShow() {
    if (debugUpdate.value != null) debugUpdate.update { it?.copy(showDialog = it.available != null) }
    else UpdateManager.showDialog()
}

internal fun Presenter.updateLater(fromScrim: Boolean) {
    val state = updateState()
    if (fromScrim && state.downloadProgress >= 0f) return
    if (debugUpdate.value != null) debugUpdate.update { it?.copy(showDialog = false) }
    else UpdateManager.dismiss()
}

internal fun Presenter.updateSkip() {
    if (debugUpdate.value != null) debugUpdate.update { it?.copy(showDialog = false, skippedVersion = it.available?.version.orEmpty()) }
    else UpdateManager.skip()
}

internal fun Presenter.updateConfirm() {
    val state = updateState()
    val release = state.available ?: return
    val fromStore = PlatformUpdater.updatesFromStore
    val asset = if (fromStore) null else PlatformUpdater.pickAsset(release.assets)
    val ready = state.installerPath != null
    when {
        state.canInstallInApp && (ready || asset != null) -> if (debugUpdate.value == null) UpdateManager.installNow()
        fromStore -> {
            openExternal(MICROSOFT_STORE_PAGE)
            updateLater(false)
        }

        else -> {
            openExternal(asset?.url ?: release.pageUrl)
            updateLater(false)
        }
    }
}
