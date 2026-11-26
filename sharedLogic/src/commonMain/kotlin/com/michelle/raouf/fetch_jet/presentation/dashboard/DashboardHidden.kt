package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.filesystem.AllFilesAccess
import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import okio.Path.Companion.toPath
import com.michelle.raouf.fetch_jet.core.security.DeviceAuth
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenCheck
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenOpening
import com.michelle.raouf.fetch_jet.feature.hidden.PinReset
import com.michelle.raouf.fetch_jet.feature.hidden.RecoveryRestore
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.michelle.raouf.fetch_jet.feature.queue_manager.encryptHiddenPending
import com.michelle.raouf.fetch_jet.feature.queue_manager.HiddenMoveReport
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent.OnHideTargets
import com.michelle.raouf.fetch_jet.feature.queue_manager.hideTasks
import com.michelle.raouf.fetch_jet.feature.queue_manager.hidePlaylistTasks
import com.michelle.raouf.fetch_jet.feature.queue_manager.unhideEverything
import com.michelle.raouf.fetch_jet.feature.queue_manager.unhideTasks
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HiddenError { WRONG_PIN, LOCKED, INVALID_PIN }

enum class HiddenNotice {
    COPY_LEFT, MOVE_FAILED, OPEN_FAILED, HIDDEN_DONE,
    RESTORE_DONE, RESTORE_WRONG_CODE, RESTORE_NOTHING, RECOVERY_UNAVAILABLE,
    AUTH_FAILED, RESET_DONE, RESET_EXPIRED, NEED_FILE_ACCESS, NOT_MEDIA
}

data class HiddenUiState(
    val lockType: HiddenLockType? = null,
    val unlocked: Boolean = false,
    val tasks: List<DownloadTask> = emptyList(),
    val error: HiddenError? = null,
    val triesLeft: Int = 0,
    val lockedSeconds: Int = 0,
    val notice: HiddenNotice? = null,
    val opening: HiddenOpening? = null,
    val encryptNoticeNeeded: Boolean = false,
    val openPlaylistId: String? = null,
    val openFolder: String? = null,
    val workDone: Int = 0,
    val workTotal: Int = 0,
    val workFraction: Float = 0f,
    val recoveryCode: String? = null,
    val pinResetAllowed: Boolean = false,
    val covered: Boolean = false
) {
    val hasLock: Boolean get() = lockType != null
    val canResetPin: Boolean get() = hasLock && DeviceAuth.available()
    val working: Boolean get() = workTotal > 0
}

