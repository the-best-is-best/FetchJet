package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.getPlatform

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = DeviceSyncEngine(
    config = DeviceSyncConfig(
        platformName = deviceSyncPlatformName(),
        tempDir = "$settingsDir/device-sync-tmp",
        sendBroadcast = System.getenv(DEVICE_SYNC_NO_BROADCAST_ENV) != "1",
    ),
    platform = jvmDeviceSyncPlatform(),
    store = SharedPairedDeviceStore(JvmSyncLock()),
    onReceive = onReceive
)

private fun deviceSyncPlatformName(): String {
    val platform = getPlatform().name
    val os = (System.getProperty("os.name") ?: "").lowercase()
    val label = when {
        platform.startsWith("Android") -> platform
        os.contains("mac") || os.contains("darwin") -> "macOS"
        os.contains("win") -> "Windows"
        else -> "Linux"
    }
    val host = runCatching { deviceSyncHostName() }.getOrNull()?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("localhost", ignoreCase = true) }
    return if (host != null && !platform.startsWith("Android")) "$host · $label" else label
}

internal expect fun deviceSyncHostName(): String?
