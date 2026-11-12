package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun RestartOnPauseDialog(isClip: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                LocalizationManager.Strings.homeCantResumeTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (isClip) LocalizationManager.Strings.homeCantResumeClipText else LocalizationManager.Strings.homeCantResumeServerText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.homePauseAnyway)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.homeKeepDownloading)
            }
        }
    )
}

@Composable
internal fun ClipboardOfferDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = if (url.startsWith("magnet:", true)) LocalizationManager.Strings.homeMagnetLink
    else url.substringBefore('?').substringAfterLast('/').ifBlank { url }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                LocalizationManager.Strings.homeClipboardOfferTitle,
                fontWeight = FontWeight.Bold
            )
        },
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
                    LocalizationManager.Strings.homeClipboardOfferHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text(
                    LocalizationManager.Strings.homeDownload
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) { Text(LocalizationManager.Strings.homeNotNow) }
        }
    )
}

@Composable
internal fun BrowserFallbackDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                LocalizationManager.Strings.homeBrowserFallbackTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    LocalizationManager.Strings.homeBrowserFallbackText,
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
                Text(LocalizationManager.Strings.homeDownloadInBrowser)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.homeNoDiscard)
            }
        }
    )
}

/** A browser extension asks to use FetchJet's local bridge ([BridgePairing]). */
@Composable
internal fun BridgePairingDialog(
    request: com.michelle.raouf.fetch_jet.core.network.BridgePairing.Request,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text(LocalizationManager.Strings.bridgePairTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (request.legacy) LocalizationManager.Strings.bridgePairOldText
                    else LocalizationManager.Strings.bridgePairText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    request.origin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        // Block is the prominent button: a reflexive tap lands on the safe choice. Allow is
        // secondary, so letting an extension in is a deliberate act.
        confirmButton = {
            Button(onClick = onBlock, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.bridgePairBlock)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onAllow, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.bridgePairAllow)
            }
        }
    )
}