internal fun DashboardViewModel.handleHiddenEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnHiddenSetPin -> {
            if (!HiddenStore.isValidSecret(event.lockType, event.pin)) {
                _uiState.update { it.copy(hidden = it.hidden.copy(error = HiddenError.INVALID_PIN)) }
            } else if (HiddenStore.setPin(event.pin, event.lockType)) {
                _uiState.update { it.copy(hidden = it.hidden.copy(lockType = HiddenStore.state.value.lockType, unlocked = true, error = null)) }
                refreshHiddenTasks()
            } else _uiState.update { it.copy(hidden = it.hidden.copy(notice = HiddenNotice.MOVE_FAILED)) }
        }

        is DashboardUiEvent.OnHiddenUnlock -> applyHiddenCheck(HiddenStore.verify(event.pin), unlockOnOk = true)
        DashboardUiEvent.OnHiddenLock -> lockHidden()
        DashboardUiEvent.OnHiddenCover -> coverHidden()
        DashboardUiEvent.OnHiddenReauth -> reauthHidden()
        is DashboardUiEvent.OnHiddenChangePin -> applyHiddenCheck(HiddenStore.changePin(event.oldPin, event.newPin, event.newLockType), unlockOnOk = false)
        is DashboardUiEvent.OnHiddenRemoveLock -> {
            val check = HiddenStore.verify(event.pin)
            if (check != HiddenCheck.Ok) {
                applyHiddenCheck(check, unlockOnOk = false)
                return
            }
            scope.launch {
                val report = queueRepository.unhideEverything()
                if (report.failed > 0) {
                    reportHiddenMove(report)
                    return@launch
                }
                val removed = HiddenStore.removeLock(event.pin)
                applyHiddenCheck(removed, unlockOnOk = false)
                if (removed == HiddenCheck.Ok) lockHidden()
            }
        }

        is OnHideTargets -> hideTargets(event)

        DashboardUiEvent.OnHiddenNoticeShown ->
            _uiState.update { it.copy(hidden = it.hidden.copy(notice = null)) }

        is DashboardUiEvent.OnAddHiddenToggled ->
            if (event.on) startHiddenAdd() else _uiState.update { it.copy(addHidden = false) }

        DashboardUiEvent.OnHiddenAddDownload -> startHiddenAdd()

        is DashboardUiEvent.OnUnhideTasks -> {
            if (!_uiState.value.hidden.unlocked) return
            if (unhideNeedsFileAccess(event.taskIds)) return
            scope.launch { reportHiddenMove(queueRepository.unhideTasks(event.taskIds)) }
        }

        DashboardUiEvent.OnUnhideAll -> {
            if (!_uiState.value.hidden.unlocked) return
            if (unhideNeedsFileAccess(queueRepository.allTasksFlow.value.filter { it.hidden }.map { it.id })) return
            scope.launch { reportHiddenMove(queueRepository.unhideEverything()) }
        }

        is DashboardUiEvent.OnHiddenOpenFile -> openHiddenFile(event.taskId)

        is DashboardUiEvent.OnHiddenPlayClicked -> if (_uiState.value.hidden.unlocked) playHidden(event.taskId)

        DashboardUiEvent.OnHiddenGroupPlayClicked -> if (_uiState.value.hidden.unlocked) playHiddenGroup()

        DashboardUiEvent.OnHiddenClosePlayer -> {
            closeHiddenPlayer()
            HiddenMedia.cleanup(queueRepository.pathResolver)
        }

        DashboardUiEvent.OnHiddenEncryptNoticeAccepted -> {
            val hadKey = HiddenStore.hasRecoveryKey()
            HiddenStore.acceptNotice()
            val newCode = if (hadKey) null else HiddenStore.recoveryCode()
            _uiState.update {
                val h = it.hidden.copy(encryptNoticeNeeded = false)
                it.copy(hidden = when {
                    hadKey -> h
                    newCode != null -> h.copy(recoveryCode = newCode)
                    else -> h.copy(notice = HiddenNotice.RECOVERY_UNAVAILABLE)
                })
            }
            val pending = pendingHide
            val addDialog = pendingAddDialog
            pendingHide = null
            pendingAddDialog = false
            scope.launch {
                queueRepository.encryptHiddenPending()
                noteRecoveryGap()
                if (pending != null) runHideTargets(pending)
                if (addDialog) startHiddenAdd()
            }
        }

        DashboardUiEvent.OnHiddenEncryptNoticeDismissed -> {
            pendingHide = null
            pendingAddDialog = false
            _uiState.update { it.copy(hidden = it.hidden.copy(encryptNoticeNeeded = false)) }
        }

        DashboardUiEvent.OnHiddenShowRecoveryCode -> {
            if (!_uiState.value.hidden.unlocked) return
            val code = HiddenStore.recoveryCode()
            _uiState.update {
                if (code != null) it.copy(hidden = it.hidden.copy(recoveryCode = code))
                else it.copy(hidden = it.hidden.copy(notice = HiddenNotice.RECOVERY_UNAVAILABLE))
            }
        }

        DashboardUiEvent.OnHiddenRecoveryCodeShown ->
            _uiState.update { it.copy(hidden = it.hidden.copy(recoveryCode = null)) }

        is DashboardUiEvent.OnHiddenRestoreWithCode -> {
            if (HiddenStore.hasLock() && !_uiState.value.hidden.unlocked) return
            val notice = when (HiddenStore.restoreWithCode(event.code)) {
                RecoveryRestore.DONE -> HiddenNotice.RESTORE_DONE
                RecoveryRestore.WRONG_CODE -> HiddenNotice.RESTORE_WRONG_CODE
                RecoveryRestore.NOTHING -> HiddenNotice.RESTORE_NOTHING
                RecoveryRestore.SAVE_FAILED -> HiddenNotice.MOVE_FAILED
            }
            _uiState.update { it.copy(hidden = it.hidden.copy(notice = notice)) }
        }

        DashboardUiEvent.OnHiddenForgotPin -> {
            val auth = DeviceAuth.handler ?: return
            if (!HiddenStore.hasLock()) return
            scope.launch {
                val confirmed = try {
                    auth(LocalizationManager.Strings.hiddenForgotReason)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    _uiState.update { it.copy(hidden = it.hidden.copy(notice = HiddenNotice.AUTH_FAILED)) }
                    return@launch
                }
                if (!confirmed || !HiddenStore.allowPinReset()) return@launch
                _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = true)) }
                delay(HiddenStore.PIN_RESET_WINDOW_MS + 500)
                if (!HiddenStore.pinResetAllowed()) _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = false)) }
            }
        }

        DashboardUiEvent.OnHiddenResetCancel -> {
            HiddenStore.cancelPinReset()
            _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = false)) }
        }

        is DashboardUiEvent.OnHiddenResetPin -> {
            val outcome = HiddenStore.resetPin(event.pin, event.lockType)
            if (outcome != PinReset.INVALID) HiddenStore.cancelPinReset()
            when (outcome) {
                PinReset.OK -> {
                    _uiState.update {
                        it.copy(hidden = it.hidden.copy(
                            lockType = HiddenStore.state.value.lockType,
                            unlocked = true,
                            error = null,
                            triesLeft = 0,
                            lockedSeconds = 0,
                            pinResetAllowed = false,
                            notice = HiddenNotice.RESET_DONE
                        ))
                    }
                    refreshHiddenTasks()
                }
                PinReset.INVALID -> _uiState.update { it.copy(hidden = it.hidden.copy(error = HiddenError.INVALID_PIN)) }
                PinReset.EXPIRED -> _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = false, notice = HiddenNotice.RESET_EXPIRED)) }
                PinReset.NO_LOCK -> {
                    _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = false)) }
                    applyHiddenCheck(HiddenCheck.NoLock, unlockOnOk = false)
                }
                PinReset.SAVE_FAILED -> _uiState.update { it.copy(hidden = it.hidden.copy(pinResetAllowed = false, notice = HiddenNotice.MOVE_FAILED)) }
            }
        }

        else -> Unit
    }
}

