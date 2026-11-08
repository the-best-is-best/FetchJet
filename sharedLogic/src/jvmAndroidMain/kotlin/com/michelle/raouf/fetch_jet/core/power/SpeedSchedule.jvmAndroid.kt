package com.michelle.raouf.fetch_jet.core.power

import java.util.Calendar

actual fun localMinutesOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
