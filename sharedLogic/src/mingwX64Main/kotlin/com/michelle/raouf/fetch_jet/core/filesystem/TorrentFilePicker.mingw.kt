package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.win.HostHooks
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual object TorrentFilePicker {
    actual val isSupported: Boolean get() = HostHooks.pickFile != null

    @OptIn(DelicateCoroutinesApi::class)
    actual fun pick(onPicked: (String?) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            val path = try {
                HostHooks.pickFile?.invoke("Open .torrent file", "Torrent files", "*.torrent")
            } catch (_: Throwable) {
                null
            }
            withContext(Dispatchers.Main) { onPicked(path) }
        }
    }
}
