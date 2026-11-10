package com.michelle.raouf.fetch_jet.feature.media_extractor

internal class LocalThrottlingProxy private constructor(val port: Int) {
    val totalDownloadedBytes: Long get() = 0L

    fun getInstantaneousDownloadSpeedBytesPerSec(): Long = 0L

    fun close() {}

    companion object {
        fun start(speedLimitProvider: (() -> Long)?): LocalThrottlingProxy? = null
    }
}
