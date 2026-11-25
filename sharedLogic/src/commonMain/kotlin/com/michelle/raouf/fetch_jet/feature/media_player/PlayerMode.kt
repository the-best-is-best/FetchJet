package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings

object PlayerMode {
    const val ASK = "ask"
    const val ON = "on"
    const val OFF = "off"

    fun current(): String = BehaviorSettings.inAppPlayer.value

    fun isOn(): Boolean = current() == ON

    fun asks(): Boolean = current() == ASK

    fun isOff(): Boolean = current() == OFF

    fun set(mode: String) = BehaviorSettings.setInAppPlayer(mode)
}
