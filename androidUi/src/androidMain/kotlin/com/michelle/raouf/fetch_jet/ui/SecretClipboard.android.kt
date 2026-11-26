package com.michelle.raouf.fetch_jet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val CLEAR_AFTER_MS = 60_000L
private val handler = Handler(Looper.getMainLooper())
private var pendingClear: Runnable? = null

private fun copySecret(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
    pendingClear?.let { handler.removeCallbacks(it) }
    val clear = Runnable {
        try {
            val held = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (held == text) clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Throwable) {
        }
        pendingClear = null
    }
    pendingClear = clear
    handler.postDelayed(clear, CLEAR_AFTER_MS)
}

@Composable
internal actual fun rememberSecretCopier(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { text -> copySecret(context, text) } }
}
