package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.getPlatform

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = DeviceSyncImpl(
    platformName = deviceSyncPlatformName(),
    scratchDirFor = scratchDirFor,
    tempDir = "$settingsDir/device-sync-tmp",
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
    val host = runCatching {
        java.net.InetAddress.getLocalHost().hostName.substringBefore('.')
    }.getOrNull()?.takeIf { it.isNotBlank() && !it.equals("localhost", ignoreCase = true) }
    return if (host != null && !platform.startsWith("Android")) "$host · $label" else label
}
