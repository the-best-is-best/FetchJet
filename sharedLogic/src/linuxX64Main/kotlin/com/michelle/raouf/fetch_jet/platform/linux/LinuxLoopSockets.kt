@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.proxy.LoopAddress
import com.michelle.raouf.fetch_jet.platform.proxy.LoopSockets
import com.michelle.raouf.fetch_jet.platform.proxy.ProxyLock
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
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
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.IPPROTO_TCP
import platform.posix.MSG_NOSIGNAL
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDTIMEO
import platform.posix.TCP_NODELAY
import platform.posix.FD_CLOEXEC
import platform.posix.F_SETFD
import platform.posix.accept
import platform.posix.fcntl
import platform.posix.addrinfo
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getsockname
import platform.linux.inet_pton
import platform.posix.listen
import platform.posix.memset
import platform.posix.pthread_attr_destroy
import platform.posix.pthread_attr_init
import platform.posix.pthread_attr_setdetachstate
import platform.posix.pthread_attr_setstacksize
import platform.posix.pthread_attr_t
import platform.posix.pthread_create
import platform.posix.pthread_tVar
import platform.posix.PTHREAD_CREATE_DETACHED
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.strerror
import platform.posix.timeval
import platform.posix.usleep

private const val THREAD_STACK_BYTES = 524_288

private class LoopThreadStart(val run: () -> Unit)

private fun loopThreadMain(parameter: COpaquePointer?): COpaquePointer? {
    try {
        val reference = parameter?.asStableRef<LoopThreadStart>() ?: return null
        val start = reference.get()
        reference.dispose()
        start.run()
    } catch (_: Throwable) {
    }
    return null
}

private inline fun <T> withLoopbackAddress(port: Int, block: (CPointer<sockaddr>) -> T): T = memScoped {
    val bytes = allocArray<ByteVar>(16)
    for (index in 0 until 16) bytes[index] = 0
    bytes[0] = AF_INET.toByte()
    bytes[2] = (port shr 8).toByte()
    bytes[3] = port.toByte()
    bytes[4] = 127
    bytes[7] = 1
    block(bytes.reinterpret())
}

private fun setSocketInt(socket: Long, level: Int, option: Int, value: Int) {
    memScoped {
        val holder = alloc<IntVar>()
        holder.value = value
        setsockopt(socket.toInt(), level, option, holder.ptr, sizeOf<IntVar>().convert())
    }
}

private object LinuxLoopSockets : LoopSockets {
    override val noSocket: Long = -1L

    override fun prepare() {}

    override fun newTcp(v6: Boolean): Long =
        socket(if (v6) AF_INET6 else AF_INET, SOCK_STREAM or SOCK_CLOEXEC, IPPROTO_TCP).toLong()

    override fun listenOnLoopback(socket: Long): Int = withLoopbackAddress(0) { address ->
        memScoped {
            val size = alloc<UIntVar>()
            size.value = 16u
            if (bind(socket.toInt(), address, 16u) != 0 || listen(socket.toInt(), 1024) != 0 ||
                getsockname(socket.toInt(), address, size.ptr) != 0
            ) {
                -1
            } else {
                val bytes = address.reinterpret<ByteVar>()
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            }
        }
    }

    override fun accept(listener: Long): Long {
        val client = accept(listener.toInt(), null, null)
        if (client >= 0) fcntl(client, F_SETFD, FD_CLOEXEC)
        return client.toLong()
    }

    override fun connectLoopback(socket: Long, port: Int): Boolean =
        withLoopbackAddress(port) { connect(socket.toInt(), it, 16u) == 0 }

