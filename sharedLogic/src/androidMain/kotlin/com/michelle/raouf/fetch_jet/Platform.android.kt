package com.michelle.raouf.fetch_jet

import android.os.Build
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual suspend fun getClipboardText(): String? {
    val context = PathResolver.appContext ?: return null
    val clipboard =
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    val clip = clipboard?.primaryClip
    if (clip != null && clip.itemCount > 0) {
        return clip.getItemAt(0)?.text?.toString()
    }
    return null
}