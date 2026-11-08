package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.flow.StateFlow

/** How much of a download travels to the other device. */
enum class SendMode {
    /** Only the link + its settings; the other device downloads it from the start. */
    LINK_ONLY,

    /** The link + the bytes already downloaded (`.partN` + manifest); the other device continues. */
    TRANSFER_AND_CONTINUE
}

/** The pairing handshake, surfaced to both devices so each user can compare the code and confirm. */
sealed interface PairingState {
    val peerName: String

    /** Reaching the other device and exchanging keys. */
    data class Connecting(override val peerName: String) : PairingState

    /** Both devices show [code]; the user confirms it matches on both, then the pairing is stored. */
    data class Confirm(override val peerName: String, val peerId: String, val code: String) :
        PairingState

    data class Failed(override val peerName: String, val reason: String) : PairingState
}

/** Progress of one send, surfaced to the sender's UI. */
sealed interface SendState {
    val deviceName: String

    /** Reached the other device; waiting for its user to accept the pairing before streaming. */
    data class Waiting(override val deviceName: String) : SendState
    data class Sending(override val deviceName: String, val fraction: Float) : SendState
    data class Done(override val deviceName: String) : SendState
    data class Failed(override val deviceName: String, val reason: String) : SendState
}

/**
 * Everything the sender hands over for one "Send to device" (ROADMAP-1.3.0 item 4). The partial
 * files are included only for [SendMode.TRANSFER_AND_CONTINUE]; the receiver writes them into its
 * own scratch folder and resumes. [deleteAfterSuccess] removes the task here *only* once the
 * transfer has fully succeeded.
 */
data class SendRequest(
    val deviceId: String,
    val payload: SendPayload,
    val mode: SendMode,
    val deleteAfterSuccess: Boolean,
    /** Absolute paths of the `.partN` / `.chunks` files to stream (TRANSFER_AND_CONTINUE only). */
    val partialFiles: List<String> = emptyList(),
    /**
     * A finished download has no `.partN` pieces left on disk (they were merged away). To still send
     * it under TRANSFER_AND_CONTINUE, this holds the finished file's path; it travels as a single
     * complete piece so the other device lands at 100% instead of fetching the whole thing again.
     */
    val completedFilePath: String = "",
    /** The local task to remove once the send fully succeeds (when [deleteAfterSuccess]). */
    val sourceTaskId: String = ""
)

/**
 * LAN device-to-device sync: discover other FetchJet devices on the network by name, pair with
 * them securely (ECDH + a code both users compare), and send a download — link only, or link plus
 * the bytes already fetched so the other device continues from the same point.
 */
interface DeviceSync {
    val discovered: StateFlow<List<DiscoveredDevice>>
    val paired: StateFlow<List<PairedDevice>>
    val pairing: StateFlow<PairingState?>
    val sending: StateFlow<SendState?>

    val selfId: String
    val selfName: String
    fun setSelfName(name: String)

    /** Begin listening on the LAN (and announcing, when visible). Safe to call again. */
    fun start()
    fun stop()

    /**
     * Visible: announce this device and accept pairing / sends. Hidden: no announce and no open
     * port, so nobody can find or reach it; it can still see other devices and send to them.
     */
    fun setVisible(visible: Boolean)

    fun startPairing(deviceId: String)

    /** Pair by the other device's IP, for networks where UDP discovery is blocked. */
    fun startPairingWithHost(host: String)
    fun confirmPairing()
    fun cancelPairing()
    fun unpair(deviceId: String)

    /** Sends in the background; [onResult] is true only after the receiver confirmed full receipt. */
    fun send(request: SendRequest, onResult: (Boolean) -> Unit)
}

/**
 * Builds the platform device-sync. [onReceive] is called on the receiver when a payload arrives
 * (with the local paths of any partial files already written into the scratch folder, ready to
 * resume). JVM + Android share one implementation; iOS gets it in its own phase.
 */
expect fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync
