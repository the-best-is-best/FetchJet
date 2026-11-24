package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
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

fun hiddenWrongPinText(triesLeft: Int): String = wrongPinText(hiddenLockTexts(storedLockType()).wrong, triesLeft)

data class HiddenLockTexts(
    val label: String,
    val setTitle: String,
    val setHint: String,
    val resetTitle: String,
    val enter: String,
    val confirm: String,
    val mismatch: String,
    val invalid: String,
    val wrong: String,
    val oldLabel: String,
    val newLabel: String,
    val change: String,
    val remove: String,
    val removeWarn: String,
    val forgot: String,
    val switchTo: String,
    val changed: String
)

internal fun lockTextsOf(s: AppLocalization, type: HiddenLockType): HiddenLockTexts = when (type) {
    HiddenLockType.PIN -> HiddenLockTexts(
        label = s.hiddenLockTypePin, setTitle = s.hiddenSetPinTitle, setHint = s.hiddenSetPinHint, resetTitle = s.hiddenResetPinTitle,
        enter = s.hiddenEnterPin, confirm = s.hiddenConfirmPin, mismatch = s.hiddenPinMismatch, invalid = s.hiddenInvalidPin,
        wrong = s.hiddenWrongPin, oldLabel = s.hiddenOldPin, newLabel = s.hiddenNewPin, change = s.hiddenChangePin,
        remove = s.hiddenRemovePin, removeWarn = s.hiddenRemoveWarn, forgot = s.hiddenForgotPin, switchTo = s.hiddenUsePattern,
        changed = s.hiddenNoticeResetDone
    )
    HiddenLockType.PATTERN -> HiddenLockTexts(
        label = s.hiddenLockTypePattern, setTitle = s.hiddenPatternDraw, setHint = s.hiddenPatternTooShort, resetTitle = s.hiddenPatternDraw,
        enter = s.hiddenPatternEnter, confirm = s.hiddenPatternAgain, mismatch = s.hiddenPatternMismatch, invalid = s.hiddenPatternTooShort,
        wrong = s.hiddenWrongPattern, oldLabel = s.hiddenOldPattern, newLabel = s.hiddenNewPattern, change = s.hiddenChangePattern,
        remove = s.hiddenRemovePattern, removeWarn = s.hiddenRemovePatternWarn, forgot = s.hiddenForgotPattern, switchTo = s.hiddenUsePin,
        changed = s.hiddenNoticePatternChanged
    )
}

fun hiddenLockTexts(type: HiddenLockType): HiddenLockTexts = lockTextsOf(LocalizationManager.Strings, type)

private fun storedLockType(): HiddenLockType = HiddenStore.state.value.lockType ?: HiddenLockType.PIN

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
    HiddenNotice.RESET_DONE -> hiddenLockTexts(storedLockType()).changed
    HiddenNotice.RESET_EXPIRED -> LocalizationManager.Strings.hiddenNoticeResetExpired
    HiddenNotice.NEED_FILE_ACCESS -> LocalizationManager.Strings.hiddenNoticeNeedFileAccess
    HiddenNotice.NOT_MEDIA -> LocalizationManager.Strings.hiddenNoticeNotMedia
}
