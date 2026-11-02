package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun RestartOnPauseDialog(isClip: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("This download can't be resumed", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (isClip) "Clips cut while downloading can't continue where they stopped. " +
                        "If you pause now, the clip will start again from the beginning when you resume."
                else "The server doesn't support resuming. If you pause now, the download will " +
                        "start again from the beginning when you resume.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text("Pause anyway")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Keep downloading")
            }
        }
    )
}

@Composable
internal fun ClipboardOfferDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = if (url.startsWith("magnet:", true)) "Magnet link"
    else url.substringBefore('?').substringAfterLast('/').ifBlank { url }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Download the link you copied?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Turn this off in Settings > General > Watch the clipboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) { Text("Download") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) { Text("Not now") }
        }
    )
}

@Composable
internal fun BrowserFallbackDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Download in browser instead?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "You cancelled this file in FetchJet. The browser stopped its own download when it " +
                            "handed the link over, so it will only continue if you send it back.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    url.substringAfterLast('/').substringBefore('?').ifBlank { url },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text("Download in browser")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("No, discard")
            }
        }
    )
}
