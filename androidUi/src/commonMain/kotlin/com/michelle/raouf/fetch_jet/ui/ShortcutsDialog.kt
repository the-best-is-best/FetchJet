package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

internal object ListShortcuts {
    val topBarSearch = FocusRequester()
}

@Composable
internal fun ShortcutsDialog(onDismiss: () -> Unit) {
    val canOpenFolder = remember { PathResolver().isOpenFolderSupported() }
    val s = LocalizationManager.Strings
    val rows = buildList {
        add("↑ / ↓" to s.shortcutMove)
        add("Shift + ↑ / ↓" to s.shortcutExtend)
        add("Home / End" to s.shortcutFirstLast)
        add("Enter" to s.shortcutOpen)
        add("Alt + Enter" to s.shortcutDetails)
        add("Ctrl + R" to s.shortcutResume)
        add("Ctrl + P" to s.shortcutPause)
        add("Ctrl + O" to s.shortcutOpenFile)
        if (canOpenFolder) add("Ctrl + E" to s.shortcutOpenFolder)
        add("Ctrl + N" to s.shortcutAdd)
        add("Ctrl + F" to s.shortcutSearch)
        add("Ctrl + A" to s.shortcutSelectAll)
        add("Delete" to s.shortcutDelete)
        add("Esc" to s.shortcutCancelSelection)
        add("F1" to s.shortcutHelp)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                s.shortcutsTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { (keys, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.width(118.dp)) {
                            Text(
                                keys,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(s.ok)
            }
        }
    )
}
