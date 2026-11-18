package com.michelle.raouf.fetch_jet.feature.report

import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask

data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    override fun compareTo(other: MonthKey): Int = compareValuesBy(this, other, { it.year }, { it.month })

    fun previous(): MonthKey = if (month == 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)

    fun next(): MonthKey = if (month == 12) MonthKey(year + 1, 1) else MonthKey(year, month + 1)

    val label: String get() = "$year-${month.toString().padStart(2, '0')}"

    val firstDay: DayKey get() = DayKey(year, month, 1)

    val length: Int
        get() = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    companion object {
        fun parse(text: String): MonthKey? {
            val parts = text.trim().split('-')
            if (parts.size != 2) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (m in 1..12 && y in 1970..9999) MonthKey(y, m) else null
        }
    }
}

data class DayKey(val year: Int, val month: Int, val day: Int) : Comparable<DayKey> {
    override fun compareTo(other: DayKey): Int = epochDay.compareTo(other.epochDay)

    val label: String get() = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    val monthKey: MonthKey get() = MonthKey(year, month)

    val epochDay: Long
        get() {
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468L
        }

    val dayOfWeekFromMonday: Int get() = ((epochDay + 3).mod(7L)).toInt()

    fun plusDays(days: Long): DayKey = fromEpochDay(epochDay + days)

    companion object {
        fun fromEpochDay(epochDay: Long): DayKey {
            val z = epochDay + 719468
            val era = z.floorDiv(146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
            return DayKey(year, month, day)
        }

        fun of(epochMs: Long, utcOffsetMs: Long = 0L): DayKey = fromEpochDay((epochMs + utcOffsetMs).floorDiv(86_400_000L))

        fun parse(text: String): DayKey? {
            val parts = text.trim().split('-')
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || y !in 1970..9999) return null
            return if (d in 1..MonthKey(y, m).length) DayKey(y, m, d) else null
        }
    }
}

enum class ReportPeriod { THIS_MONTH, LAST_MONTH, LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR, ALL, CUSTOM }

data class SiteShare(val site: String, val files: Int, val bytes: Long)

data class MonthReport(val month: MonthKey, val files: Int, val bytes: Long, val sites: List<SiteShare>)

data class RangeReport(val from: DayKey?, val to: DayKey?, val files: Int, val bytes: Long, val siteCount: Int, val sites: List<SiteShare>)

object DownloadReport {
    const val ENABLED = false

    fun monthOf(epochMs: Long, utcOffsetMs: Long = 0L): MonthKey = DayKey.of(epochMs, utcOffsetMs).monthKey

    fun siteOf(task: DownloadTask): String {
        val page = task.originalUrl?.takeIf { it.contains("://") }
        val host = SiteLogin.cleanSite(page ?: task.url)
        return if (host.isBlank() || task.url.startsWith("magnet:", ignoreCase = true)) "torrent" else host
    }

    private fun finished(tasks: List<DownloadTask>) =
        tasks.filter { it.status == DownloadStatus.COMPLETED && it.createdAtEpochMs > 0L }

    fun months(tasks: List<DownloadTask>, utcOffsetMs: Long = 0L): List<MonthKey> =
        finished(tasks).map { monthOf(it.createdAtEpochMs, utcOffsetMs) }.distinct().sortedDescending()

    private fun sitesOf(done: List<DownloadTask>, topSites: Int): Pair<Int, List<SiteShare>> {
        val all = done.groupBy { siteOf(it) }
            .map { (site, list) -> SiteShare(site, list.size, list.sumOf { it.totalBytes.coerceAtLeast(0L) }) }
            .sortedWith(compareByDescending<SiteShare> { it.bytes }.thenByDescending { it.files }.thenBy { it.site })
        return all.size to all.take(topSites)
    }

    fun forMonth(tasks: List<DownloadTask>, month: MonthKey, utcOffsetMs: Long = 0L, topSites: Int = 5): MonthReport {
        val done = finished(tasks).filter { monthOf(it.createdAtEpochMs, utcOffsetMs) == month }
        return MonthReport(month, done.size, done.sumOf { it.totalBytes.coerceAtLeast(0L) }, sitesOf(done, topSites).second)
    }

    fun rangeOf(period: ReportPeriod, today: DayKey, customFrom: DayKey? = null, customTo: DayKey? = null): Pair<DayKey?, DayKey?> =
        when (period) {
            ReportPeriod.THIS_MONTH -> today.monthKey.firstDay to today
            ReportPeriod.LAST_MONTH -> today.monthKey.previous().let { it.firstDay to DayKey(it.year, it.month, it.length) }
            ReportPeriod.LAST_7_DAYS -> today.plusDays(-6) to today
            ReportPeriod.LAST_30_DAYS -> today.plusDays(-29) to today
            ReportPeriod.THIS_YEAR -> DayKey(today.year, 1, 1) to today
            ReportPeriod.ALL -> null to null
            ReportPeriod.CUSTOM -> {
                val from = customFrom ?: today.monthKey.firstDay
                val to = customTo ?: today
                if (from <= to) from to to else to to from
            }
        }

    fun forRange(
        tasks: List<DownloadTask>,
        from: DayKey?,
        to: DayKey?,
        utcOffsetMs: Long = 0L,
        topSites: Int = 5,
    ): RangeReport {
        val done = finished(tasks).filter {
            val day = DayKey.of(it.createdAtEpochMs, utcOffsetMs)
            (from == null || day >= from) && (to == null || day <= to)
        }
        val (count, sites) = sitesOf(done, topSites)
        return RangeReport(from, to, done.size, done.sumOf { it.totalBytes.coerceAtLeast(0L) }, count, sites)
    }

    fun calendarDays(month: MonthKey): List<DayKey?> {
        val lead = month.firstDay.dayOfWeekFromMonday
        val cells = ArrayList<DayKey?>(42)
        repeat(lead) { cells.add(null) }
        for (d in 1..month.length) cells.add(DayKey(month.year, month.month, d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }
}
