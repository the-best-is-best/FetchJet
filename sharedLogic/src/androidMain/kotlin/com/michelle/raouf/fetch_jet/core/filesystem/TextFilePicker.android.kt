package com.michelle.raouf.fetch_jet.core.filesystem

import android.net.Uri

actual object TextFilePicker {
    @Volatile
    var launcher: ((onResult: (Uri?) -> Unit) -> Unit)? = null

    actual val isSupported: Boolean
        get() = launcher != null

    actual fun pick(onPicked: (String?) -> Unit) {
        val launch = launcher
        if (launch == null) {
            onPicked(null)
            return
        }
        launch { uri ->
            val ctx = PathResolver.appContext
            onPicked(
                if (uri == null || ctx == null) null
                else try {
                    ctx.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                } catch (_: Throwable) {
                    null
                }
            )
        }
    }
}
