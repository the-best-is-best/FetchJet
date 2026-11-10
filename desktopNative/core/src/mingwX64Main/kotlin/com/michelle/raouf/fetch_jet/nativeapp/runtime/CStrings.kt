@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.free
import platform.posix.malloc
import platform.posix.memcpy

fun ByteArray.toMallocCString(): CPointer<ByteVar>? {
    val length = size
    val out = malloc((length + 1).convert())?.reinterpret<ByteVar>() ?: return null
    if (length > 0) {
        usePinned { pinned -> memcpy(out, pinned.addressOf(0), length.convert()) }
    }
    out[length] = 0
    return out
}

fun String.toMallocCString(): CPointer<ByteVar>? = encodeToByteArray().toMallocCString()

fun CPointer<ByteVar>?.toKStringOrEmpty(): String = this?.toKString() ?: ""

fun CPointer<ByteVar>?.toKStringOfLength(byteCount: Int): String =
    if (this == null || byteCount <= 0) "" else readBytes(byteCount).decodeToString()

fun freeMallocCString(pointer: COpaquePointer?) {
    if (pointer != null) free(pointer)
}
