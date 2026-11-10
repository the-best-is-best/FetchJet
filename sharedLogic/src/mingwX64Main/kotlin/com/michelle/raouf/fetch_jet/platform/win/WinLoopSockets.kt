@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import com.michelle.raouf.fetch_jet.platform.proxy.LoopAddress
import com.michelle.raouf.fetch_jet.platform.proxy.LoopSockets
import com.michelle.raouf.fetch_jet.platform.proxy.ProxyLock
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.WSAData
import platform.posix.WSASocketW
import platform.posix.memset
import platform.posix.sockaddr
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.CreateThread
import platform.windows.FreeAddrInfoW
import platform.windows.GetAddrInfoW
import platform.windows.HANDLE
import platform.windows.SetHandleInformation
import platform.windows.Sleep
import platform.windows.WSAGetLastError
import platform.windows.WSAStartup
import platform.windows.accept
import platform.windows.addrinfoW
import platform.windows.bind
import platform.windows.closesocket
import platform.windows.connect
import platform.windows.getsockname
import platform.windows.inet_pton
import platform.windows.listen
import platform.windows.recv
import platform.windows.send
import platform.windows.setsockopt
import platform.windows.shutdown

private const val NO_SOCKET = ULong.MAX_VALUE
private const val FAMILY_V4 = 2
private const val FAMILY_V6 = 23
private const val TYPE_STREAM = 1
private const val PROTOCOL_TCP = 6
private const val LEVEL_SOCKET = 0xFFFF
private const val OPTION_RECEIVE_TIMEOUT = 0x1006
private const val OPTION_SEND_TIMEOUT = 0x1005
private const val OPTION_NO_DELAY = 1
private const val SHUT_BOTH_WAYS = 2
private const val SOCKET_OVERLAPPED = 0x01u
private const val SOCKET_NOT_INHERITED = 0x80u
private const val HANDLE_INHERIT = 1u
private const val LISTEN_BACKLOG = 0x7fffffff
private const val SOCKET_TIMED_OUT = 10060
private const val THREAD_STACK_BYTES = 524_288uL
private const val THREAD_STACK_IS_RESERVATION = 0x00010000u

private class LoopThreadStart(val run: () -> Unit)

private fun loopThreadMain(parameter: COpaquePointer?): UInt {
    try {
        val reference = parameter?.asStableRef<LoopThreadStart>() ?: return 0u
        val start = reference.get()
        reference.dispose()
        start.run()
    } catch (_: Throwable) {
    }
    return 0u
}

private fun Long.socket(): ULong = toULong()

private fun ULong.asLong(): Long = toLong()

private fun socketHandle(socket: Long): HANDLE? = socket.toCPointer<CPointed>()

private fun setSocketInt(socket: Long, level: Int, option: Int, value: Int) {
    memScoped {
        val holder = alloc<IntVar>()
        holder.value = value
        setsockopt(socket.socket(), level, option, holder.ptr.reinterpret<ByteVar>(), 4)
    }
}

private inline fun <T> withLoopbackAddress(port: Int, block: (CPointer<sockaddr>) -> T): T = memScoped {
    val bytes = allocArray<ByteVar>(16)
    for (index in 0 until 16) bytes[index] = 0
    bytes[0] = FAMILY_V4.toByte()
    bytes[2] = (port shr 8).toByte()
    bytes[3] = port.toByte()
    bytes[4] = 127
    bytes[7] = 1
    block(bytes.reinterpret())
}

private object WinLoopSockets : LoopSockets {
    private val ready = AtomicInt(0)

    override val noSocket: Long = NO_SOCKET.asLong()

    override fun prepare() {
        if (ready.value != 0) return
        memScoped {
            val data = alloc<WSAData>()
            WSAStartup(0x0202u, data.ptr)
        }
        ready.value = 1
    }

    override fun newTcp(v6: Boolean): Long =
        WSASocketW(if (v6) FAMILY_V6 else FAMILY_V4, TYPE_STREAM, PROTOCOL_TCP, null, 0u, SOCKET_OVERLAPPED or SOCKET_NOT_INHERITED).asLong()

    override fun listenOnLoopback(socket: Long): Int = withLoopbackAddress(0) { address ->
        memScoped {
            val size = alloc<IntVar>()
            size.value = 16
            if (bind(socket.socket(), address, 16) != 0 || listen(socket.socket(), LISTEN_BACKLOG) != 0 ||
                getsockname(socket.socket(), address, size.ptr) != 0
            ) {
                -1
            } else {
                val bytes = address.reinterpret<ByteVar>()
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            }
        }
    }

