package com.michelle.raouf.fetch_jet.feature.report

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadReportTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun task(url: String, created: Long, bytes: Long, status: DownloadStatus = DownloadStatus.COMPLETED, page: String? = null) =
        DownloadTask(
            id = url + created, url = url, fileName = "f", destinationPath = "/d", engineType = EngineType.HTTP_DIRECT,
            status = status, totalBytes = bytes, createdAtEpochMs = created, originalUrl = page,
        )

    @Test
    fun monthsFollowTheCalendar() {
        assertEquals(MonthKey(2026, 11), DownloadReport.monthOf(at(2026, 11, 1, 0)))
        assertEquals(MonthKey(2026, 10), DownloadReport.monthOf(at(2026, 10, 31, 23)))
        assertEquals(MonthKey(2024, 2), DownloadReport.monthOf(at(2024, 2, 29)))
        assertEquals(MonthKey(2027, 1), DownloadReport.monthOf(at(2026, 12, 31, 23), utcOffsetMs = 2 * 3_600_000L))
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).previous())
    }

    @Test
    fun aMonthCountsOnlyFinishedDownloadsAndSortsSitesBySize() {
        val tasks = listOf(
            task("https://a.com/1.bin", at(2026, 11, 3), 100),
            task("https://cdn.a.com/2.bin", at(2026, 11, 4), 50),
            task("https://b.org/3.bin", at(2026, 11, 5), 500),
            task("https://b.org/4.bin", at(2026, 11, 6), 900, status = DownloadStatus.PAUSED),
            task("https://c.net/5.bin", at(2026, 10, 30), 70),
            task("https://video.cdn.net/x.mp4", at(2026, 11, 7), 30, page = "https://www.youtube.com/watch?v=1"),
        )
        val report = DownloadReport.forMonth(tasks, MonthKey(2026, 11))
        assertEquals(4, report.files)
        assertEquals(680, report.bytes)
        assertEquals(listOf("b.org", "a.com", "cdn.a.com", "youtube.com"), report.sites.map { it.site })
        assertEquals(listOf(MonthKey(2026, 11), MonthKey(2026, 10)), DownloadReport.months(tasks))
    }

    @Test
    fun daysMatchJavaTime() {
        var day = java.time.LocalDate.of(1999, 12, 25)
        repeat(4000) {
            val key = DayKey.fromEpochDay(day.toEpochDay())
            assertEquals(DayKey(day.year, day.monthValue, day.dayOfMonth), key)
            assertEquals(day.toEpochDay(), key.epochDay)
            assertEquals(day.dayOfWeek.value - 1, key.dayOfWeekFromMonday)
            day = day.plusDays(1)
        }
        assertEquals(DayKey(2024, 2, 29), DayKey.parse("2024-02-29"))
        assertEquals(null, DayKey.parse("2025-02-29"))
        assertEquals(null, DayKey.parse("2025-13-01"))
    }

    @Test
    fun periodsGiveTheirDays() {
        val today = DayKey(2026, 3, 5)
        assertEquals(DayKey(2026, 3, 1) to today, DownloadReport.rangeOf(ReportPeriod.THIS_MONTH, today))
        assertEquals(DayKey(2026, 2, 1) to DayKey(2026, 2, 28), DownloadReport.rangeOf(ReportPeriod.LAST_MONTH, today))
        assertEquals(DayKey(2026, 2, 27) to today, DownloadReport.rangeOf(ReportPeriod.LAST_7_DAYS, today))
        assertEquals(DayKey(2026, 2, 4) to today, DownloadReport.rangeOf(ReportPeriod.LAST_30_DAYS, today))
        assertEquals(DayKey(2026, 1, 1) to today, DownloadReport.rangeOf(ReportPeriod.THIS_YEAR, today))
        assertEquals(null to null, DownloadReport.rangeOf(ReportPeriod.ALL, today))
        assertEquals(
            DayKey(2025, 12, 1) to DayKey(2026, 1, 9),
            DownloadReport.rangeOf(ReportPeriod.CUSTOM, today, DayKey(2026, 1, 9), DayKey(2025, 12, 1))
        )
        assertEquals(DayKey(2025, 12, 1) to DayKey(2025, 12, 31), DownloadReport.rangeOf(ReportPeriod.LAST_MONTH, DayKey(2026, 1, 15)))
    }

    @Test
    fun aRangeIncludesBothEnds() {
        val tasks = listOf(
            task("https://a.com/1.bin", at(2026, 11, 1, 0), 100),
            task("https://a.com/2.bin", at(2026, 11, 10, 23), 200),
            task("https://b.org/3.bin", at(2026, 11, 11, 0), 400),
            task("https://c.net/4.bin", at(2026, 10, 31, 23), 800),
        )
        val report = DownloadReport.forRange(tasks, DayKey(2026, 11, 1), DayKey(2026, 11, 10))
        assertEquals(2, report.files)
        assertEquals(300, report.bytes)
        assertEquals(1, report.siteCount)
        val all = DownloadReport.forRange(tasks, null, null)
        assertEquals(4, all.files)
        assertEquals(3, all.siteCount)
    }

    @Test
    fun theCalendarStartsOnMonday() {
        val cells = DownloadReport.calendarDays(MonthKey(2026, 11))
        assertEquals(6, cells.indexOfFirst { it != null })
        assertEquals(DayKey(2026, 11, 1), cells[6])
        assertEquals(0, cells.size % 7)
        assertEquals(30, cells.count { it != null })
    }
}
