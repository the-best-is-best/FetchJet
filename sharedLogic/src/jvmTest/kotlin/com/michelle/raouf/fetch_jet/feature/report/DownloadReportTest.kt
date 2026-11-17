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
}
