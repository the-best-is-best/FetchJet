package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS placeholder. The real LAN discovery (Bonjour / NWBrowser), listener and CryptoKit-backed
 * [com.michelle.raouf.fetch_jet.core.security.CryptoBox] are added in the iOS phase of item 4.
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
    override fun startPairing(deviceId: String) {}
    override fun startPairingWithHost(host: String) {}
    override fun confirmPairing() {}
    override fun cancelPairing() {}
    override fun unpair(deviceId: String) {}
    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) = onResult(false)
}

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = NoopDeviceSync
