package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winHostName

private fun deviceSyncPlatformName(): String {
    val host = try {
        winHostName().substringBefore('.').trim()
    } catch (_: Throwable) {
        ""
    }
    return if (host.isBlank() || host.equals("localhost", ignoreCase = true)) "Windows" else "$host · Windows"
}

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = winDeviceSync(
    platformName = deviceSyncPlatformName(),
    tempDir = "$settingsDir/device-sync-tmp",
    store = SharedPairedDeviceStore(WinSyncLock()),
    onReceive = onReceive,
    discoveryPort = winGetEnv("FETCHJET_SYNC_UDP_PORT")?.toIntOrNull() ?: DEVICE_SYNC_DISCOVERY_PORT,
    tcpPort = winGetEnv("FETCHJET_SYNC_TCP_PORT")?.toIntOrNull() ?: DEVICE_SYNC_TCP_PORT,
    bindHost = winGetEnv("FETCHJET_SYNC_BIND_HOST")?.takeIf { it.isNotBlank() } ?: DEVICE_SYNC_ANY_HOST,
)
