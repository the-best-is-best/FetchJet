@file:OptIn(ExperimentalForeignApi::class, InternalAPI::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.callContext
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.engine.type
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlin.concurrent.AtomicInt
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException

class LinuxCurlConfig : HttpClientEngineConfig() {
    var sslVerify: Boolean = true
    var proxyUser: String = ""
    var proxyPassword: String = ""
    var caBundle: String? = linuxGetEnv("FETCHJET_CA_BUNDLE")?.takeIf { it.isNotBlank() }
    var readBufferBytes: Int = 128 * 1024
}

object LinuxCurlHttp : HttpClientEngineFactory<LinuxCurlConfig> {
    override fun create(block: LinuxCurlConfig.() -> Unit): HttpClientEngine = LinuxCurlEngine(LinuxCurlConfig().apply(block))

    override fun toString(): String = "LinuxCurl"
}

class LinuxCurlFailure(val code: Int, stage: String, detail: String) : IOException("$stage: $detail (curl $code)")

private const val DEFAULT_TIMEOUT_MS = 60_000L
private const val HTTP_1_1 = 2L
private const val ERROR_BUFFER_BYTES = 256

private val transfers = Dispatchers.IO.limitedParallelism(256)

private class CurlHead(val status: Int, val reason: String, val version: HttpProtocolVersion, val headers: Headers)

private class CurlTransfer(
    private val data: HttpRequestData,
    private val config: LinuxCurlConfig,
    private val body: ByteArray?,
) {
    val head = CompletableDeferred<CurlHead>()
    val channel = ByteChannel()
    private val aborted = AtomicInt(0)
    private var statusLine = ""
    private val lines = ArrayList<String>()

    fun abort() {
        aborted.value = 1
    }

    fun onHeader(line: String) {
        val text = line.trimEnd('\r', '\n')
        if (text.startsWith("HTTP/")) {
            statusLine = text
            lines.clear()
        } else if (text.isNotEmpty()) {
            lines.add(text)
        }
    }

    private fun deliverHead() {
        if (head.isCompleted) return
        val parts = statusLine.split(' ', limit = 3)
        val version = try {
            HttpProtocolVersion.parse(parts.getOrNull(0) ?: "HTTP/1.1")
        } catch (_: Throwable) {
            HttpProtocolVersion.HTTP_1_1
        }
        val headers = HeadersBuilder()
        for (line in lines) {
            val cut = line.indexOf(':')
            if (cut > 0) headers.append(line.substring(0, cut).trim(), line.substring(cut + 1).trim())
        }
        val status = parts.getOrNull(1)?.toIntOrNull() ?: 0
        head.complete(CurlHead(status, parts.getOrNull(2).orEmpty(), version, headers.build()))
    }

    fun onBody(bytes: ByteArray): Boolean {
        if (aborted.value != 0) return false
        deliverHead()
        return try {
            runBlocking {
                channel.writeFully(bytes)
                channel.flush()
            }
            aborted.value == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun keepGoing(): Boolean = aborted.value == 0

    fun run() {
        val self = StableRef.create(this)
        val errors = nativeHeap.allocArray<ByteVar>(ERROR_BUFFER_BYTES)
        errors[0] = 0
        var headerList: COpaquePointer? = null
        var bodyCopy: CPointer<ByteVar>? = null
        var handle: COpaquePointer? = null
        try {
            val curl = LinuxCurl.newHandle()
            handle = curl
            headerList = LinuxCurl.headerList(requestHeaders())
            bodyCopy = body?.takeIf { it.isNotEmpty() }?.let { bytes ->
                nativeHeap.allocArray<ByteVar>(bytes.size).also { for (i in bytes.indices) it[i] = bytes[i] }
            }
            configure(curl, self.asCPointer(), errors, headerList, bodyCopy)
            val code = LinuxCurl.perform(curl)
            if (code == CurlCode.OK) {
                deliverHead()
                runBlocking { channel.flushAndClose() }
            } else {
                fail(failure(code, errors.toKString()))
            }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            handle?.let { LinuxCurl.free(it) }
            LinuxCurl.freeHeaderList(headerList)
            bodyCopy?.let { nativeHeap.free(it) }
            nativeHeap.free(errors)
            self.dispose()
        }
    }

    private fun fail(cause: Throwable) {
        if (!head.isCompleted) head.completeExceptionally(cause)
        channel.cancel(cause)
    }

    private fun failure(code: Int, detail: String): Throwable {
        val text = detail.ifBlank { LinuxCurl.errorText(code) }
        if (code == CurlCode.ABORTED_BY_CALLBACK || code == CurlCode.WRITE_ERROR) {
            if (aborted.value != 0) return CancellationException("The request was cancelled")
        }
        val error = LinuxCurlFailure(code, if (head.isCompleted) "The download was interrupted" else "Could not get an answer", text)
        if (code == CurlCode.OPERATION_TIMEDOUT) {
            return if (head.isCompleted || statusLine.isNotEmpty()) SocketTimeoutException(data, error) else ConnectTimeoutException(data, error)
        }
        return error
    }

    private fun requestHeaders(): List<String> {
        val out = ArrayList<String>()
        val dropProxyLogin = config.proxyUser.isNotEmpty()
        mergeHeaders(data.headers, data.body) { name, value ->
            val skip = (body == null && name.equals(HttpHeaders.ContentLength, ignoreCase = true)) ||
                (dropProxyLogin && name.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true))
            if (!skip) out.add("$name: $value")
        }
        if (out.none { it.startsWith("Accept:", ignoreCase = true) }) out.add("Accept:")
        out.add("Expect:")
        return out
    }

    private fun configure(
        curl: COpaquePointer,
        self: COpaquePointer,
        errors: CPointer<ByteVar>,
        headerList: COpaquePointer?,
        bodyCopy: CPointer<ByteVar>?,
    ) {
        LinuxCurl.set(curl, CurlOpt.URL, data.url.toString())
        LinuxCurl.set(curl, CurlOpt.ERRORBUFFER, errors.reinterpret())
        LinuxCurl.set(curl, CurlOpt.NOSIGNAL, 1L)
        LinuxCurl.set(curl, CurlOpt.FOLLOWLOCATION, 0L)
        LinuxCurl.set(curl, CurlOpt.PATH_AS_IS, 1L)
        LinuxCurl.set(curl, CurlOpt.HTTP_VERSION, HTTP_1_1)
        LinuxCurl.set(curl, CurlOpt.TCP_KEEPALIVE, 1L)
        LinuxCurl.set(curl, CurlOpt.BUFFERSIZE, config.readBufferBytes.toLong())
        LinuxCurl.set(curl, CurlOpt.HTTPHEADER, headerList)
        LinuxCurl.set(curl, CurlOpt.WRITEFUNCTION, staticCFunction(::curlWrite).reinterpret())
        LinuxCurl.set(curl, CurlOpt.WRITEDATA, self)
        LinuxCurl.set(curl, CurlOpt.HEADERFUNCTION, staticCFunction(::curlHeader).reinterpret())
        LinuxCurl.set(curl, CurlOpt.HEADERDATA, self)
        LinuxCurl.set(curl, CurlOpt.NOPROGRESS, 0L)
        LinuxCurl.set(curl, CurlOpt.XFERINFOFUNCTION, staticCFunction(::curlProgress).reinterpret())
        LinuxCurl.set(curl, CurlOpt.XFERINFODATA, self)
        if (!config.sslVerify) {
            LinuxCurl.set(curl, CurlOpt.SSL_VERIFYPEER, 0L)
            LinuxCurl.set(curl, CurlOpt.SSL_VERIFYHOST, 0L)
        }
        config.caBundle?.let { LinuxCurl.set(curl, CurlOpt.CAINFO, it) }

        val timeouts = data.getCapabilityOrNull(HttpTimeoutCapability)
        timeoutMs(timeouts?.connectTimeoutMillis)?.let { LinuxCurl.set(curl, CurlOpt.CONNECTTIMEOUT_MS, it) }
        timeoutMs(timeouts?.socketTimeoutMillis)?.let {
            LinuxCurl.set(curl, CurlOpt.LOW_SPEED_LIMIT, 1L)
            LinuxCurl.set(curl, CurlOpt.LOW_SPEED_TIME, ((it + 999) / 1000).coerceAtLeast(1))
        }

        val proxy = config.proxy
        if (proxy != null) {
            val scheme = if (proxy.type == ProxyType.SOCKS) "socks5h" else "http"
            LinuxCurl.set(curl, CurlOpt.PROXY, "$scheme://${proxy.url.host}:${proxy.url.port}")
            LinuxCurl.set(curl, CurlOpt.SUPPRESS_CONNECT_HEADERS, 1L)
            if (config.proxyUser.isNotEmpty()) {
                LinuxCurl.set(curl, CurlOpt.PROXYUSERNAME, config.proxyUser)
                LinuxCurl.set(curl, CurlOpt.PROXYPASSWORD, config.proxyPassword)
            }
        }

        when (data.method) {
            HttpMethod.Get -> LinuxCurl.set(curl, CurlOpt.HTTPGET, 1L)
            HttpMethod.Head -> LinuxCurl.set(curl, CurlOpt.NOBODY, 1L)
            else -> {
                if (data.method == HttpMethod.Post) LinuxCurl.set(curl, CurlOpt.POST, 1L)
                LinuxCurl.set(curl, CurlOpt.CUSTOMREQUEST, data.method.value)
            }
        }
        if (bodyCopy != null && body != null) {
            if (data.method != HttpMethod.Post) LinuxCurl.set(curl, CurlOpt.POST, 1L)
            LinuxCurl.set(curl, CurlOpt.CUSTOMREQUEST, data.method.value)
            LinuxCurl.set(curl, CurlOpt.POSTFIELDSIZE_LARGE, body.size.toLong())
            LinuxCurl.set(curl, CurlOpt.POSTFIELDS, bodyCopy.reinterpret())
        }
    }

    private fun timeoutMs(value: Long?): Long? = when {
        value == null -> DEFAULT_TIMEOUT_MS
        value == HttpTimeoutConfig.INFINITE_TIMEOUT_MS || value <= 0L -> null
        else -> value
    }
}

private fun transferOf(pointer: COpaquePointer?): CurlTransfer? = try {
    pointer?.asStableRef<CurlTransfer>()?.get()
} catch (_: Throwable) {
    null
}

private fun curlWrite(data: CPointer<ByteVar>?, size: ULong, count: ULong, user: COpaquePointer?): ULong {
    val total = size * count
    val transfer = transferOf(user) ?: return 0uL
    if (total == 0uL || data == null) return total
    return if (transfer.onBody(data.readBytes(total.toInt()))) total else 0uL
}

private fun curlHeader(data: CPointer<ByteVar>?, size: ULong, count: ULong, user: COpaquePointer?): ULong {
    val total = size * count
    val transfer = transferOf(user) ?: return 0uL
    if (data != null && total > 0uL) {
        try {
            transfer.onHeader(data.readBytes(total.toInt()).decodeToString())
        } catch (_: Throwable) {
        }
    }
    return if (transfer.keepGoing()) total else 0uL
}

private fun curlProgress(user: COpaquePointer?, downTotal: Long, downNow: Long, upTotal: Long, upNow: Long): Int =
    if (transferOf(user)?.keepGoing() == true) 0 else 1

private class LinuxCurlEngine(override val config: LinuxCurlConfig) : HttpClientEngineBase("fetchjet-curl") {
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val bytes = bodyBytes(data.body, callContext)
        val transfer = CurlTransfer(data, config, bytes)
        callContext.job.invokeOnCompletion { transfer.abort() }
        GlobalScope.launch(transfers) { transfer.run() }
        val head = try {
            transfer.head.await()
        } catch (e: Throwable) {
            transfer.abort()
            throw e
        }
        return HttpResponseData(
            HttpStatusCode(head.status, head.reason), requestTime, head.headers, head.version, transfer.channel, callContext
        )
    }

    private suspend fun bodyBytes(body: OutgoingContent, callContext: CoroutineContext): ByteArray? = when (body) {
        is OutgoingContent.NoContent -> null
        is OutgoingContent.ByteArrayContent -> body.bytes()
        is OutgoingContent.ReadChannelContent -> body.readFrom().toByteArray()
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(callContext) { body.writeTo(channel) }.channel.toByteArray()
        is OutgoingContent.ContentWrapper -> bodyBytes(body.delegate(), callContext)
        is OutgoingContent.ProtocolUpgrade -> throw IOException("Protocol upgrades are not supported by this engine")
    }
}
