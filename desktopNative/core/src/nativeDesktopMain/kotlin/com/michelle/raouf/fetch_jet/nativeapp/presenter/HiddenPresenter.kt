package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.security.DeviceAuth
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.getClipboardText
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.nativeapp.dto.AlertDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AlertKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenError
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenNotice
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderOfRow
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenLockTexts
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenLockedText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenNoticeText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenWrongPinText
import com.michelle.raouf.fetch_jet.presentation.dashboard.playlistIdOfRow
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val HIDE_KEY = 100
internal const val UNHIDE_KEY = 101
private const val CLIPBOARD_CLEAR_MS = 60_000L

internal enum class HiddenStep { SET_FIRST, SET_CONFIRM, UNLOCK, CHANGE_OLD, CHANGE_NEW, CHANGE_CONFIRM, REMOVE, RESTORE_CODE, RESET_FIRST, RESET_CONFIRM }

internal data class HiddenTarget(
    val taskIds: List<String>,
    val playlistIds: List<String>,
    val folders: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = taskIds.isEmpty() && playlistIds.isEmpty() && folders.isEmpty()
}

internal data class HiddenLocal(
    val step: HiddenStep? = null,
    val first: String = "",
    val old: String = "",
    val message: String = "",
    val rev: Int = 0,
    val pending: HiddenTarget? = null,
    val lockedUntilMs: Long = 0L,
    val type: HiddenLockType = HiddenLockType.PIN,
)

private fun storedLockType(): HiddenLockType = HiddenStore.state.value.lockType ?: HiddenLockType.PIN

private fun HiddenStep.asksStored(): Boolean = this == HiddenStep.UNLOCK || this == HiddenStep.CHANGE_OLD || this == HiddenStep.REMOVE

private fun HiddenStep.offersSwitch(): Boolean = this == HiddenStep.SET_FIRST || this == HiddenStep.RESET_FIRST || this == HiddenStep.CHANGE_NEW

private fun HiddenLocal.shownType(step: HiddenStep): HiddenLockType = if (step.asksStored()) storedLockType() else type

private fun freshHidden(step: HiddenStep, rev: Int, pending: HiddenTarget? = null): HiddenLocal =
    HiddenLocal(step = step, rev = rev + 1, pending = pending, type = storedLockType())

internal fun DashboardUiState.findTask(id: String): DownloadTask? =
    allTasks.firstOrNull { it.id == id } ?: hidden.tasks.firstOrNull { it.id == id }

private fun normalizePin(raw: String): String = buildString {
    for (c in raw.trim()) {
        append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        )
    }
}

internal fun Presenter.startHidden() {
    scope.launch {
        var seenRev = local.value.filterRev
        combine(
            vm.uiState.map { it.hidden.unlocked }.distinctUntilChanged(),
            vm.uiState.map { it.selectedTab }.distinctUntilChanged(),
            local.map { it.hiddenOpen }.distinctUntilChanged(),
            local.map { it.filterRev }.distinctUntilChanged(),
        ) { unlocked, tab, open, rev ->
            val left = rev != seenRev
            seenRev = rev
            open && (!unlocked || tab == DashboardTab.SETTINGS || left)
        }
            .filter { it }
            .collect { hiddenLockAll() }
    }
    scope.launch {
        vm.uiState.map { it.hidden.notice }.distinctUntilChanged().filterNotNull().collect { notice ->
            showToast(hiddenNoticeText(notice))
            if (notice == HiddenNotice.HIDDEN_DONE) local.update { it.copy(checkedIds = emptySet(), selectedTaskId = null, focusId = null) }
            vm.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        }
    }
    scope.launch {
        vm.uiState.map { it.hidden.pinResetAllowed }.distinctUntilChanged().collect { allowed ->
            val step = hiddenLocal.value.step
            if (allowed && step == HiddenStep.UNLOCK) {
                clearTyped(AlertKind.HIDDEN_PIN.ordinal)
                hiddenLocal.update { freshHidden(HiddenStep.RESET_FIRST, it.rev) }
            } else if (!allowed && (step == HiddenStep.RESET_FIRST || step == HiddenStep.RESET_CONFIRM)) {
                hiddenDismiss()
            }
        }
    }
}

