package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
import com.michelle.raouf.fetch_jet.feature.report.DayKey
import com.michelle.raouf.fetch_jet.feature.report.DownloadReport
import com.michelle.raouf.fetch_jet.feature.report.MonthKey
import com.michelle.raouf.fetch_jet.feature.report.RangeReport
import com.michelle.raouf.fetch_jet.feature.report.ReportPeriod
import com.michelle.raouf.fetch_jet.nativeapp.dto.CalendarDayDto
import com.tbib.kmp_localization.LocalizationManager

internal const val REPORT_FIRST_LINE = 4

internal fun reportToday(): DayKey = DayKey.of(kotlin.time.Clock.System.now().toEpochMilliseconds(), localUtcOffsetMs())

internal fun reportPeriodLabel(period: ReportPeriod): String {
    val s = LocalizationManager.Strings
    return when (period) {
        ReportPeriod.THIS_MONTH -> s.reportThisMonth
        ReportPeriod.LAST_MONTH -> s.reportLastMonth
        ReportPeriod.LAST_7_DAYS -> s.reportLast7
        ReportPeriod.LAST_30_DAYS -> s.reportLast30
        ReportPeriod.THIS_YEAR -> s.reportThisYear
        ReportPeriod.ALL -> s.reportAllTime
        ReportPeriod.CUSTOM -> s.reportCustom
    }
}

internal fun monthName(month: MonthKey): String {
    val names = LocalizationManager.Strings.reportMonthNames.split(',')
    return (names.getOrNull(month.month - 1)?.trim() ?: month.month.toString()) + " " + month.year
}

internal fun dayText(day: DayKey): String {
    val names = LocalizationManager.Strings.reportMonthNames.split(',')
    return "${day.day} ${names.getOrNull(day.month - 1)?.trim() ?: day.month} ${day.year}"
}

internal fun weekdayNames(): List<String> =
    LocalizationManager.Strings.reportWeekdays.split(',').map { it.trim() }.let { if (it.size == 7) it else listOf("M", "T", "W", "T", "F", "S", "S") }

internal fun SettingsPage.reportRange(today: DayKey = reportToday()): Pair<DayKey?, DayKey?> =
    DownloadReport.rangeOf(reportPeriod, today, reportFrom?.let { DayKey.parse(it) }, reportTo?.let { DayKey.parse(it) })

internal fun Presenter.currentReport(page: SettingsPage): RangeReport {
    val (from, to) = page.reportRange()
    return DownloadReport.forRange(vm.uiState.value.allTasks, from, to, localUtcOffsetMs())
}

internal fun Presenter.reportSummary(): String {
    val page = settingsLocal.value.page
    val report = currentReport(page)
    return reportPeriodLabel(page.reportPeriod) + " · " + report.files + " · " + formatBytes(report.bytes)
}

internal fun reportCalendarDays(page: SettingsPage, today: DayKey): Pair<MonthKey, List<CalendarDayDto>> {
    val (from, to) = page.reportRange(today)
    val editing = if (page.reportEditing == "to") to else from
    val shown = page.reportShownMonth?.let { MonthKey.parse(it) } ?: (editing ?: today).monthKey
    val days = DownloadReport.calendarDays(shown).map { day ->
        if (day == null) CalendarDayDto(blank = true)
        else CalendarDayDto(
            label = day.day.toString(),
            id = "cal.day." + day.label,
            inRange = from != null && to != null && day >= from && day <= to,
            edge = day == from || day == to,
            today = day == today,
        )
    }
    return shown to days
}

internal fun Presenter.reportPick(index: Int) {
    val period = ReportPeriod.entries.getOrNull(index) ?: return
    val today = reportToday()
    settingsPage {
        if (period == ReportPeriod.CUSTOM) {
            val (from, to) = reportRange(today)
            copy(
                reportPeriod = period,
                reportFrom = reportFrom ?: (from ?: today.monthKey.firstDay).label,
                reportTo = reportTo ?: (to ?: today).label,
                reportEditing = "from",
                reportShownMonth = null,
            )
        } else {
            copy(reportPeriod = period, reportEditing = null, reportShownMonth = null)
        }
    }
}

internal fun Presenter.reportClick(id: String): Boolean {
    when {
        id == "reportFrom" || id == "reportTo" -> {
            val which = if (id == "reportFrom") "from" else "to"
            settingsPage { copy(reportEditing = if (reportEditing == which) null else which, reportShownMonth = null) }
        }

        id == "cal.prev" || id == "cal.next" -> {
            val (shown, _) = reportCalendarDays(settingsLocal.value.page, reportToday())
            val moved = if (id == "cal.prev") shown.previous() else shown.next()
            settingsPage { copy(reportShownMonth = moved.label) }
        }

        id.startsWith("cal.day.") -> {
            val day = DayKey.parse(id.removePrefix("cal.day.")) ?: return true
            settingsPage {
                if (reportEditing == "to") {
                    val from = reportFrom?.let { DayKey.parse(it) }
                    if (from != null && day < from) copy(reportFrom = day.label, reportTo = from.label, reportEditing = null)
                    else copy(reportTo = day.label, reportEditing = null)
                } else {
                    val to = reportTo?.let { DayKey.parse(it) }
                    if (to != null && day > to) copy(reportFrom = day.label, reportTo = day.label, reportEditing = "to", reportShownMonth = null)
                    else copy(reportFrom = day.label, reportEditing = "to", reportShownMonth = null)
                }
            }
        }

        else -> return false
    }
    return true
}
