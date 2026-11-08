package com.michelle.raouf.fetch_jet.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

@Composable
fun MissingFileDialog(
    fileName: String,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = LocalizationManager.Strings.fileMissingTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = LocalizationManager.Strings.fileMissingMessage(fileName),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(LocalizationManager.Strings.detailsRemoveFromList, color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(LocalizationManager.Strings.close)
            }
        }
    )
}