internal fun Presenter.hiddenAlertFlow(): Flow<AlertDto?> {
    val tick = hiddenLocal.map { it.lockedUntilMs }.distinctUntilChanged().flatMapLatest { until ->
        if (until <= currentEpochMs()) flowOf(0L) else flow {
            while (true) {
                val now = currentEpochMs()
                emit(now)
                if (now >= until) break
                delay(1000)
            }
        }
    }
    return combine(hiddenLocal, tick, AppLanguage.current) { s, now, _ -> buildHiddenAlert(s, now) }
}

private fun buildHiddenAlert(s: HiddenLocal, now: Long): AlertDto? {
    val step = s.step ?: return null
    val st = LocalizationManager.Strings
    val locked = s.lockedUntilMs > now
    val message = if (locked) hiddenLockedText(((s.lockedUntilMs - now + 999) / 1000).toInt()) else s.message
    val type = s.shownType(step)
    val t = hiddenLockTexts(type)
    val stored = hiddenLockTexts(storedLockType())
    val pattern = type == HiddenLockType.PATTERN && step != HiddenStep.RESTORE_CODE
    val title: String
    val text: String
    val label: String
    when (step) {
        HiddenStep.SET_FIRST -> { title = t.setTitle; text = t.setHint; label = t.enter }
        HiddenStep.SET_CONFIRM -> { title = t.setTitle; text = ""; label = t.confirm }
        HiddenStep.UNLOCK -> { title = st.hiddenTitle; text = ""; label = t.enter }
        HiddenStep.CHANGE_OLD -> { title = stored.change; text = ""; label = t.oldLabel }
        HiddenStep.CHANGE_NEW -> { title = stored.change; text = ""; label = t.newLabel }
        HiddenStep.CHANGE_CONFIRM -> { title = stored.change; text = ""; label = t.confirm }
        HiddenStep.REMOVE -> { title = t.remove; text = t.removeWarn; label = t.enter }
        HiddenStep.RESTORE_CODE -> { title = st.hiddenRecoveryRestoreTitle; text = st.hiddenRecoveryRestoreHint; label = st.hiddenRecoveryEnter }
        HiddenStep.RESET_FIRST -> { title = t.resetTitle; text = t.setHint; label = t.enter }
        HiddenStep.RESET_CONFIRM -> { title = t.resetTitle; text = ""; label = t.confirm }
    }
    val link = when (step) {
        HiddenStep.UNLOCK -> if (DeviceAuth.available()) t.forgot else ""
        HiddenStep.SET_FIRST -> if (!HiddenStore.hasLock()) st.hiddenRecoveryRestoreTitle else ""
        else -> ""
    }
    return AlertDto(
        kind = AlertKind.HIDDEN_PIN.ordinal,
        title = title,
        titleSmall = true,
        text = text,
        hint = message,
        hintError = message.isNotEmpty(),
        inputLabel = label,
        inputSecret = step != HiddenStep.RESTORE_CODE && !pattern,
        inputPattern = pattern,
        inputRev = s.rev,
        inputDisabled = locked,
        extraLabel = if (step.offersSwitch()) t.switchTo else "",
        confirmLabel = if (step == HiddenStep.REMOVE) t.remove else st.ok,
        confirmDanger = step == HiddenStep.REMOVE,
        dismissLabel = st.cancel,
        linkLabel = link,
    )
}

private fun Presenter.hiddenGo(step: HiddenStep, first: String = "", old: String = "", message: String = "") {
    hiddenLocal.update { it.copy(step = step, first = first, old = old, message = message, rev = it.rev + 1) }
}

private fun Presenter.hiddenCloseDialog() {
    clearTyped(AlertKind.HIDDEN_PIN.ordinal)
    hiddenLocal.update { HiddenLocal(rev = it.rev + 1) }
}

private fun Presenter.hiddenFail(h: HiddenUiState, step: HiddenStep, first: String = "", old: String = "") {
    when (h.error) {
        HiddenError.WRONG_PIN ->
            hiddenGo(step, first, old, hiddenWrongPinText(h.triesLeft))

        HiddenError.LOCKED -> hiddenLocal.update {
            it.copy(
                step = step, first = first, old = old, message = "", rev = it.rev + 1,
                lockedUntilMs = currentEpochMs() + h.lockedSeconds * 1000L,
            )
        }

        HiddenError.INVALID_PIN -> hiddenGo(step, first, old, hiddenLockTexts(hiddenLocal.value.shownType(step)).invalid)
        null -> hiddenCloseDialog()
    }
}

