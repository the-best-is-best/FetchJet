package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.platform.win.WinLoopbackProxy

internal class LocalThrottlingProxy private constructor(private val proxy: WinLoopbackProxy) {
    val port: Int get() = proxy.port

    val totalDownloadedBytes: Long get() = proxy.downloadedBytes

    val totalUploadedBytes: Long get() = proxy.uploadedBytes

    fun getInstantaneousDownloadSpeedBytesPerSec(): Long = proxy.downloadSpeedBytesPerSec()

    fun close() = proxy.close()

    companion object {
        fun start(speedLimitProvider: (() -> Long)?): LocalThrottlingProxy? = try {
            LocalThrottlingProxy(WinLoopbackProxy.start(speedLimit = speedLimitProvider))
        } catch (_: Throwable) {
            null
        }
    }
}
