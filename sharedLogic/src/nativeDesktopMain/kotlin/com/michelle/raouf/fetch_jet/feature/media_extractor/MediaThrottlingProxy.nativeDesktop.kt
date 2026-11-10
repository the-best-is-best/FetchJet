package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.platform.proxy.LoopbackProxy

internal interface LocalThrottlingProxy {
    val port: Int
    val totalDownloadedBytes: Long
    val totalUploadedBytes: Long
    fun getInstantaneousDownloadSpeedBytesPerSec(): Long
    fun close()

    companion object {
        fun start(speedLimitProvider: (() -> Long)?): LocalThrottlingProxy? = startThrottlingProxy(speedLimitProvider)
    }
}

private class LoopThrottlingProxy(private val proxy: LoopbackProxy) : LocalThrottlingProxy {
    override val port: Int get() = proxy.port
    override val totalDownloadedBytes: Long get() = proxy.downloadedBytes
    override val totalUploadedBytes: Long get() = proxy.uploadedBytes
    override fun getInstantaneousDownloadSpeedBytesPerSec(): Long = proxy.downloadSpeedBytesPerSec()
    override fun close() = proxy.close()
}

internal fun startThrottlingProxy(speedLimitProvider: (() -> Long)?): LocalThrottlingProxy? = try {
    LoopThrottlingProxy(LoopbackProxy.start(speedLimit = speedLimitProvider))
} catch (_: Throwable) {
    null
}