    override fun resolve(host: String, port: Int): List<LoopAddress>? = memScoped {
        val hints = alloc<addrinfo>()
        memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
        hints.ai_socktype = SOCK_STREAM
        hints.ai_protocol = IPPROTO_TCP
        val found = alloc<CPointerVar<addrinfo>>()
        found.value = null
        if (getaddrinfo(host, port.toString(), hints.ptr, found.ptr) != 0) return null
        try {
            val list = ArrayList<LoopAddress>()
            var entry = found.value
            while (entry != null) {
                val info = entry.pointed
                val address = info.ai_addr
                if ((info.ai_family == AF_INET || info.ai_family == AF_INET6) && address != null) {
                    list.add(LoopAddress(info.ai_family == AF_INET6, address.reinterpret<ByteVar>().readBytes(info.ai_addrlen.toInt())))
                }
                entry = info.ai_next
            }
            list
        } finally {
            freeaddrinfo(found.value)
        }
    }

    override fun connect(socket: Long, address: LoopAddress): Boolean =
        address.bytes.usePinned { connect(socket.toInt(), it.addressOf(0).reinterpret(), address.bytes.size.toUInt()) == 0 }

    override fun send(socket: Long, data: CPointer<ByteVar>, count: Int): Int =
        send(socket.toInt(), data, count.convert(), MSG_NOSIGNAL).toInt()

    override fun receive(socket: Long, data: CPointer<ByteVar>, count: Int): Int =
        recv(socket.toInt(), data, count.convert(), 0).toInt()

    override fun setNoDelay(socket: Long) = setSocketInt(socket, IPPROTO_TCP, TCP_NODELAY, 1)

    override fun setTimeouts(socket: Long, milliseconds: Int) {
        memScoped {
            val time = alloc<timeval>()
            time.tv_sec = (milliseconds / 1000).convert()
            time.tv_usec = ((milliseconds % 1000) * 1000).convert()
            setsockopt(socket.toInt(), SOL_SOCKET, SO_RCVTIMEO, time.ptr, sizeOf<timeval>().convert())
            setsockopt(socket.toInt(), SOL_SOCKET, SO_SNDTIMEO, time.ptr, sizeOf<timeval>().convert())
        }
    }

    override fun interrupt(socket: Long) {
        shutdown(socket.toInt(), SHUT_RDWR)
    }

    override fun close(socket: Long) {
        close(socket.toInt())
    }

    override fun lastErrorTimedOut(): Boolean {
        val code = errno
        return code == EAGAIN || code == EWOULDBLOCK
    }

    override fun lastErrorText(): String {
        val code = errno
        return (strerror(code)?.toKString() ?: "error") + " ($code)"
    }

    override fun numericAddress(host: String): ByteArray? = memScoped {
        val buffer = allocArray<ByteVar>(16)
        if (inet_pton(AF_INET, host, buffer) == 1) return ByteArray(4) { buffer[it] }
        if (inet_pton(AF_INET6, host, buffer) == 1) return ByteArray(16) { buffer[it] }
        null
    }

    override fun startThread(run: () -> Unit): Boolean = memScoped {
        val reference = StableRef.create(LoopThreadStart(run))
        val attributes = alloc<pthread_attr_t>()
        pthread_attr_init(attributes.ptr)
        pthread_attr_setdetachstate(attributes.ptr, PTHREAD_CREATE_DETACHED)
        pthread_attr_setstacksize(attributes.ptr, THREAD_STACK_BYTES.convert())
        val thread = alloc<pthread_tVar>()
        val code = pthread_create(thread.ptr, attributes.ptr, staticCFunction(::loopThreadMain), reference.asCPointer())
        pthread_attr_destroy(attributes.ptr)
        if (code != 0) {
            reference.dispose()
            false
        } else {
            true
        }
    }

    override fun sleepMs(milliseconds: Int) {
        usleep((milliseconds * 1000).toUInt())
    }

    override fun newLock(): ProxyLock = object : ProxyLock {
        private val inner = LinuxLock()
        override fun lock() = inner.lock()
        override fun unlock() = inner.unlock()
    }

    override fun randomHex(bytes: Int): String =
        DesktopOs.randomBytes(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

internal fun linuxLoopSockets(): LoopSockets = LinuxLoopSockets
