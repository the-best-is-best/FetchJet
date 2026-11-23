package com.michelle.raouf.fetch_jet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberBackupFileSaver(text: () -> String?, onFinished: (failed: Boolean) -> Unit): (fileName: String) -> Unit {
    val context = LocalContext.current
    val currentText by rememberUpdatedState(text)
    val currentFinished by rememberUpdatedState(onFinished)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val body = currentText()
        val failed = if (uri == null) false else if (body.isNullOrEmpty()) true else try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(body.encodeToByteArray()) } == null
        } catch (_: Throwable) {
            true
        }
        currentFinished(failed)
    }
    return remember(launcher) {
        { fileName ->
            try {
                launcher.launch(fileName)
            } catch (_: Throwable) {
                currentFinished(true)
            }
        }
    }
}
