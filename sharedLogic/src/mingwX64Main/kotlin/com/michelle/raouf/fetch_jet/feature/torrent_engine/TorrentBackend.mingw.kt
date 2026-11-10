package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

private object UnavailableTorrentBackend : TorrentBackend {
    override val isSupported: Boolean = false

    override fun engineStatus(): String = LocalizationManager.Strings.sysTorrentEngineNotAvailable

    override suspend fun installEngine(): Result<Unit> =
        Result.failure(UnsupportedOperationException(engineStatus()))

    override suspend fun fetchMetadata(magnetUri: String?, torrentBytes: ByteArray?): TorrentMetadata =
        throw UnsupportedOperationException(engineStatus())

    override suspend fun add(
        taskId: String,
        torrentBytes: ByteArray,
        savePath: String,
        selected: Set<Int>,
        priorities: Map<Int, Int>,
        disableUpload: Boolean,
        downloadLimitBytesPerSec: Long,
        uploadLimitBytesPerSec: Long
    ) = throw UnsupportedOperationException(engineStatus())

    override fun pause(taskId: String) {}

    override fun resume(taskId: String) {}

    override fun remove(taskId: String, deleteFiles: Boolean) {}

    override fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>) {}

    override fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long) {}

    override fun progress(taskId: String): TorrentProgress? = null

    override fun shutdown() {}
}

actual fun createTorrentBackend(): TorrentBackend = UnavailableTorrentBackend
