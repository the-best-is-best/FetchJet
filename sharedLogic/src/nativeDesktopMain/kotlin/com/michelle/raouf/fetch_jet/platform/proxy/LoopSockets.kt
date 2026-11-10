@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.proxy

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

class LoopbackProxyException(message: String) : Exception(message)

interface ProxyLock {
    fun lock()
    fun unlock()
}

inline fun <T> ProxyLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

class LoopAddress(val isV6: Boolean, val bytes: ByteArray)

interface LoopSockets {
    val noSocket: Long
    fun prepare()
    fun newTcp(v6: Boolean): Long
    fun listenOnLoopback(socket: Long): Int
    fun accept(listener: Long): Long
    fun connectLoopback(socket: Long, port: Int): Boolean
    fun resolve(host: String, port: Int): List<LoopAddress>?
    fun connect(socket: Long, address: LoopAddress): Boolean
    fun send(socket: Long, data: CPointer<ByteVar>, count: Int): Int
    fun receive(socket: Long, data: CPointer<ByteVar>, count: Int): Int
    fun setNoDelay(socket: Long)
    fun setTimeouts(socket: Long, milliseconds: Int)
    fun interrupt(socket: Long)
    fun close(socket: Long)
    fun lastErrorTimedOut(): Boolean
    fun lastErrorText(): String
    fun numericAddress(host: String): ByteArray?
    fun startThread(run: () -> Unit): Boolean
    fun sleepMs(milliseconds: Int)
    fun newLock(): ProxyLock
    fun randomHex(bytes: Int): String
}

expect fun loopSockets(): LoopSockets
