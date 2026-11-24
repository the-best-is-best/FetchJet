package com.michelle.raouf.fetch_jet.feature.media_player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PlayerRequests {
    private val _queue = MutableStateFlow<PlaybackQueue?>(null)
    val queue: StateFlow<PlaybackQueue?> = _queue

    fun open(queue: PlaybackQueue) {
        if (queue.items.isNotEmpty()) _queue.value = queue
    }

    fun close() {
        _queue.value = null
    }
}