private fun DashboardViewModel.startHiddenAdd() {
    if (!HiddenStore.hasLock()) return
    if (noticeBlocks(true)) {
        pendingAddDialog = true
        return
    }
    _uiState.update { it.copy(addHidden = true) }
    onEvent(DashboardUiEvent.OnToggleAddDialog(true))
}

internal fun DashboardViewModel.startedHidden(task: DownloadTask): Boolean =
    HiddenFiles.isPrivate(queueRepository.pathResolver, task.destinationPath)

internal fun DashboardViewModel.noteHiddenDone() {
    _uiState.update { if (it.hidden.notice == null) it.copy(hidden = it.hidden.copy(notice = HiddenNotice.HIDDEN_DONE)) else it }
}

internal fun DashboardViewModel.noticeBlocks(needed: Boolean): Boolean {
    if (!needed || HiddenStore.noticeAccepted()) return false
    _uiState.update { it.copy(hidden = it.hidden.copy(encryptNoticeNeeded = true)) }
    return true
}

private fun DashboardViewModel.openHiddenFile(taskId: String) {
    val state = _uiState.value.hidden
    if (!state.unlocked || state.opening != null) return
    val task = queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId && it.hidden } ?: return
    _uiState.update { it.copy(hidden = it.hidden.copy(opening = HiddenOpening(taskId, 0f))) }
    scope.launch {
        val path = try {
            HiddenMedia.openPlayable(taskId, task.fileName, { fraction ->
                _uiState.update { s -> s.copy(hidden = s.hidden.copy(opening = HiddenOpening(taskId, fraction))) }
            }, queueRepository.pathResolver)
        } finally {
            _uiState.update { it.copy(hidden = it.hidden.copy(opening = null)) }
        }
        if (path != null) _uiEffect.emit(DashboardUiEffect.OpenHiddenFile(path))
        else _uiState.update { it.copy(hidden = it.hidden.copy(notice = HiddenNotice.OPEN_FAILED)) }
    }
}

internal fun HiddenMoveReport.notice(): HiddenNotice? = when {
    failed > 0 -> HiddenNotice.MOVE_FAILED
    copiesLeft > 0 -> HiddenNotice.COPY_LEFT
    skipped > 0 -> HiddenNotice.NOT_MEDIA
    else -> null
}

internal fun DashboardViewModel.noteRecoveryGap() {
    if (HiddenStore.takeRecoveryGap()) {
        _uiState.update { it.copy(hidden = it.hidden.copy(notice = HiddenNotice.RECOVERY_UNAVAILABLE)) }
    }
}

