@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import io.ktor.client.HttpClient
import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.winhttp.WINHTTP_ASYNC_RESULT
import platform.winhttp.WINHTTP_DISABLE_KEEP_ALIVE
import platform.winhttp.WINHTTP_OPTION_DISABLE_FEATURE
import platform.winhttp.WINHTTP_OPTION_MAX_RESPONSE_DRAIN_SIZE
import platform.winhttp.WinHttpCloseHandle
import platform.winhttp.WinHttpConnect
import platform.winhttp.WinHttpOpen
import platform.winhttp.WinHttpOpenRequest
import platform.winhttp.WinHttpReadData
import platform.winhttp.WinHttpReceiveResponse
import platform.winhttp.WinHttpSendRequest
import platform.winhttp.WinHttpSetOption
import platform.winhttp.WinHttpSetStatusCallback

private const val FLAG_ASYNC = 0x10000000u
private const val STATUS_SENDREQUEST_COMPLETE = 0x00400000u
private const val STATUS_HEADERS_AVAILABLE = 0x00020000u
private const val STATUS_READ_COMPLETE = 0x00080000u
private const val STATUS_REQUEST_ERROR = 0x00200000u
private const val STATUS_HANDLE_CLOSING = 0x00000800u
private const val ALL_NOTIFICATIONS = 0xFFFFFFFFu

private val asyncRead = AtomicLong(0)
private val asyncState = AtomicInt(0)
private val asyncError = AtomicInt(0)
private val asyncClosing = AtomicInt(0)
private val asyncReads = AtomicInt(0)
private var asyncBuffer: kotlinx.cinterop.CPointer<ByteVar>? = null
private const val ASYNC_BUFFER = 64 * 1024

private fun asyncCallback(handle: COpaquePointer?, context: ULong, status: UInt, info: COpaquePointer?, length: UInt) {
    when (status) {
        STATUS_SENDREQUEST_COMPLETE -> {
            asyncState.value = 1
            WinHttpReceiveResponse(handle, null)
        }

        STATUS_HEADERS_AVAILABLE -> {
            asyncState.value = 2
            WinHttpReadData(handle, asyncBuffer, ASYNC_BUFFER.toUInt(), null)
        }

        STATUS_READ_COMPLETE -> {
            asyncReads.incrementAndGet()
            if (length > 0u) {
                asyncRead.addAndGet(length.toLong())
                if (asyncState.value == 2) WinHttpReadData(handle, asyncBuffer, ASYNC_BUFFER.toUInt(), null)
            } else {
                asyncState.value = 4
            }
        }

        STATUS_REQUEST_ERROR -> asyncError.value = info?.reinterpret<WINHTTP_ASYNC_RESULT>()?.pointed?.dwError?.toInt() ?: -1
        STATUS_HANDLE_CLOSING -> asyncClosing.incrementAndGet()
    }
}

fun rawHttpTests(work: String, options: List<String>) = runBlocking {
    val port = options.firstOrNull { it.startsWith("server=") }?.substringAfter('=')?.toIntOrNull() ?: 18200
    val http = HttpClient(WinHttpNative)
    suspend fun live(key: String): String {
        val stats = http.get("http://127.0.0.1:$port/stats?of=$key").bodyAsText()
        val active = Regex("\"active\": ([0-9]+)").find(stats)?.groupValues?.get(1)
        val sent = Regex("\"liveBytes\": ([0-9]+)").find(stats)?.groupValues?.get(1)
        return "active=$active sent=$sent"
    }
    for (variant in listOf("default", "drain0", "noKeepAlive")) {
        val key = "raw-$variant"
        val session = WinHttpOpen("fj-raw", 1u, null, null, 0u)
        val connect = WinHttpConnect(session, "127.0.0.1", port.toUShort(), 0u)
        val request = WinHttpOpenRequest(connect, "GET", "/file/64MB.bin?throttle=1024&key=$key", null, null, null, 0u)
        memScoped {
            val value = alloc<UIntVar>()
            if (variant == "drain0") {
                value.value = 0u
                WinHttpSetOption(request, WINHTTP_OPTION_MAX_RESPONSE_DRAIN_SIZE.toUInt(), value.ptr, sizeOf<UIntVar>().toUInt())
            }
            if (variant == "noKeepAlive") {
                value.value = WINHTTP_DISABLE_KEEP_ALIVE.toUInt()
                WinHttpSetOption(request, WINHTTP_OPTION_DISABLE_FEATURE.toUInt(), value.ptr, sizeOf<UIntVar>().toUInt())
            }
        }
        val sent = WinHttpSendRequest(request, null, 0u, null, 0u, 0u, 0u)
        val received = WinHttpReceiveResponse(request, null)
        var got = 0
        val buffer = ByteArray(64 * 1024)
        memScoped {
            val count = alloc<UIntVar>()
            while (got < 1024 * 1024) {
                val ok = buffer.usePinned { WinHttpReadData(request, it.addressOf(0), buffer.size.toUInt(), count.ptr) }
                if (ok == 0 || count.value == 0u) break
                got += count.value.toInt()
            }
        }
        WinHttpCloseHandle(request)
        delay(1500)
        val afterRequest = live(key)
        WinHttpCloseHandle(connect)
        WinHttpCloseHandle(session)
        T.check("sync WinHTTP ($variant): closing the request handle closes the connection", afterRequest.startsWith("active=0"), "send=$sent receive=$received read=$got; 1.5 s later $afterRequest")
    }

    for (variant in listOf("closeWhileReading", "removeCallbackThenClose", "closeAfterStop")) {
        val key = "async-$variant"
        asyncRead.value = 0
        asyncState.value = 0
        asyncError.value = 0
        asyncClosing.value = 0
        asyncReads.value = 0
        asyncBuffer = nativeHeap.allocArray(ASYNC_BUFFER)
        val session = WinHttpOpen("fj-raw-async", 1u, null, null, FLAG_ASYNC)
        WinHttpSetStatusCallback(session, staticCFunction(::asyncCallback), ALL_NOTIFICATIONS, 0u)
        val connect = WinHttpConnect(session, "127.0.0.1", port.toUShort(), 0u)
        val request = WinHttpOpenRequest(connect, "GET", "/file/64MB.bin?throttle=1024&key=$key", null, null, null, 0u)
        val sent = WinHttpSendRequest(request, null, 0u, null, 0u, 0u, 1uL)
        delay(1500)
        val before = "read=${asyncRead.value} reads=${asyncReads.value} state=${asyncState.value} ${live(key)}"
        when (variant) {
            "removeCallbackThenClose" -> {
                WinHttpSetStatusCallback(request, null, ALL_NOTIFICATIONS, 0u)
                WinHttpCloseHandle(request)
            }

            "closeAfterStop" -> {
                asyncState.value = 3
                delay(300)
                WinHttpCloseHandle(request)
            }

            else -> WinHttpCloseHandle(request)
        }
        delay(1500)
        val after = live(key)
        WinHttpCloseHandle(connect)
        WinHttpCloseHandle(session)
        delay(300)
        T.check(
            "async WinHTTP ($variant): closing the request handle closes the connection", after.startsWith("active=0"),
            "send=$sent before: $before; 1.5 s after close: $after error=${asyncError.value} closingCallbacks=${asyncClosing.value}"
        )
    }
    http.close()
}
