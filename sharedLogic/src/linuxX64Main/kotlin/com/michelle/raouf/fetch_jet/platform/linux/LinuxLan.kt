@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.linux

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.linux.freeifaddrs
import platform.linux.getifaddrs
import platform.linux.ifaddrs
import platform.posix.AF_INET
import platform.posix.gethostname
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_t
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_destroy
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.sockaddr_in
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.free

private const val IFF_UP = 0x1u
private const val IFF_LOOPBACK = 0x8u

class LinuxIpv4Interface(val name: String, val address: String, val broadcast: String, val prefixBits: Int, val up: Boolean, val loopback: Boolean)

private fun octets(value: UInt): String =
    "${value and 0xFFu}.${(value shr 8) and 0xFFu}.${(value shr 16) and 0xFFu}.${(value shr 24) and 0xFFu}"

fun linuxIpv4Interfaces(): List<LinuxIpv4Interface> = memScoped {
    val head = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
    val out = ArrayList<LinuxIpv4Interface>()
    try {
        var cursor = head.value
        while (cursor != null) {
            val item = cursor.pointed
            val address = item.ifa_addr
            val mask = item.ifa_netmask
            if (address != null && mask != null && address.pointed.sa_family.toInt() == AF_INET) {
                val ip = address.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                val bits = mask.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                val flags = item.ifa_flags
                out.add(
                    LinuxIpv4Interface(
                        name = item.ifa_name?.toKString().orEmpty(),
                        address = octets(ip),
                        broadcast = octets(ip or bits.inv()),
                        prefixBits = bits.countOneBits(),
                        up = flags and IFF_UP != 0u,
                        loopback = flags and IFF_LOOPBACK != 0u,
                    )
                )
            }
            cursor = item.ifa_next
        }
    } finally {
        freeifaddrs(head.value)
    }
    out
}

fun linuxBroadcastAddresses(): List<String> =
    linuxIpv4Interfaces().filter { it.up && !it.loopback && it.prefixBits in 1..31 }.map { it.broadcast }.distinct()

fun linuxHostName(): String = memScoped {
    val buffer = allocArray<ByteVar>(256)
    if (gethostname(buffer, 255.convert()) != 0) "" else buffer.toKString()
}

class LinuxLock {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().also { created ->
        memScoped {
            val attributes = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attributes.ptr)
            pthread_mutexattr_settype(attributes.ptr, PTHREAD_MUTEX_RECURSIVE.convert())
            pthread_mutex_init(created.ptr, attributes.ptr)
            pthread_mutexattr_destroy(attributes.ptr)
        }
    }

    fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }

    fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }

    fun dispose() {
        pthread_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex)
    }
}
