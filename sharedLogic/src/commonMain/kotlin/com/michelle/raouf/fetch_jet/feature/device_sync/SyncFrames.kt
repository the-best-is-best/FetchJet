package com.michelle.raouf.fetch_jet.feature.device_sync

internal const val T_HELLO: Byte = 1
internal const val T_HELLO_ACK: Byte = 2
internal const val T_SEND_META: Byte = 3
internal const val T_FILE_CHUNK: Byte = 4
internal const val T_FILE_END: Byte = 5
internal const val T_ACK: Byte = 6
internal const val T_ERR: Byte = 7
internal const val T_READY: Byte = 8
internal const val T_UNPAIR: Byte = 9

internal class SyncFrame(val type: Byte, val body: ByteArray) {
    operator fun component1(): Byte = type
    operator fun component2(): ByteArray = body
}

internal fun frameBytes(type: Byte, body: ByteArray): ByteArray {
    val out = ByteArray(5 + body.size)
    out[0] = type
    intBytes(body.size).copyInto(out, 1)
    body.copyInto(out, 5)
    return out
}

internal suspend fun SyncConnection.readFrame(timeoutMs: Long): SyncFrame? {
    val head = read(5, timeoutMs) ?: return null
    val length = intAt(head, 1)
    if (length < 0 || length > SYNC_MAX_FRAME) return null
    val body = if (length == 0) ByteArray(0) else read(length, timeoutMs) ?: return null
    return SyncFrame(head[0], body)
}

internal suspend fun SyncConnection.writeFrame(type: Byte, body: ByteArray = ByteArray(0)) {
    write(frameBytes(type, body))
}

internal suspend fun SyncConnection.writeError(reason: String) {
    writeFrame(T_ERR, reason.encodeToByteArray())
}

internal fun idAndSealed(id: String, sealed: ByteArray): ByteArray {
    val idBytes = id.encodeToByteArray()
    return intBytes(idBytes.size) + idBytes + sealed
}

internal fun splitIdAndSealed(body: ByteArray): Pair<String, ByteArray>? {
    if (body.size < 4) return null
    val idLength = intAt(body, 0)
    if (idLength < 0 || idLength > body.size - 4) return null
    return body.decodeToString(4, 4 + idLength) to body.copyOfRange(4 + idLength, body.size)
}

internal fun intBytes(value: Int): ByteArray = byteArrayOf(
    ((value ushr 24) and 0xff).toByte(),
    ((value ushr 16) and 0xff).toByte(),
    ((value ushr 8) and 0xff).toByte(),
    (value and 0xff).toByte()
)

internal fun intAt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 24) or ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or (bytes[offset + 3].toInt() and 0xff)
