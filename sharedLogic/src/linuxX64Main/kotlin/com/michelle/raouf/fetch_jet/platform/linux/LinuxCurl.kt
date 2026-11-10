@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.RTLD_LOCAL
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym

class LinuxCurlException(message: String) : Exception(message)

internal object CurlOpt {
    const val WRITEDATA = 10001
    const val URL = 10002
    const val PROXY = 10004
    const val ERRORBUFFER = 10010
    const val POSTFIELDS = 10015
    const val LOW_SPEED_LIMIT = 19
    const val LOW_SPEED_TIME = 20
    const val HTTPHEADER = 10023
    const val HEADERDATA = 10029
    const val CUSTOMREQUEST = 10036
    const val NOPROGRESS = 43
    const val NOBODY = 44
    const val POST = 47
    const val FOLLOWLOCATION = 52
    const val XFERINFODATA = 10057
    const val SSL_VERIFYPEER = 64
    const val CAINFO = 10065
    const val HTTPGET = 80
    const val SSL_VERIFYHOST = 81
    const val HTTP_VERSION = 84
    const val BUFFERSIZE = 98
    const val NOSIGNAL = 99
    const val POSTFIELDSIZE_LARGE = 30120
    const val CONNECTTIMEOUT_MS = 156
    const val PROXYUSERNAME = 10175
    const val PROXYPASSWORD = 10176
    const val TCP_KEEPALIVE = 213
    const val PATH_AS_IS = 234
    const val SUPPRESS_CONNECT_HEADERS = 265
    const val WRITEFUNCTION = 20011
    const val HEADERFUNCTION = 20079
    const val XFERINFOFUNCTION = 20219
}

internal object CurlCode {
    const val OK = 0
    const val COULDNT_RESOLVE_PROXY = 5
    const val COULDNT_RESOLVE_HOST = 6
    const val COULDNT_CONNECT = 7
    const val WRITE_ERROR = 23
    const val OPERATION_TIMEDOUT = 28
    const val ABORTED_BY_CALLBACK = 42
}

internal typealias CurlWriteFn = CFunction<(CPointer<ByteVar>?, ULong, ULong, COpaquePointer?) -> ULong>
internal typealias CurlProgressFn = CFunction<(COpaquePointer?, Long, Long, Long, Long) -> Int>

private typealias FnGlobalInit = CFunction<(Long) -> Int>
private typealias FnEasyInit = CFunction<() -> COpaquePointer?>
private typealias FnHandle = CFunction<(COpaquePointer?) -> Unit>
private typealias FnPerform = CFunction<(COpaquePointer?) -> Int>
private typealias FnSetLong = CFunction<(COpaquePointer?, Int, Long) -> Int>
private typealias FnSetPointer = CFunction<(COpaquePointer?, Int, COpaquePointer?) -> Int>
private typealias FnStrError = CFunction<(Int) -> CPointer<ByteVar>?>
private typealias FnSlistAppend = CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> COpaquePointer?>

object LinuxCurl {
    private const val GLOBAL_ALL = 3L

    private val library: COpaquePointer? by lazy {
        listOf("libcurl.so.4", "libcurl-gnutls.so.4", "libcurl.so")
            .firstNotNullOfOrNull { dlopen(it, RTLD_NOW or RTLD_LOCAL) }
    }

    private fun symbol(name: String): COpaquePointer =
        dlsym(library ?: throw LinuxCurlException("libcurl is not installed"), name)
            ?: throw LinuxCurlException("libcurl has no $name")

    private val globalInit by lazy { symbol("curl_global_init").reinterpret<FnGlobalInit>() }
    private val easyInit by lazy { symbol("curl_easy_init").reinterpret<FnEasyInit>() }
    private val easyCleanup by lazy { symbol("curl_easy_cleanup").reinterpret<FnHandle>() }
    private val easyPerform by lazy { symbol("curl_easy_perform").reinterpret<FnPerform>() }
    private val setLong by lazy { symbol("curl_easy_setopt").reinterpret<FnSetLong>() }
    private val setPointer by lazy { symbol("curl_easy_setopt").reinterpret<FnSetPointer>() }
    private val strError by lazy { symbol("curl_easy_strerror").reinterpret<FnStrError>() }
    private val slistAppend by lazy { symbol("curl_slist_append").reinterpret<FnSlistAppend>() }
    private val slistFree by lazy { symbol("curl_slist_free_all").reinterpret<FnHandle>() }

    private val initialized: Boolean by lazy { library != null && globalInit(GLOBAL_ALL) == CurlCode.OK }

    val isAvailable: Boolean get() = initialized

    fun newHandle(): COpaquePointer {
        if (!initialized) throw LinuxCurlException("libcurl is not installed")
        return easyInit() ?: throw LinuxCurlException("curl_easy_init failed")
    }

    fun free(handle: COpaquePointer) = easyCleanup(handle)

    fun perform(handle: COpaquePointer): Int = easyPerform(handle)

    fun set(handle: COpaquePointer, option: Int, value: Long): Int = setLong(handle, option, value)

    fun set(handle: COpaquePointer, option: Int, value: COpaquePointer?): Int = setPointer(handle, option, value)

    fun set(handle: COpaquePointer, option: Int, value: String): Int = memScoped {
        setPointer(handle, option, value.cstr.ptr.reinterpret())
    }

    fun headerList(lines: List<String>): COpaquePointer? {
        var list: COpaquePointer? = null
        for (line in lines) {
            val next = memScoped { slistAppend(list, line.cstr.ptr) } ?: break
            list = next
        }
        return list
    }

    fun freeHeaderList(list: COpaquePointer?) {
        if (list != null) slistFree(list)
    }

    fun errorText(code: Int): String = strError(code)?.toKString() ?: "curl error $code"
}
