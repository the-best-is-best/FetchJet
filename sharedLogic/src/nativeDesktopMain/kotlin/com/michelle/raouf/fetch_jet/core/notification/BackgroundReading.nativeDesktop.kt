package com.michelle.raouf.fetch_jet.core.notification

actual object BackgroundReading {
    actual fun begin(key: String, title: String, text: String) = Unit
    actual fun progress(key: String, text: String) = Unit
    actual fun end(key: String) = Unit
}