internal fun Presenter.hiddenConfirm(raw: String) {
    val s = hiddenLocal.value
    val step = s.step ?: return
    if (step == HiddenStep.RESTORE_CODE) {
        hiddenRestoreConfirm(raw)
        return
    }
    if (s.lockedUntilMs > currentEpochMs()) return
    val t = hiddenLockTexts(s.type)
    val pin = normalizePin(raw)
    if (pin.isEmpty()) return
    when (step) {
        HiddenStep.SET_FIRST ->
            if (!HiddenStore.isValidSecret(s.type, pin)) hiddenGo(HiddenStep.SET_FIRST, message = t.invalid)
            else hiddenGo(HiddenStep.SET_CONFIRM, first = pin)

        HiddenStep.SET_CONFIRM ->
            if (pin != s.first) hiddenGo(HiddenStep.SET_FIRST, message = t.mismatch)
            else hiddenFinishSet(pin, s)

        HiddenStep.UNLOCK -> {
            vm.onEvent(DashboardUiEvent.OnHiddenUnlock(pin))
            val h = vm.uiState.value.hidden
            if (h.unlocked && h.error == null) {
                hiddenCloseDialog()
                openHiddenScreen()
            } else hiddenFail(h, HiddenStep.UNLOCK)
        }

        HiddenStep.CHANGE_OLD -> {
            vm.onEvent(DashboardUiEvent.OnHiddenUnlock(pin))
            val h = vm.uiState.value.hidden
            if (h.unlocked && h.error == null) hiddenGo(HiddenStep.CHANGE_NEW, old = pin)
            else hiddenFail(h, HiddenStep.CHANGE_OLD)
        }

        HiddenStep.CHANGE_NEW ->
            if (!HiddenStore.isValidSecret(s.type, pin)) hiddenGo(HiddenStep.CHANGE_NEW, old = s.old, message = t.invalid)
            else hiddenGo(HiddenStep.CHANGE_CONFIRM, first = pin, old = s.old)

        HiddenStep.CHANGE_CONFIRM ->
            if (pin != s.first) hiddenGo(HiddenStep.CHANGE_NEW, old = s.old, message = t.mismatch)
            else {
                vm.onEvent(DashboardUiEvent.OnHiddenChangePin(s.old, pin, s.type))
                val h = vm.uiState.value.hidden
                if (h.error == null) hiddenCloseDialog() else hiddenFail(h, HiddenStep.CHANGE_OLD)
            }

        HiddenStep.RESTORE_CODE -> Unit

        HiddenStep.RESET_FIRST ->
            if (!HiddenStore.isValidSecret(s.type, pin)) hiddenGo(HiddenStep.RESET_FIRST, message = t.invalid)
            else hiddenGo(HiddenStep.RESET_CONFIRM, first = pin)

        HiddenStep.RESET_CONFIRM ->
            if (pin != s.first) hiddenGo(HiddenStep.RESET_FIRST, message = t.mismatch)
            else {
                vm.onEvent(DashboardUiEvent.OnHiddenResetPin(pin, s.type))
                val h = vm.uiState.value.hidden
                if (h.unlocked && h.error == null && !h.pinResetAllowed) {
                    hiddenCloseDialog()
                    openHiddenScreen()
                } else if (h.error == HiddenError.INVALID_PIN) hiddenGo(HiddenStep.RESET_FIRST, message = t.invalid)
                else hiddenDismiss()
            }

        HiddenStep.REMOVE -> {
            vm.onEvent(DashboardUiEvent.OnHiddenRemoveLock(pin))
            val h = vm.uiState.value.hidden
            if (h.error == null) {
                hiddenCloseDialog()
                hiddenLockAll()
            } else hiddenFail(h, HiddenStep.REMOVE)
        }
    }
}

private fun Presenter.hiddenFinishSet(pin: String, s: HiddenLocal) {
    vm.onEvent(DashboardUiEvent.OnHiddenSetPin(pin, s.type))
    if (!HiddenStore.hasLock()) {
        hiddenGo(HiddenStep.SET_FIRST, message = hiddenLockTexts(s.type).invalid)
        return
    }
    val pending = s.pending
    hiddenCloseDialog()
    if (pending != null) {
        applyHide(pending, lockAfter = true)
    } else {
        openHiddenScreen()
    }
}

