package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.win.winLocalMinutesOfDay

actual fun localMinutesOfDay(): Int = winLocalMinutesOfDay()
