package com.michelle.raouf.fetch_jet.core.edition

actual fun currentAppEdition(): AppEdition = AppEdition.FULL
actual fun platformSupportsTorrent(): Boolean = true
actual fun platformSupportsMediaExtractor(): Boolean = true
