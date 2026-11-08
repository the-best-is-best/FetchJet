package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIDevice

/**
 * iOS device sync (ROADMAP-1.3.0 item 4). The real transport (UDP discovery + TCP, ECDH +
 * AES-256-GCM) lives in [IosDeviceSync]; the raw sockets and crypto come from the Swift bridge
 * ([IosDeviceSyncNative], `NativeDeviceSyncBridge.swift`), set at app startup. Until that bridge
 * is registered this stays the no-op below, so the feature is simply inert rather than crashing.
 */
private object NoopDeviceSync : DeviceSync {
    override val discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList()).asStateFlow()
    override val paired = MutableStateFlow<List<PairedDevice>>(emptyList()).asStateFlow()
    override val pairing = MutableStateFlow<PairingState?>(null).asStateFlow()
    override val sending = MutableStateFlow<SendState?>(null).asStateFlow()
    override val selfId = ""
    override val selfName = ""
    override fun setSelfName(name: String) {}
    override fun start() {}
    override fun stop() {}
    override fun setVisible(visible: Boolean) {}
    override fun startPairing(deviceId: String) {}
    override fun startPairingWithHost(host: String) {}
    override fun confirmPairing() {}
    override fun cancelPairing() {}
    override fun blockPairing() {}
    override fun unblock(deviceId: String) {}
    override val blocked = MutableStateFlow<List<PairedDevice>>(emptyList()).asStateFlow()
    override fun unpair(deviceId: String) {}
    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) = onResult(false)
    override fun cancelSend() {}
}

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync {
    val native = IosDeviceSyncNativeRegistry.native ?: return NoopDeviceSync
    return IosDeviceSync(
        native = native,
        platformName = deviceSyncPlatformName(),
        tempDir = "$settingsDir/device-sync-tmp",
        onReceive = onReceive
    )
}

private fun deviceSyncPlatformName(): String {
    val name = UIDevice.currentDevice.name
    return if (name.isNotBlank()) name else "iPhone"
}
