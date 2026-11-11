package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = macDeviceSync(
    platformName = macDeviceName(),
    tempDir = "$settingsDir/device-sync-tmp",
    store = SharedPairedDeviceStore(MacSyncLock()),
    onReceive = onReceive,
    discoveryPort = DesktopOs.getEnv("FETCHJET_SYNC_UDP_PORT")?.toIntOrNull() ?: DEVICE_SYNC_DISCOVERY_PORT,
    tcpPort = DesktopOs.getEnv("FETCHJET_SYNC_TCP_PORT")?.toIntOrNull() ?: DEVICE_SYNC_TCP_PORT,
    bindHost = DesktopOs.getEnv("FETCHJET_SYNC_BIND_HOST")?.takeIf { it.isNotBlank() } ?: DEVICE_SYNC_ANY_HOST,
)