internal fun DashboardViewModel.reportHiddenMove(report: HiddenMoveReport) {
    val notice = report.notice()
    if (notice != null) _uiState.update { it.copy(hidden = it.hidden.copy(notice = notice)) }
    refreshHiddenTasks()
}

internal data class HideStep(val kind: Kind, val value: String) {
    enum class Kind { TASKS, PLAYLIST, FOLDER }
}

internal fun OnHideTargets.steps(): List<HideStep> =
    (if (taskIds.isNotEmpty()) listOf(HideStep(HideStep.Kind.TASKS, "")) else emptyList()) +
            playlistIds.distinct().map { HideStep(HideStep.Kind.PLAYLIST, it) } +
            folders.distinct().map { HideStep(HideStep.Kind.FOLDER, it) }

internal fun DashboardViewModel.askFileAccess(): Boolean {
    AllFilesAccess.openSettings()
    _uiState.update { it.copy(hidden = it.hidden.copy(notice = HiddenNotice.NEED_FILE_ACCESS)) }
    return true
}

internal fun DashboardViewModel.hideNeedsFileAccess(event: OnHideTargets): Boolean {
    if (!AllFilesAccess.needed()) return false
    val state = _uiState.value
    val all = queueRepository.allTasksFlow.value
    val tasks = all.filter { it.id in event.taskIds || (it.playlistId.isNotEmpty() && it.playlistId in event.playlistIds) } +
            event.folders.flatMap { folderTasks(queueRepository.visibleTasksFlow.value, state.playlists, it) }
    val blocked = tasks.any { task ->
        val name = task.fileName
        if (name.isBlank()) return@any false
        val path = task.destinationPath.trimEnd('/', '\\') + "/" + name
        AppFileSystem.exists(path.toPath()) && !AllFilesAccess.canRead(path)
    }
    return blocked && askFileAccess()
}

internal fun DashboardViewModel.unhideNeedsFileAccess(ids: Collection<String>): Boolean {
    if (!AllFilesAccess.needed()) return false
    val folders = ids.mapNotNull { HiddenStore.originOf(it) }.distinct()
    return folders.any { !AllFilesAccess.canWriteInto(it) } && askFileAccess()
}

private fun DashboardViewModel.hideTargets(event: OnHideTargets) {
    if (!HiddenStore.hasLock()) return
    if (hideNeedsFileAccess(event)) return
    val needsNotice = event.playlistIds.isNotEmpty() || event.folders.isNotEmpty() ||
            HiddenMedia.noticeRequired(queueRepository.allTasksFlow.value, event.taskIds, HiddenStore.noticeAccepted())
    if (noticeBlocks(needsNotice)) {
        pendingHide = event
        return
    }
    scope.launch { runHideTargets(event) }
}

internal suspend fun DashboardViewModel.runHideTargets(event: OnHideTargets) {
    val state = _uiState.value
    val openFolder = state.openFolder
    val openFolderIds = openFolder?.let { folderTasks(queueRepository.visibleTasksFlow.value, state.playlists, it).map { t -> t.id } }.orEmpty()
    var report = HiddenMoveReport()
    for (step in event.steps()) {
        report += when (step.kind) {
            HideStep.Kind.TASKS -> queueRepository.hideTasks(event.taskIds)
            HideStep.Kind.PLAYLIST -> queueRepository.hidePlaylistTasks(step.value)
            HideStep.Kind.FOLDER -> {
                val ids = folderTasks(queueRepository.visibleTasksFlow.value, _uiState.value.playlists, step.value).map { it.id }
                val one = queueRepository.hideTasks(ids)
                if (one.failed == 0 && one.skipped == 0) HiddenStore.hideFolder(step.value)
                one
            }
        }
    }
    val now = _uiState.value
    if (now.openPlaylistId != null && now.openPlaylistId in event.playlistIds) onEvent(DashboardUiEvent.OnClosePlaylist)
    if (openFolder != null && now.openFolder == openFolder &&
        (openFolder in event.folders || (openFolderIds.isNotEmpty() && event.taskIds.containsAll(openFolderIds)))
    ) onEvent(DashboardUiEvent.OnCloseFolderGroup)
    reportHiddenMove(report)
    noteRecoveryGap()
    if (report.failed == 0 && report.moved > 0) noteHiddenDone()
    if (event.lockAfter) lockHidden()
}

