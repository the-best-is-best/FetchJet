package com.michelle.raouf.fetch_jet.core.security

import com.michelle.raouf.fetch_jet.feature.device_sync.IosDeviceSyncNativeRegistry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual object CryptoBox {
    actual fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        val native = IosDeviceSyncNativeRegistry.native ?: throw IllegalStateException("CryptoKit bridge not registered")
        return native.aesSeal(key.toNSData(), plain.toNSData())?.toByteArray() ?: throw IllegalStateException("encryption failed")
    }

    actual fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(sealed.size > 28) { "sealed message too short" }
        val native = IosDeviceSyncNativeRegistry.native ?: throw IllegalStateException("CryptoKit bridge not registered")
        return native.aesOpen(key.toNSData(), sealed.toNSData())?.toByteArray() ?: throw IllegalStateException("tag did not verify")
    }

    private fun NSData.toByteArray(): ByteArray {
        val out = ByteArray(length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }
}
