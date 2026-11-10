package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual object TextFilePicker {
    actual val isSupported: Boolean get() = HostHooks.pickFile != null

    @OptIn(DelicateCoroutinesApi::class)
    actual fun pick(onPicked: (String?) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            val text = try {
                val path = HostHooks.pickFile?.invoke("Import links", "Text files", "*.txt")
                if (path == null) null else DesktopOs.readText(path)
            } catch (_: Throwable) {
                null
            }
            withContext(Dispatchers.Main) { onPicked(text) }
        }
    }
}
