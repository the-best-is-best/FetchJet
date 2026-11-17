package com.michelle.raouf.fetch_jet.feature.report

import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask

data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    override fun compareTo(other: MonthKey): Int = compareValuesBy(this, other, { it.year }, { it.month })

    fun previous(): MonthKey = if (month == 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)

    val label: String get() = "$year-${month.toString().padStart(2, '0')}"
}

data class SiteShare(val site: String, val files: Int, val bytes: Long)

data class MonthReport(val month: MonthKey, val files: Int, val bytes: Long, val sites: List<SiteShare>)

object DownloadReport {
    fun monthOf(epochMs: Long, utcOffsetMs: Long = 0L): MonthKey {
        val days = (epochMs + utcOffsetMs).floorDiv(86_400_000L)
        val z = days + 719468
        val era = z.floorDiv(146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        return MonthKey(year, month)
    }

    fun siteOf(task: DownloadTask): String {
        val page = task.originalUrl?.takeIf { it.contains("://") }
        val host = SiteLogin.cleanSite(page ?: task.url)
        return if (host.isBlank() || task.url.startsWith("magnet:", ignoreCase = true)) "torrent" else host
    }

    fun months(tasks: List<DownloadTask>, utcOffsetMs: Long = 0L): List<MonthKey> =
        tasks.filter { it.status == DownloadStatus.COMPLETED && it.createdAtEpochMs > 0L }
            .map { monthOf(it.createdAtEpochMs, utcOffsetMs) }.distinct().sortedDescending()

    fun forMonth(tasks: List<DownloadTask>, month: MonthKey, utcOffsetMs: Long = 0L, topSites: Int = 5): MonthReport {
        val done = tasks.filter {
            it.status == DownloadStatus.COMPLETED && it.createdAtEpochMs > 0L && monthOf(it.createdAtEpochMs, utcOffsetMs) == month
        }
        val sites = done.groupBy { siteOf(it) }
            .map { (site, list) -> SiteShare(site, list.size, list.sumOf { it.totalBytes.coerceAtLeast(0L) }) }
            .sortedWith(compareByDescending<SiteShare> { it.bytes }.thenByDescending { it.files }.thenBy { it.site })
            .take(topSites)
        return MonthReport(month, done.size, done.sumOf { it.totalBytes.coerceAtLeast(0L) }, sites)
    }
}
