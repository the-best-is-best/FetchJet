@file:OptIn(ExperimentalForeignApi::class, InternalAPI::class, DelicateCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.callContext
import io.ktor.client.engine.engines
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.engine.type
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.fullPath
import io.ktor.http.isSecure
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import platform.windows.GetLastError
import platform.winhttp.WINHTTP_ASYNC_RESULT
import platform.winhttp.WinHttpCloseHandle
import platform.winhttp.WinHttpConnect
import platform.winhttp.WinHttpOpen
import platform.winhttp.WinHttpOpenRequest
import platform.winhttp.WinHttpQueryHeaders
import platform.winhttp.WinHttpReadData
import platform.winhttp.WinHttpReceiveResponse
import platform.winhttp.WinHttpSendRequest
import platform.winhttp.WinHttpSetCredentials
import platform.winhttp.WinHttpSetOption
import platform.winhttp.WinHttpSetStatusCallback
import platform.winhttp.WinHttpSetTimeouts

class WinHttpException(val code: UInt, val stage: String, detail: String = "") :
    IOException("$stage: ${winHttpErrorText(code)}$detail")

class WinHttpConfig : HttpClientEngineConfig() {
    var sslVerify: Boolean = true
    var proxyUser: String = ""
    var proxyPassword: String = ""
    var readBufferBytes: Int = 128 * 1024
}

object WinHttpNative : HttpClientEngineFactory<WinHttpConfig> {
    private val registered = AtomicInt(0)

    override fun create(block: WinHttpConfig.() -> Unit): HttpClientEngine = WinHttpNativeEngine(WinHttpConfig().apply(block))

    fun registerAsDefault() {
        if (registered.compareAndSet(0, 1)) engines.append(this)
    }

    override fun toString(): String = "WinHttpNative"
}

private const val ACCESS_NO_PROXY = 1u
private const val ACCESS_NAMED_PROXY = 3u
private const val FLAG_ASYNC = 0x10000000u
private const val FLAG_SECURE = 0x00800000u
private const val FLAG_ESCAPE_DISABLE = 0x00000040u
private const val FLAG_ESCAPE_DISABLE_QUERY = 0x00000080u
private const val FLAG_NULL_CODEPAGE = 0x00000008u
private const val CALLBACK_SENDREQUEST_COMPLETE = 0x00400000u
private const val CALLBACK_HEADERS_AVAILABLE = 0x00020000u
private const val CALLBACK_READ_COMPLETE = 0x00080000u
private const val CALLBACK_REQUEST_ERROR = 0x00200000u
private const val CALLBACK_SECURE_FAILURE = 0x00010000u
private const val CALLBACK_HANDLE_CLOSING = 0x00000800u
private const val NOTIFY_FLAGS = 0x007F0C00u
private const val OPTION_CONTEXT_VALUE = 45u
private const val OPTION_DISABLE_FEATURE = 63u
private const val OPTION_SECURITY_FLAGS = 31u
private const val OPTION_RECEIVE_RESPONSE_TIMEOUT = 7u
private const val OPTION_MAX_RESPONSE_DRAIN_SIZE = 92u
private const val DISABLE_COOKIES = 0x1u
private const val DISABLE_REDIRECTS = 0x2u
private const val DISABLE_AUTHENTICATION = 0x4u
private const val IGNORE_CERT_ERRORS = 0x00003300u
private const val QUERY_STATUS_CODE_NUMBER = 0x20000013u
private const val QUERY_VERSION = 18u
private const val QUERY_RAW_HEADERS_CRLF = 22u
private const val AUTH_TARGET_PROXY = 1u
private const val AUTH_SCHEME_BASIC = 1u
private const val ERROR_TIMEOUT = 12002u
private const val ERROR_OPERATION_CANCELLED = 12017u
private const val ERROR_SECURE_FAILURE = 12175u
private const val ERROR_INSUFFICIENT_BUFFER = 122u
private const val DEFAULT_TIMEOUT_MS = 60_000

fun winHttpErrorText(code: UInt): String = when (code) {
    12002u -> "The connection timed out"
    12005u -> "The address is not valid"
    12006u -> "The address scheme is not supported"
    12007u -> "The server name could not be resolved"
    12009u -> "A request option is not valid"
    12017u -> "The request was cancelled"
    12029u -> "Could not connect to the server"
    12030u -> "The connection was closed by the server"
    12031u -> "The connection was reset"
    12044u -> "The server asks for a client certificate"
    12152u -> "The server sent an invalid response"
    12156u -> "Too many redirects"
    12157u -> "A secure channel error occurred"
    12175u -> "The secure connection could not be trusted"
    12178u -> "The proxy settings could not be found"
    12180u -> "The proxy could not be found"
    else -> winErrorText(code)
} + " ($code)"

private fun secureFailureText(flags: UInt): String {
    val parts = ArrayList<String>()
    if (flags and 0x1u != 0u) parts.add("revocation check failed")
    if (flags and 0x2u != 0u) parts.add("invalid certificate")
    if (flags and 0x4u != 0u) parts.add("certificate revoked")
    if (flags and 0x8u != 0u) parts.add("untrusted certificate authority")
    if (flags and 0x10u != 0u) parts.add("certificate name does not match")
    if (flags and 0x20u != 0u) parts.add("certificate expired or not yet valid")
    if (flags and 0x40u != 0u) parts.add("certificate used for the wrong purpose")
    if (flags and 0x80000000u != 0u) parts.add("secure channel error")
    return if (parts.isEmpty()) "" else ": " + parts.joinToString(", ")
}

private class Waiter(val event: UInt, val continuation: CancellableContinuation<Int>, val stage: String)

private class WinHttpCall(private val session: COpaquePointer, private val data: HttpRequestData, private val config: WinHttpConfig) {
    private val self = StableRef.create(this)
    private val closed = AtomicInt(0)
    private val waiter = AtomicReference<Waiter?>(null)
    private val secureFailure = AtomicInt(0)
    private var connect: COpaquePointer? = null
    private var request: COpaquePointer? = null
    private var pinnedBody: Pinned<ByteArray>? = null
    private var receiving = false

    fun open() {
        val url = data.url
        connect = WinHttpConnect(session, url.host, url.port.toUShort(), 0u)
            ?: throw WinHttpException(GetLastError(), "Could not open the connection")
        var flags = FLAG_ESCAPE_DISABLE or FLAG_ESCAPE_DISABLE_QUERY or FLAG_NULL_CODEPAGE
        if (url.protocol.isSecure()) flags = flags or FLAG_SECURE
        val handle = WinHttpOpenRequest(connect, data.method.value, url.fullPath, "HTTP/1.1", null, null, flags)
            ?: throw WinHttpException(GetLastError(), "Could not open the request")
        request = handle
        memScoped {
            val context = alloc<ULongVar>()
            context.value = self.asCPointer().toLong().toULong()
            WinHttpSetOption(handle, OPTION_CONTEXT_VALUE, context.ptr, sizeOf<ULongVar>().toUInt())
            val number = alloc<UIntVar>()
            number.value = DISABLE_COOKIES or DISABLE_REDIRECTS or if (config.proxyUser.isEmpty()) DISABLE_AUTHENTICATION else 0u
            WinHttpSetOption(handle, OPTION_DISABLE_FEATURE, number.ptr, sizeOf<UIntVar>().toUInt())
            number.value = 0u
            WinHttpSetOption(handle, OPTION_MAX_RESPONSE_DRAIN_SIZE, number.ptr, sizeOf<UIntVar>().toUInt())
            if (!config.sslVerify) {
                number.value = IGNORE_CERT_ERRORS
                WinHttpSetOption(handle, OPTION_SECURITY_FLAGS, number.ptr, sizeOf<UIntVar>().toUInt())
            }
            val timeouts = data.getCapabilityOrNull(HttpTimeoutCapability)
            val connectMs = timeoutMs(timeouts?.connectTimeoutMillis)
            val socketMs = timeoutMs(timeouts?.socketTimeoutMillis)
            WinHttpSetTimeouts(handle, connectMs, connectMs, socketMs, socketMs)
            number.value = if (socketMs == 0) UInt.MAX_VALUE else socketMs.toUInt()
            WinHttpSetOption(handle, OPTION_RECEIVE_RESPONSE_TIMEOUT, number.ptr, sizeOf<UIntVar>().toUInt())
        }
        if (config.proxyUser.isNotEmpty()) {
            WinHttpSetCredentials(handle, AUTH_TARGET_PROXY, AUTH_SCHEME_BASIC, config.proxyUser, config.proxyPassword, null)
        }
    }

    private fun timeoutMs(value: Long?): Int = when {
        value == null -> DEFAULT_TIMEOUT_MS
        value == HttpTimeoutConfig.INFINITE_TIMEOUT_MS || value <= 0L -> 0
        value > Int.MAX_VALUE -> Int.MAX_VALUE
        else -> value.toInt()
    }

    private suspend fun await(event: UInt, stage: String, start: () -> Boolean): Int =
        suspendCancellableCoroutine { continuation ->
            val mine = Waiter(event, continuation, stage)
            waiter.value = mine
            continuation.invokeOnCancellation { close() }
            if (closed.value != 0) {
                if (waiter.compareAndSet(mine, null)) continuation.resumeWithException(WinHttpException(ERROR_OPERATION_CANCELLED, stage))
                return@suspendCancellableCoroutine
            }
            if (!start()) {
                val error = GetLastError()
                if (waiter.compareAndSet(mine, null)) continuation.resumeWithException(failure(error, stage))
            }
        }

    private fun failure(code: UInt, stage: String): Throwable {
        if (code == ERROR_TIMEOUT) {
            val cause = WinHttpException(code, stage)
            return if (receiving) SocketTimeoutException(data, cause) else ConnectTimeoutException(data, cause)
        }
        val detail = if (code == ERROR_SECURE_FAILURE) secureFailureText(secureFailure.value.toUInt()) else ""
        return WinHttpException(code, stage, detail)
    }

    fun onStatus(handle: COpaquePointer?, status: UInt, info: COpaquePointer?, length: UInt) {
        when (status) {
            CALLBACK_SECURE_FAILURE -> secureFailure.value = info?.reinterpret<UIntVar>()?.pointed?.value?.toInt() ?: 0
            CALLBACK_REQUEST_ERROR -> {
                val code = info?.reinterpret<WINHTTP_ASYNC_RESULT>()?.pointed?.dwError ?: 0u
                val current = waiter.getAndSet(null) ?: return
                current.continuation.resumeWithException(failure(code, current.stage))
            }

            CALLBACK_SENDREQUEST_COMPLETE, CALLBACK_HEADERS_AVAILABLE, CALLBACK_READ_COMPLETE -> {
                val current = waiter.value ?: return
                if (current.event != status || !waiter.compareAndSet(current, null)) return
                current.continuation.resume(if (status == CALLBACK_READ_COMPLETE) length.toInt() else 0)
            }

            CALLBACK_HANDLE_CLOSING -> if (handle == request) release()
        }
    }

    suspend fun send(headers: String, body: ByteArray?) {
        val pinned = if (body != null && body.isNotEmpty()) body.pin() else null
        pinnedBody = pinned
        val size = (body?.size ?: 0).toUInt()
        await(CALLBACK_SENDREQUEST_COMPLETE, "Could not send the request") {
            WinHttpSendRequest(
                request, headers, headers.length.toUInt(), pinned?.addressOf(0), size, size,
                self.asCPointer().toLong().toULong()
            ) != 0
        }
        receiving = true
        await(CALLBACK_HEADERS_AVAILABLE, "No answer from the server") { WinHttpReceiveResponse(request, null) != 0 }
    }

    suspend fun read(buffer: Pinned<ByteArray>, size: Int): Int =
        await(CALLBACK_READ_COMPLETE, "The download was interrupted") {
            WinHttpReadData(request, buffer.addressOf(0), size.toUInt(), null) != 0
        }

    fun statusCode(): Int = memScoped {
        val code = alloc<UIntVar>()
        val size = alloc<UIntVar>()
        size.value = sizeOf<UIntVar>().toUInt()
        if (WinHttpQueryHeaders(request, QUERY_STATUS_CODE_NUMBER, null as String?, code.ptr, size.ptr, null) == 0) {
            throw WinHttpException(GetLastError(), "Could not read the status")
        }
        code.value.toInt()
    }

    fun header(query: UInt): String = memScoped {
        val size = alloc<UIntVar>()
        size.value = 0u
        if (WinHttpQueryHeaders(request, query, null as String?, null, size.ptr, null) == 0) {
            val error = GetLastError()
            if (error != ERROR_INSUFFICIENT_BUFFER) throw WinHttpException(error, "Could not read the headers")
        }
        val buffer = allocArray<UShortVar>(size.value.toInt() / 2 + 2)
        if (WinHttpQueryHeaders(request, query, null as String?, buffer, size.ptr, null) == 0) {
            throw WinHttpException(GetLastError(), "Could not read the headers")
        }
        buffer.toKStringFromUtf16()
    }

    fun close() {
        if (!closed.compareAndSet(0, 1)) return
        val handle = request
        if (handle != null) WinHttpCloseHandle(handle) else release()
        connect?.let { WinHttpCloseHandle(it) }
    }

    private val released = AtomicInt(0)

    private fun release() {
        if (!released.compareAndSet(0, 1)) return
        waiter.getAndSet(null)?.let {
            it.continuation.resumeWithException(WinHttpException(ERROR_OPERATION_CANCELLED, it.stage))
        }
        pinnedBody?.unpin()
        pinnedBody = null
        self.dispose()
    }
}

private fun winHttpStatus(handle: COpaquePointer?, context: ULong, status: UInt, info: COpaquePointer?, length: UInt) {
    if (context == 0uL) return
    try {
        val call = context.toLong().toCPointer<CPointed>()?.asStableRef<WinHttpCall>()?.get() ?: return
        call.onStatus(handle, status, info, length)
    } catch (_: Throwable) {
    }
}

private class WinHttpNativeEngine(override val config: WinHttpConfig) : HttpClientEngineBase("fetchjet-winhttp") {
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    private val session: COpaquePointer

    init {
        val proxy = config.proxy
        val opened = if (proxy == null) {
            WinHttpOpen(null, ACCESS_NO_PROXY, null, null, FLAG_ASYNC)
        } else {
            val address = "${proxy.url.host}:${proxy.url.port}"
            val list = if (proxy.type == ProxyType.SOCKS) "socks=$address" else address
            WinHttpOpen(null, ACCESS_NAMED_PROXY, list, "<-loopback>", FLAG_ASYNC)
        }
        session = opened ?: throw WinHttpException(GetLastError(), "Could not start WinHTTP")
        WinHttpSetStatusCallback(session, staticCFunction(::winHttpStatus), NOTIFY_FLAGS, 0u)
        coroutineContext.job.invokeOnCompletion { WinHttpCloseHandle(session) }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val call = WinHttpCall(session, data, config)
        callContext.job.invokeOnCompletion { call.close() }
        call.open()
        val bytes = bodyBytes(data.body, callContext)
        val headers = StringBuilder()
        mergeHeaders(data.headers, data.body) { name, value ->
            if (bytes != null || !name.equals("Content-Length", ignoreCase = true)) {
                headers.append(name).append(": ").append(value).append("\r\n")
            }
        }
        call.send(headers.toString(), bytes)
        val status = call.statusCode()
        val version = try {
            HttpProtocolVersion.parse(call.header(QUERY_VERSION))
        } catch (_: Throwable) {
            HttpProtocolVersion.HTTP_1_1
        }
        val lines = call.header(QUERY_RAW_HEADERS_CRLF).split("\r\n")
        val reason = lines.firstOrNull()?.split(' ', limit = 3)?.getOrNull(2).orEmpty()
        val responseHeaders = HeadersBuilder()
        for (line in lines.drop(1)) {
            val cut = line.indexOf(':')
            if (cut > 0) responseHeaders.append(line.substring(0, cut).trim(), line.substring(cut + 1).trim())
        }
        val body = readBody(call, callContext)
        return HttpResponseData(HttpStatusCode(status, reason), requestTime, responseHeaders.build(), version, body, callContext)
    }

    private suspend fun bodyBytes(body: OutgoingContent, callContext: CoroutineContext): ByteArray? = when (body) {
        is OutgoingContent.NoContent -> null
        is OutgoingContent.ByteArrayContent -> body.bytes()
        is OutgoingContent.ReadChannelContent -> body.readFrom().toByteArray()
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(callContext) { body.writeTo(channel) }.channel.toByteArray()
        is OutgoingContent.ContentWrapper -> bodyBytes(body.delegate(), callContext)
        is OutgoingContent.ProtocolUpgrade -> throw IOException("Protocol upgrades are not supported by this engine")
    }

    private fun readBody(call: WinHttpCall, callContext: CoroutineContext): ByteReadChannel =
        GlobalScope.writer(callContext) {
            val buffer = ByteArray(config.readBufferBytes)
            buffer.usePinned { pinned ->
                while (true) {
                    val read = call.read(pinned, buffer.size)
                    if (read <= 0) break
                    channel.writeFully(buffer, 0, read)
                    channel.flush()
                }
            }
        }.channel
}

@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused", "DEPRECATION")
@EagerInitialization
private val registerDefaultEngine = WinHttpNative.registerAsDefault()
