package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard

@Composable
fun ProvideSafeClipboard(content: @Composable () -> Unit) {
    val base = LocalClipboard.current
    val safe = remember(base) {
        object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? =
                try {
                    base.getClipEntry()
                } catch (_: Throwable) {
                    null
                }

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                try {
                    base.setClipEntry(clipEntry)
                } catch (_: Throwable) {
                }
            }

            override val nativeClipboard: NativeClipboard
                get() = base.nativeClipboard
        }
    }
    CompositionLocalProvider(LocalClipboard provides safe, content = content)
}
