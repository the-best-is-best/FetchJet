package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.getPlatform

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = DeviceSyncImpl(
    platformName = getPlatform().name,
    scratchDirFor = scratchDirFor,
    tempDir = "$settingsDir/device-sync-tmp",
    onReceive = onReceive
)
