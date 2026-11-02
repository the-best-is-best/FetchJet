package com.michelle.raouf.fetch_jet.feature.http_downloader

internal class ServerBusyException(message: String) : Exception(message)

internal data class ResolvedHttpMetadata(
    val finalUrl: String,
    val statusCode: Int,
    val statusDescription: String,
    val contentType: String,
    val contentLength: Long,
    val acceptRanges: Boolean,
    val contentDisposition: String?
)
