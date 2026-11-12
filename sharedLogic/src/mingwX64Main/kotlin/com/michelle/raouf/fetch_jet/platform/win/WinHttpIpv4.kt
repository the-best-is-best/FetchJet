@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.WSAData
import platform.posix.memset
import platform.windows.FreeAddrInfoW
import platform.windows.GetAddrInfoW
import platform.windows.WSAStartup
import platform.windows.addrinfoW
import platform.winhttp.WinHttpCloseHandle
import platform.winhttp.WinHttpConnect
import platform.winhttp.WinHttpOpen
import platform.winhttp.WinHttpOpenRequest
import platform.winhttp.WinHttpSetOption

private const val OPTION_RESOLUTION_HOSTNAME = 165u
private const val PROBE_ACCESS_NO_PROXY = 1u
private const val ADDRESS_FAMILY_V4 = 2
private const val SOCKET_TYPE_STREAM = 1
private const val V4_ADDRESS_BYTES = 16

object WinHttpIpv4 {
    @kotlin.concurrent.Volatile
    var prefers: (String) -> Boolean = { false }

    val available: Boolean by lazy { probe() }

    private val winsock: Boolean by lazy {
        memScoped {
            val data = alloc<WSAData>()
            WSAStartup(0x0202u, data.ptr) == 0
        }
    }

    fun isName(host: String): Boolean = host.isNotEmpty() && ':' !in host && host.any { it.isLetter() }

    fun addressOf(host: String): String? {
        if (!isName(host) || !winsock) return null
        return memScoped {
            val hints = alloc<addrinfoW>()
            memset(hints.ptr, 0, sizeOf<addrinfoW>().convert())
            hints.ai_family = ADDRESS_FAMILY_V4
            hints.ai_socktype = SOCKET_TYPE_STREAM
            val found = alloc<CPointerVar<addrinfoW>>()
            found.value = null
            if (GetAddrInfoW(host.wcstr.ptr, null, hints.ptr, found.ptr) != 0) return@memScoped null
            try {
                var entry = found.value
                var text: String? = null
                while (entry != null && text == null) {
                    val info = entry.pointed
                    val address = info.ai_addr
                    if (info.ai_family == ADDRESS_FAMILY_V4 && address != null && info.ai_addrlen.toInt() >= V4_ADDRESS_BYTES) {
                        val bytes = address.reinterpret<ByteVar>()
                        text = (4..7).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
                    }
                    entry = info.ai_next
                }
                text
            } finally {
                FreeAddrInfoW(found.value)
            }
        }
    }

    fun resolveAs(request: COpaquePointer, address: String): Boolean = memScoped {
        WinHttpSetOption(request, OPTION_RESOLUTION_HOSTNAME, address.wcstr.ptr, address.length.toUInt()) != 0
    }

    private fun probe(): Boolean {
        val session = WinHttpOpen(null, PROBE_ACCESS_NO_PROXY, null, null, 0u) ?: return false
        try {
            val connect = WinHttpConnect(session, "localhost", 80u, 0u) ?: return false
            try {
                val request = WinHttpOpenRequest(connect, "GET", "/", null, null, null, 0u) ?: return false
                try {
                    return resolveAs(request, "127.0.0.1")
                } finally {
                    WinHttpCloseHandle(request)
                }
            } finally {
                WinHttpCloseHandle(connect)
            }
        } finally {
            WinHttpCloseHandle(session)
        }
    }
}
