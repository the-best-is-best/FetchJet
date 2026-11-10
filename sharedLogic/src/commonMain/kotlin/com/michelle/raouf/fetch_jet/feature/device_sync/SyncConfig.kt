package com.michelle.raouf.fetch_jet.feature.device_sync

internal const val DEVICE_SYNC_DISCOVERY_PORT = 28461
internal const val DEVICE_SYNC_TCP_PORT = 28462
internal const val DEVICE_SYNC_ANY_HOST = "0.0.0.0"
internal const val DEVICE_SYNC_NO_BROADCAST_ENV = "FETCHJET_SYNC_NO_BROADCAST"

internal const val SYNC_CHUNK = 64 * 1024
internal const val SYNC_MAX_FRAME = 64 * 1024 * 1024
internal const val SYNC_MAX_DATAGRAM = 2048
internal const val SYNC_MAX_INBOUND = 16

internal class DeviceSyncTiming(
    val announceEveryMs: Long = 2000L,
    val deviceExpireMs: Long = 8000L,
    val acceptWaitMs: Long = 90_000L,
    val offerWaitMs: Long = 60_000L,
    val pairRequestGapMs: Long = 30_000L,
    val firstFrameMs: Long = 20_000L,
    val receiveStartMs: Long = 30_000L,
    val connectMs: Long = 5000L,
    val unpairConnectMs: Long = 3000L,
    val replyMs: Long = 30_000L,
    val idleMs: Long = 120_000L,
    val waitRetryMs: Long = 1500L,
    val endedMs: Long = 120_000L,
    val rebindMs: Long = 1000L,
    val sweepEveryMs: Long = 0L,
)

internal class DeviceSyncConfig(
    val platformName: String,
    val tempDir: String,
    val discoveryPort: Int = DEVICE_SYNC_DISCOVERY_PORT,
    val tcpPort: Int = DEVICE_SYNC_TCP_PORT,
    val bindHost: String? = null,
    val sendBroadcast: Boolean = true,
    val alwaysDiscover: Boolean = true,
    val logTimestamps: Boolean = false,
    val timing: DeviceSyncTiming = DeviceSyncTiming(),
)