    override fun accept(listener: Long): Long {
        val client = accept(listener.socket(), null, null)
        if (client != NO_SOCKET) SetHandleInformation(socketHandle(client.asLong()), HANDLE_INHERIT, 0u)
        return client.asLong()
    }

    override fun connectLoopback(socket: Long, port: Int): Boolean =
        withLoopbackAddress(port) { connect(socket.socket(), it, 16) == 0 }

    override fun resolve(host: String, port: Int): List<LoopAddress>? = memScoped {
        val hints = alloc<addrinfoW>()
        memset(hints.ptr, 0, sizeOf<addrinfoW>().convert())
        hints.ai_socktype = TYPE_STREAM
        hints.ai_protocol = PROTOCOL_TCP
        val found = alloc<CPointerVar<addrinfoW>>()
        found.value = null
        if (GetAddrInfoW(host.wcstr.ptr, port.toString().wcstr.ptr, hints.ptr, found.ptr) != 0) return null
        try {
            val list = ArrayList<LoopAddress>()
            var entry = found.value
            while (entry != null) {
                val info = entry.pointed
                val address = info.ai_addr
                if ((info.ai_family == FAMILY_V4 || info.ai_family == FAMILY_V6) && address != null) {
                    list.add(LoopAddress(info.ai_family == FAMILY_V6, address.reinterpret<ByteVar>().readBytes(info.ai_addrlen.toInt())))
                }
                entry = info.ai_next
            }
            list
        } finally {
            FreeAddrInfoW(found.value)
        }
    }

    override fun connect(socket: Long, address: LoopAddress): Boolean =
        address.bytes.usePinned { connect(socket.socket(), it.addressOf(0).reinterpret(), address.bytes.size) == 0 }

    override fun send(socket: Long, data: CPointer<ByteVar>, count: Int): Int = send(socket.socket(), data, count, 0)

    override fun receive(socket: Long, data: CPointer<ByteVar>, count: Int): Int = recv(socket.socket(), data, count, 0)

    override fun setNoDelay(socket: Long) = setSocketInt(socket, PROTOCOL_TCP, OPTION_NO_DELAY, 1)

    override fun setTimeouts(socket: Long, milliseconds: Int) {
        setSocketInt(socket, LEVEL_SOCKET, OPTION_RECEIVE_TIMEOUT, milliseconds)
        setSocketInt(socket, LEVEL_SOCKET, OPTION_SEND_TIMEOUT, milliseconds)
    }

    override fun interrupt(socket: Long) {
        shutdown(socket.socket(), SHUT_BOTH_WAYS)
        CancelIoEx(socketHandle(socket), null)
    }

    override fun close(socket: Long) {
        closesocket(socket.socket())
    }

    override fun lastErrorTimedOut(): Boolean = WSAGetLastError() == SOCKET_TIMED_OUT

    override fun lastErrorText(): String {
        val code = WSAGetLastError().toUInt()
        return winErrorText(code) + " ($code)"
    }

    override fun numericAddress(host: String): ByteArray? = memScoped {
        val buffer = allocArray<ByteVar>(16)
        if (inet_pton(FAMILY_V4, host, buffer) == 1) return ByteArray(4) { buffer[it] }
        if (inet_pton(FAMILY_V6, host, buffer) == 1) return ByteArray(16) { buffer[it] }
        null
    }

    override fun startThread(run: () -> Unit): Boolean {
        val reference = StableRef.create(LoopThreadStart(run))
        val thread = CreateThread(
            null, THREAD_STACK_BYTES, staticCFunction(::loopThreadMain), reference.asCPointer(), THREAD_STACK_IS_RESERVATION, null
        )
        if (thread == null) {
            reference.dispose()
            return false
        }
        CloseHandle(thread)
        return true
    }

    override fun sleepMs(milliseconds: Int) = Sleep(milliseconds.toUInt())

    override fun newLock(): ProxyLock = object : ProxyLock {
        private val inner = WinLock()
        override fun lock() = inner.lock()
        override fun unlock() = inner.unlock()
    }

    override fun randomHex(bytes: Int): String = winRandomBytes(bytes).toHexString()
}

internal fun winLoopSockets(): LoopSockets = WinLoopSockets
