package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val ANNOUNCE = "fj-ann"
internal const val PROBE = "fj-probe"

internal val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
internal data class Announce(
    val t: String = ANNOUNCE,
    val id: String,
    val name: String,
    val plat: String,
    val port: Int
)

@Serializable
internal data class Hello(val id: String, val name: String, val plat: String, val pub: String)

@Serializable
internal data class FileMeta(val name: String, val size: Long = 0L)

@Serializable
internal data class SendMeta(val payload: SendPayload, val mode: String, val files: List<FileMeta>)

internal fun Hello.toBytes(): ByteArray = syncJson.encodeToString(Hello.serializer(), this).encodeToByteArray()

internal fun SendMeta.toBytes(): ByteArray = syncJson.encodeToString(SendMeta.serializer(), this).encodeToByteArray()

internal fun Announce.toBytes(): ByteArray = syncJson.encodeToString(Announce.serializer(), this).encodeToByteArray()

internal fun helloOf(bytes: ByteArray): Hello? = try {
    syncJson.decodeFromString(Hello.serializer(), bytes.decodeToString())
} catch (_: Throwable) {
    null
}

internal fun sendMetaOf(text: String): SendMeta? = try {
    syncJson.decodeFromString(SendMeta.serializer(), text)
} catch (_: Throwable) {
    null
}

internal fun announceOf(bytes: ByteArray): Announce? = try {
    syncJson.decodeFromString(Announce.serializer(), bytes.decodeToString())
} catch (_: Throwable) {
    null
}
