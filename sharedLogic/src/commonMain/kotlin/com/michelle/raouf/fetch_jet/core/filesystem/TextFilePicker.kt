package com.michelle.raouf.fetch_jet.core.filesystem

expect object TextFilePicker {
    val isSupported: Boolean

    fun pick(onPicked: (String?) -> Unit)
}
