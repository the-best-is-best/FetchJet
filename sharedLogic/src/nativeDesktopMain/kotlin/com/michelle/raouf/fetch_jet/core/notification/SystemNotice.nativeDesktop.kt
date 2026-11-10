package com.michelle.raouf.fetch_jet.core.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual object SystemNotice {
    private val _notices = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val notices: SharedFlow<Pair<String, String>> = _notices.asSharedFlow()

    actual fun show(id: String, title: String, text: String) {
        _notices.tryEmit(title to text)
    }
}
