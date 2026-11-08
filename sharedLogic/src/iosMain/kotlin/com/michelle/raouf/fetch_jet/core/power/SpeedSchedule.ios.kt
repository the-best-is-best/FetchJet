package com.michelle.raouf.fetch_jet.core.power

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate

actual fun localMinutesOfDay(): Int {
    val comps = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate()
    )
    return (comps.hour * 60L + comps.minute).toInt()
}
