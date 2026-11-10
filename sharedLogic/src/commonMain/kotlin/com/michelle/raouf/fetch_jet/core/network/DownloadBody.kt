package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlin.concurrent.Volatile

object DownloadBody {
    @Volatile
    var direct: Boolean = false
}

@OptIn(InternalAPI::class)
suspend fun HttpResponse.downloadChannel(): ByteReadChannel =
    if (DownloadBody.direct) rawContent else bodyAsChannel()
