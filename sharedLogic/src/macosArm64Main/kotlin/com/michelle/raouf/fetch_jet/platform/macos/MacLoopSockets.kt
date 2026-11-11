@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.macos

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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
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
import platform.posix.AI_NUMERICHOST
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.FD_CLOEXEC
import platform.posix.F_SETFD
import platform.posix.IPPROTO_TCP
import platform.posix.PTHREAD_CREATE_DETACHED
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDTIMEO
import platform.posix.TCP_NODELAY
import platform.posix.accept
import platform.posix.addrinfo
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.pthread_attr_destroy
import platform.posix.pthread_attr_init
import platform.posix.pthread_attr_setdetachstate
import platform.posix.pthread_attr_setstacksize
import platform.posix.pthread_attr_t
import platform.posix.pthread_create
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.pthread_tVar
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

private class MacLoopThreadStart(val run: () -> Unit)

private fun macLoopThreadMain(parameter: COpaquePointer?): COpaquePointer? {
    try {
        val reference = parameter?.asStableRef<MacLoopThreadStart>() ?: return null
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
    bytes[0] = 16
    bytes[1] = AF_INET.toByte()
    bytes[2] = (port shr 8).toByte()
    bytes[3] = port.toByte()
    bytes[4] = 127
    bytes[7] = 1
    block(bytes.reinterpret())
}

private fun setSocketInt(socket: Int, level: Int, option: Int, value: Int) {
    memScoped {
        val holder = alloc<IntVar>()
        holder.value = value
        setsockopt(socket, level, option, holder.ptr, sizeOf<IntVar>().convert())
    }
}

private fun prepareSocket(socket: Int): Int {
    if (socket >= 0) {
        fcntl(socket, F_SETFD, FD_CLOEXEC)
        setSocketInt(socket, SOL_SOCKET, SO_NOSIGPIPE, 1)
    }
    return socket
}

private class MacProxyLock : ProxyLock {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().also { created ->
        memScoped {
            val attributes = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attributes.ptr)
            pthread_mutexattr_settype(attributes.ptr, PTHREAD_MUTEX_RECURSIVE)
            pthread_mutex_init(created.ptr, attributes.ptr)
            pthread_mutexattr_destroy(attributes.ptr)
        }
    }

    override fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    override fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }
}

private object MacLoopSockets : LoopSockets {
    override val noSocket: Long = -1L

    override fun prepare() {}

    override fun newTcp(v6: Boolean): Long =
        prepareSocket(socket(if (v6) AF_INET6 else AF_INET, SOCK_STREAM, IPPROTO_TCP)).toLong()

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

    override fun accept(listener: Long): Long = prepareSocket(accept(listener.toInt(), null, null)).toLong()

    override fun connectLoopback(socket: Long, port: Int): Boolean =
        withLoopbackAddress(port) { connect(socket.toInt(), it, 16u) == 0 }

    override fun resolve(host: String, port: Int): List<LoopAddress>? = lookup(host, port, numericOnly = false)

    private fun lookup(host: String, port: Int, numericOnly: Boolean): List<LoopAddress>? = memScoped {
        val hints = alloc<addrinfo>()
        memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
        hints.ai_socktype = SOCK_STREAM
        hints.ai_protocol = IPPROTO_TCP
        if (numericOnly) hints.ai_flags = AI_NUMERICHOST
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
        send(socket.toInt(), data, count.convert(), 0).toInt()

    override fun receive(socket: Long, data: CPointer<ByteVar>, count: Int): Int =
        recv(socket.toInt(), data, count.convert(), 0).toInt()

    override fun setNoDelay(socket: Long) = setSocketInt(socket.toInt(), IPPROTO_TCP, TCP_NODELAY, 1)

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

    override fun numericAddress(host: String): ByteArray? {
        val address = lookup(host, 0, numericOnly = true)?.firstOrNull() ?: return null
        return if (address.isV6) address.bytes.copyOfRange(8, 24) else address.bytes.copyOfRange(4, 8)
    }

    override fun startThread(run: () -> Unit): Boolean = memScoped {
        val reference = StableRef.create(MacLoopThreadStart(run))
        val attributes = alloc<pthread_attr_t>()
        pthread_attr_init(attributes.ptr)
        pthread_attr_setdetachstate(attributes.ptr, PTHREAD_CREATE_DETACHED)
        pthread_attr_setstacksize(attributes.ptr, THREAD_STACK_BYTES.convert())
        val thread = alloc<pthread_tVar>()
        val code = pthread_create(thread.ptr, attributes.ptr, staticCFunction(::macLoopThreadMain), reference.asCPointer())
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

    override fun newLock(): ProxyLock = MacProxyLock()

    override fun randomHex(bytes: Int): String =
        MacCrypto.randomBytes(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

internal fun macLoopSockets(): LoopSockets = MacLoopSockets
