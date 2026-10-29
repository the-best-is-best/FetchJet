package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import kotlinx.coroutines.launch

/** Settings (desktop): start FetchJet when the user logs in, optionally hidden in the tray. */
@Composable
fun StartupSettingsCard() {
    if (!StartupLauncher.isSupported) return
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var startHidden by remember { mutableStateOf(StartupPreferences.startHidden) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        enabled = StartupLauncher.isEnabled()
        busy = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "🚀 Startup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            StartupSwitchRow(
                title = "Start FetchJet when you log in",
                subtitle = "So scheduled and queued downloads continue and the browser extension always finds the app.",
                checked = enabled,
                enabled = !busy,
                onChange = { want ->
                    busy = true
                    error = null
                    scope.launch {
                        StartupLauncher.setEnabled(want)
                            .onFailure { error = "Could not change the setting: ${it.message}" }
                        enabled = StartupLauncher.isEnabled()
                        busy = false
                    }
                }
            )

            StartupSwitchRow(
                title = "Start hidden in the tray",
                subtitle = "When started at login, stay in the system tray instead of opening the window.",
                checked = startHidden,
                enabled = enabled && !busy,
                onChange = {
                    startHidden = it
                    StartupPreferences.startHidden = it
                }
            )

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StartupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
