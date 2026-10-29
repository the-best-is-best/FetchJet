package com.michelle.raouf.fetch_jet.core.util

/** Offset of the device's local time from UTC right now, in milliseconds (+3 h in Cairo). */
expect fun localUtcOffsetMs(): Long

/** Wall-clock time (epoch ms). */
@OptIn(kotlin.time.ExperimentalTime::class)
fun currentEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The next moment the local clock shows [hour]:[minute] (today if still ahead, else tomorrow). */
fun nextLocalTimeEpochMs(hour: Int, minute: Int, nowMs: Long = currentEpochMs()): Long {
    val offset = localUtcOffsetMs()
    val localNow = nowMs + offset
    val dayStart = localNow - localNow.mod(DAY_MS)
    var target = dayStart + (hour.coerceIn(0, 23) * 60L + minute.coerceIn(0, 59)) * 60_000L
    if (target <= localNow) target += DAY_MS
    return target - offset
}

/** "14:30", "tomorrow 02:00" or "in 3 days 08:00" for a time in the future, local clock. */
fun formatLocalSchedule(epochMs: Long, nowMs: Long = currentEpochMs()): String {
    val offset = localUtcOffsetMs()
    val local = epochMs + offset
    val minutesOfDay = local.mod(DAY_MS) / 60_000L
    val clock = "${(minutesOfDay / 60).toString().padStart(2, '0')}:${
        (minutesOfDay % 60).toString().padStart(2, '0')
    }"
    val days = local.floorDiv(DAY_MS) - (nowMs + offset).floorDiv(DAY_MS)
    return when {
        days <= 0L -> "today $clock"
        days == 1L -> "tomorrow $clock"
        else -> "in $days days, $clock"
    }
}
