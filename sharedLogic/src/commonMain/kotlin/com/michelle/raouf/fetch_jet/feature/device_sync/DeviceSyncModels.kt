package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.serialization.Serializable

/**
 * Send a download to another device of yours on the same local network (ROADMAP-1.3.0 item 4).
 * The payload is the *link and its settings*, not the downloaded bytes: the other device adds it
 * to its own queue and downloads it itself. It is encrypted end to end with the shared pairing
 * key before it leaves this device, so nobody else on the LAN can read the link or the cookies.
 */

/** A device this one has paired with. The shared key is kept apart, encrypted, never shown. */
@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val platform: String = "",
    val lastSeenEpochMs: Long = 0L
)

/** A device seen on the local network right now (may or may not be paired yet). */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int,
    val paired: Boolean
)

/** The download to add on the other device: the link plus everything needed to fetch it. */
@Serializable
data class SendPayload(
    val url: String,
    val fileName: String = "",
    val engineType: String = "HTTP_DIRECT",
    val formatId: String = "",
    val originalUrl: String = "",
    val cookie: String = "",
    val referer: String = "",
    val userAgent: String = "",
    /** Set by the sender so the receiver can show "from <device>" and ignore an unpaired sender. */
    val fromDeviceId: String = "",
    val fromDeviceName: String = ""
)