internal fun Presenter.hiddenDismiss() {
    val step = hiddenLocal.value.step
    hiddenCloseDialog()
    if (step == HiddenStep.RESET_FIRST || step == HiddenStep.RESET_CONFIRM) vm.onEvent(DashboardUiEvent.OnHiddenResetCancel)
    if (!local.value.hiddenOpen && !vm.uiState.value.hidden.unlocked) vm.onEvent(DashboardUiEvent.OnHiddenLock)
}

internal fun Presenter.hiddenEntry() {
    if (!HiddenStore.hasLock()) {
        hiddenLocal.update { freshHidden(HiddenStep.SET_FIRST, it.rev) }
    } else if (vm.uiState.value.hidden.unlocked) {
        openHiddenScreen()
    } else if (vm.uiState.value.hidden.pinResetAllowed) {
        hiddenLocal.update { freshHidden(HiddenStep.RESET_FIRST, it.rev) }
    } else {
        hiddenLocal.update { freshHidden(HiddenStep.UNLOCK, it.rev) }
    }
}

internal fun Presenter.hiddenSwitchType() {
    val s = hiddenLocal.value
    val step = s.step?.takeIf { it.offersSwitch() } ?: return
    if (s.lockedUntilMs > currentEpochMs()) return
    clearTyped(AlertKind.HIDDEN_PIN.ordinal)
    val next = if (s.type == HiddenLockType.PIN) HiddenLockType.PATTERN else HiddenLockType.PIN
    hiddenLocal.update { it.copy(step = step, type = next, first = "", message = "", rev = it.rev + 1) }
}

internal fun Presenter.hiddenStartChange() {
    if (!local.value.hiddenOpen) return
    hiddenLocal.update { freshHidden(HiddenStep.CHANGE_OLD, it.rev) }
}

internal fun Presenter.hiddenStartRemove() {
    if (!local.value.hiddenOpen) return
    hiddenLocal.update { freshHidden(HiddenStep.REMOVE, it.rev) }
}

private fun Presenter.openHiddenScreen() {
    vm.onEvent(DashboardUiEvent.OnClosePlaylist)
    vm.onEvent(DashboardUiEvent.OnCloseFolderGroup)
    vm.onEvent(DashboardUiEvent.OnTabSelected(DashboardTab.ALL))
    closeRowMenu()
    local.update {
        it.copy(
            hiddenOpen = true,
            search = "",
            searchRev = it.searchRev + 1,
            selectedTaskId = null,
            checkedIds = emptySet(),
            focusId = null,
            anchorId = null,
        )
    }
}

internal fun Presenter.hiddenLockAll() {
    if (hiddenLocal.value.step != null) hiddenCloseDialog()
    val h = vm.uiState.value.hidden
    if (!local.value.hiddenOpen && !h.unlocked) return
    val ids = h.tasks.mapTo(HashSet()) { it.id }
    vm.onEvent(DashboardUiEvent.OnHiddenLock)
    closeRowMenu()
    vm.onEvent(DashboardUiEvent.OnDismissAudioCompress)
    vm.onEvent(DashboardUiEvent.OnDismissSubtitles)
    local.update {
        it.copy(
            hiddenOpen = false,
            selectedTaskId = it.selectedTaskId?.takeIf { id -> id !in ids },
            checkedIds = it.checkedIds - ids,
            focusId = it.focusId?.takeIf { id -> id !in ids },
            anchorId = it.anchorId?.takeIf { id -> id !in ids },
            missingFileTaskId = it.missingFileTaskId?.takeIf { id -> id !in ids },
            restartPauseTaskId = it.restartPauseTaskId?.takeIf { id -> id !in ids },
        )
    }
}

internal fun Presenter.hiddenTargetOf(rows: Collection<String>): HiddenTarget {
    val playlists = rows.mapNotNull { playlistIdOfRow(it) }.distinct()
    val folders = rows.mapNotNull { folderOfRow(it) }.distinct()
    val others = rows.filter { playlistIdOfRow(it) == null && folderOfRow(it) == null }
    return HiddenTarget(others.distinct(), playlists, folders)
}

internal fun Presenter.hideTargets(target: HiddenTarget) {
    if (target.isEmpty) return
    if (!HiddenStore.hasLock()) {
        hiddenLocal.update { freshHidden(HiddenStep.SET_FIRST, it.rev, target) }
        return
    }
    applyHide(target)
}

private fun Presenter.applyHide(target: HiddenTarget, lockAfter: Boolean = false) {
    vm.onEvent(DashboardUiEvent.OnHideTargets(target.taskIds, target.playlistIds, target.folders, lockAfter))
    closeRowMenu()
}

