package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

internal fun workingText(template: String, done: Int, total: Int): String =
    template
        .replace("{n}", (done + 1).coerceAtMost(total).toString())
        .replace("{total}", total.toString())

internal fun lockedText(template: String, seconds: Int): String = template.replace("{n}", seconds.toString())

internal fun wrongPinText(template: String, triesLeft: Int): String = template.replace("{n}", triesLeft.toString())

internal fun openingText(template: String, fraction: Float): String =
    template.removeSuffix("...").removeSuffix("…").trimEnd() + " " + (fraction.coerceIn(0f, 1f) * 100).toInt() + "%"

fun hiddenWorkingText(done: Int, total: Int): String = workingText(LocalizationManager.Strings.hiddenWorking, done, total)

fun hiddenLockedText(seconds: Int): String = lockedText(LocalizationManager.Strings.hiddenLockedFor, seconds)

fun hiddenWrongPinText(triesLeft: Int): String = wrongPinText(LocalizationManager.Strings.hiddenWrongPin, triesLeft)

fun hiddenOpeningText(fraction: Float): String = openingText(LocalizationManager.Strings.hiddenOpening, fraction)

fun hiddenNoticeText(notice: HiddenNotice): String = when (notice) {
    HiddenNotice.COPY_LEFT -> LocalizationManager.Strings.hiddenNoticeCopyLeft
    HiddenNotice.MOVE_FAILED -> LocalizationManager.Strings.hiddenNoticeMoveFailed
    HiddenNotice.OPEN_FAILED -> LocalizationManager.Strings.hiddenNoticeOpenFailed
    HiddenNotice.HIDDEN_DONE -> LocalizationManager.Strings.hiddenHiddenToast
    HiddenNotice.RESTORE_DONE -> LocalizationManager.Strings.hiddenNoticeRestoreDone
    HiddenNotice.RESTORE_WRONG_CODE -> LocalizationManager.Strings.hiddenNoticeRestoreWrong
    HiddenNotice.RESTORE_NOTHING -> LocalizationManager.Strings.hiddenNoticeRestoreNothing
    HiddenNotice.RECOVERY_UNAVAILABLE -> LocalizationManager.Strings.hiddenNoticeRecoveryUnavailable
    HiddenNotice.AUTH_FAILED -> LocalizationManager.Strings.hiddenNoticeAuthFailed
    HiddenNotice.RESET_DONE -> LocalizationManager.Strings.hiddenNoticeResetDone
    HiddenNotice.RESET_EXPIRED -> LocalizationManager.Strings.hiddenNoticeResetExpired
    HiddenNotice.NEED_FILE_ACCESS -> LocalizationManager.Strings.hiddenNoticeNeedFileAccess
}
