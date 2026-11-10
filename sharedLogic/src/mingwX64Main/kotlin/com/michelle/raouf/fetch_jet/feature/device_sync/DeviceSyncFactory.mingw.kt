package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private object InertDeviceSync : DeviceSync {
    override val discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList()).asStateFlow()
    override val paired = MutableStateFlow<List<PairedDevice>>(emptyList()).asStateFlow()
    override val pairing = MutableStateFlow<PairingState?>(null).asStateFlow()
    override val sending = MutableStateFlow<SendState?>(null).asStateFlow()
    override val blocked = MutableStateFlow<List<PairedDevice>>(emptyList()).asStateFlow()
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
    override fun unpair(deviceId: String) {}
    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) = onResult(false)
    override fun cancelSend() {}
}

actual fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync = InertDeviceSync