internal fun Presenter.hiddenNoticeAccept() {
    vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
}

internal fun Presenter.hiddenShowRecovery() {
    if (!local.value.hiddenOpen) return
    vm.onEvent(DashboardUiEvent.OnHiddenShowRecoveryCode)
}

internal fun Presenter.hiddenStartRestore() {
    if (!local.value.hiddenOpen) return
    hiddenLocal.update { HiddenLocal(step = HiddenStep.RESTORE_CODE, rev = it.rev + 1) }
}

internal fun Presenter.hiddenLink() {
    when (hiddenLocal.value.step) {
        HiddenStep.UNLOCK -> vm.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        HiddenStep.SET_FIRST -> {
            clearTyped(AlertKind.HIDDEN_PIN.ordinal)
            hiddenLocal.update { HiddenLocal(step = HiddenStep.RESTORE_CODE, rev = it.rev + 1) }
        }
        else -> Unit
    }
}

private fun Presenter.hiddenRestoreConfirm(raw: String) {
    val code = raw.trim()
    if (code.isEmpty()) return
    hiddenCloseDialog()
    vm.onEvent(DashboardUiEvent.OnHiddenRestoreWithCode(code))
}

internal fun hiddenRecoveryAlert(code: String): AlertDto {
    val st = LocalizationManager.Strings
    return AlertDto(
        kind = AlertKind.HIDDEN_RECOVERY.ordinal,
        title = st.hiddenRecoveryTitle,
        titleSmall = true,
        text = st.hiddenRecoveryBody,
        mono = code,
        confirmLabel = st.hiddenRecoveryCopy,
        dismissLabel = st.close,
    )
}

internal fun Presenter.hiddenRecoveryCopy() {
    val code = vm.uiState.value.hidden.recoveryCode ?: return
    secretCopies.update { it + 1 }
    if (!headless) {
        DesktopOs.setClipboardText(code)
        scope.launch {
            delay(CLIPBOARD_CLEAR_MS)
            if (getClipboardText() == code) DesktopOs.setClipboardText("")
        }
    }
    showToast(LocalizationManager.Strings.copied)
}

internal fun Presenter.hiddenRecoveryClose() {
    vm.onEvent(DashboardUiEvent.OnHiddenRecoveryCodeShown)
}

internal fun Presenter.hiddenNoticeDismiss() {
    vm.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeDismissed)
}

internal fun Presenter.openHiddenPath(path: String) {
    if (headless) {
        openedHidden.update { (it + path).takeLast(20) }
        return
    }
    scope.launch(Dispatchers.IO) {
        if (!pathResolver.openFile(path)) showToast(LocalizationManager.Strings.fileMissingTitle)
    }
}

internal fun Presenter.hideChecked() {
    hideTargets(hiddenTargetOf(local.value.checkedIds))
}

internal fun Presenter.unhideChecked() {
    if (!local.value.hiddenOpen) return
    val ids = expandPlaylistRows(local.value.checkedIds)
    if (ids.isEmpty()) return
    vm.onEvent(DashboardUiEvent.OnUnhideTasks(ids))
    local.update { it.copy(checkedIds = emptySet(), selectedTaskId = null, focusId = null) }
}

internal fun Presenter.hiddenGroupEvent(make: (String, String) -> DashboardUiEvent) {
    val h = vm.uiState.value.hidden
    val playlist = h.openPlaylistId.orEmpty()
    val folder = if (playlist.isEmpty()) h.openFolder.orEmpty() else ""
    if (playlist.isEmpty() && folder.isEmpty()) return
    vm.onEvent(make(playlist, folder))
}

internal fun Presenter.unhideGroup() {
    if (!local.value.hiddenOpen) return
    hiddenGroupEvent { p, f -> DashboardUiEvent.OnUnhideGroup(p, f) }
    vm.onEvent(DashboardUiEvent.OnHiddenCloseGroup)
    local.update { it.copy(checkedIds = emptySet(), selectedTaskId = null, focusId = null) }
}

internal fun Presenter.unhideAll() {
    if (!local.value.hiddenOpen) return
    vm.onEvent(DashboardUiEvent.OnUnhideAll)
    local.update { it.copy(checkedIds = emptySet(), selectedTaskId = null, focusId = null) }
}

internal fun rowIsGroup(id: String): Boolean = playlistIdOfRow(id) != null || folderOfRow(id) != null