internal fun DashboardViewModel.hiddenTasksFor(everything: List<DownloadTask>, unlocked: Boolean): List<DownloadTask> =
    if (unlocked) everything.filter { it.hidden }.map { hiddenListed(it) } else emptyList()

internal fun DashboardViewModel.hiddenListed(task: DownloadTask): DownloadTask =
    if (HiddenFiles.isPrivate(queueRepository.pathResolver, task.destinationPath))
        task.copy(destinationPath = HiddenStore.originOf(task.id).orEmpty())
    else task

internal fun DashboardViewModel.applyHiddenCheck(check: HiddenCheck, unlockOnOk: Boolean) {
    _uiState.update { state ->
        val h = state.hidden
        state.copy(
            hidden = when (check) {
                HiddenCheck.Ok -> h.copy(lockType = HiddenStore.state.value.lockType, unlocked = h.unlocked || unlockOnOk, covered = if (unlockOnOk) false else h.covered, error = null, triesLeft = 0, lockedSeconds = 0)
                is HiddenCheck.Wrong -> h.copy(error = HiddenError.WRONG_PIN, triesLeft = check.triesLeft)
                is HiddenCheck.Locked -> h.copy(error = HiddenError.LOCKED, lockedSeconds = check.secondsLeft)
                HiddenCheck.Invalid -> h.copy(error = HiddenError.INVALID_PIN)
                HiddenCheck.SaveFailed -> h.copy(notice = HiddenNotice.MOVE_FAILED)
                HiddenCheck.NoLock -> h.copy(lockType = null, unlocked = false, error = null)
            }
        )
    }
    refreshHiddenTasks()
}

private fun DashboardViewModel.reauthHidden() {
    val auth = DeviceAuth.handler ?: return
    if (!_uiState.value.hidden.covered || reauthRunning) return
    reauthRunning = true
    scope.launch {
        val ok = try {
            auth(LocalizationManager.Strings.hiddenReauthReason)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        } finally {
            reauthRunning = false
        }
        if (ok) _uiState.update { it.copy(hidden = it.hidden.copy(covered = false)) }
    }
}

private fun DashboardViewModel.coverHidden() {
    if (!_uiState.value.hidden.unlocked) return
    closeHiddenPlayer()
    HiddenMedia.cleanup(queueRepository.pathResolver)
    val hiddenIds = queueRepository.allTasksFlow.value.filter { it.hidden }.mapTo(HashSet()) { it.id }
    _uiState.update { it.withoutHiddenDialogs(hiddenIds).let { s -> s.copy(hidden = s.hidden.copy(covered = true)) } }
}

private fun DashboardUiState.withoutHiddenDialogs(hiddenIds: Set<String>): DashboardUiState = copy(
    torrentDetails = torrentDetails?.takeIf { d -> d.taskId !in hiddenIds },
    archivePassword = archivePassword?.takeIf { a -> a.taskId !in hiddenIds },
    removeConfirmationTaskId = removeConfirmationTaskId?.takeIf { id -> id !in hiddenIds },
    removeConfirmationTaskIds = removeConfirmationTaskIds.filter { id -> id !in hiddenIds },
    deviceShareTaskId = deviceShareTaskId?.takeIf { id -> id !in hiddenIds }
)

internal fun DashboardViewModel.lockHidden() {
    HiddenStore.cancelPinReset()
    closeHiddenPlayer()
    HiddenMedia.cleanup(queueRepository.pathResolver)
    val hiddenIds = queueRepository.allTasksFlow.value.filter { it.hidden }.mapTo(HashSet()) { it.id }
    _uiState.update {
        it.withoutHiddenDialogs(hiddenIds).copy(
            hidden = HiddenUiState(
                lockType = HiddenStore.state.value.lockType,
                notice = it.hidden.notice,
                recoveryCode = it.hidden.recoveryCode,
                workDone = it.hidden.workDone,
                workTotal = it.hidden.workTotal,
                workFraction = it.hidden.workFraction
            ))
    }
}

internal fun DashboardViewModel.refreshHiddenTasks() {
    _uiState.update { state ->
        val h = state.hidden
        state.copy(hidden = h.copy(tasks = hiddenTasksFor(queueRepository.allTasksFlow.value, h.unlocked), lockType = HiddenStore.state.value.lockType))
    }
}
