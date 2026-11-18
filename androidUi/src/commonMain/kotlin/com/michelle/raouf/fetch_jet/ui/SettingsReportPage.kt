package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.feature.report.DayKey
import com.michelle.raouf.fetch_jet.feature.report.DownloadReport
import com.michelle.raouf.fetch_jet.feature.report.ReportPeriod
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.tbib.kmp_localization.LocalizationManager

private const val DAY_MS = 86_400_000L

internal fun reportPeriodText(period: ReportPeriod): String {
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

internal fun reportDayText(day: DayKey): String {
    val names = LocalizationManager.Strings.reportMonthNames.split(',')
    return "${day.day} ${names.getOrNull(day.month - 1)?.trim() ?: day.month} ${day.year}"
}

internal fun reportToday(): DayKey =
    DayKey.of(kotlin.time.Clock.System.now().toEpochMilliseconds(), localUtcOffsetMs())

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPage() {
    val s = LocalizationManager.Strings
    val tasks by QueueManagerRepository.getInstance().allTasksFlow.collectAsState()
    var period by rememberSaveable { mutableStateOf(ReportPeriod.THIS_MONTH) }
    var from by rememberSaveable { mutableStateOf<String?>(null) }
    var to by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val today = remember { reportToday() }
    val range = DownloadReport.rangeOf(period, today, from?.let { DayKey.parse(it) }, to?.let { DayKey.parse(it) })
    val report = remember(tasks, range) { DownloadReport.forRange(tasks, range.first, range.second, localUtcOffsetMs()) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsGroup(
            title = s.reportPeriod,
            footer = range.first?.let { a -> range.second?.let { b -> if (a == b) reportDayText(a) else reportDayText(a) + "  –  " + reportDayText(b) } }
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = p == period,
                        onClick = {
                            if (p == ReportPeriod.CUSTOM) {
                                from = from ?: (range.first ?: today.monthKey.firstDay).label
                                to = to ?: (range.second ?: today).label
                                picking = true
                            }
                            period = p
                        },
                        label = { Text(reportPeriodText(p)) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
            if (period == ReportPeriod.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                        Text(s.reportFrom + " · " + (range.first?.let { reportDayText(it) } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                        Text(s.reportTo + " · " + (range.second?.let { reportDayText(it) } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(report.files.toString(), s.reportFiles, highlight = true, modifier = Modifier.weight(1f))
            StatTile(formatBytes(report.bytes), s.reportSize, modifier = Modifier.weight(1f))
            StatTile(report.siteCount.toString(), s.reportSites, modifier = Modifier.weight(1f))
        }

        SettingsGroup(title = s.reportTopSites, footer = s.reportFooter) {
            if (report.sites.isEmpty()) {
                SettingRow(title = if (tasks.isEmpty()) s.reportEmpty else s.reportNothingInPeriod)
                return@SettingsGroup
            }
            val top = report.sites.maxOf { it.bytes }.coerceAtLeast(1L)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                report.sites.forEachIndexed { i, site ->
                    SiteBar(i, site.site, formatBytes(site.bytes) + " · " + site.files, (site.bytes.toFloat() / top).coerceIn(0.02f, 1f))
                }
            }
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range.first?.let { it.epochDay * DAY_MS },
            initialSelectedEndDateMillis = range.second?.let { it.epochDay * DAY_MS },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val a = state.selectedStartDateMillis?.let { DayKey.fromEpochDay(it.floorDiv(DAY_MS)) }
                        val b = state.selectedEndDateMillis?.let { DayKey.fromEpochDay(it.floorDiv(DAY_MS)) } ?: a
                        if (a != null && b != null) {
                            from = a.label
                            to = b.label
                            period = ReportPeriod.CUSTOM
                        }
                        picking = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(s.cancel) } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp), showModeToggle = false)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, primary.copy(alpha = if (highlight) 0.35f else 0.14f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(primary.copy(alpha = if (highlight) 0.16f else 0.07f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                )
            )
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SiteBar(index: Int, site: String, value: String, fraction: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(22.dp).background(
                    if (index == 0) primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (index + 1).toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (index == 0) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                site,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(
                    if (index == 0) Brush.horizontalGradient(listOf(primary, MaterialTheme.colorScheme.secondary))
                    else Brush.horizontalGradient(listOf(primary.copy(alpha = 0.55f), primary.copy(alpha = 0.55f))),
                    RoundedCornerShape(4.dp)
                )
            )
        }
    }
}
