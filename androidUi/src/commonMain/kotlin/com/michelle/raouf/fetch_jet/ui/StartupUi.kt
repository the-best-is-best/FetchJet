package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import kotlinx.coroutines.launch

/** Settings (desktop): start FetchJet when the user logs in, optionally hidden in the tray. */
@Composable
fun StartupSettingsCard() {
    StartupLauncher.managedBySystemUri?.let { settingsUri ->
        // Microsoft Store build: the switch lives in Windows itself.
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        var startHidden by remember { mutableStateOf(StartupPreferences.startHidden) }
        SettingsGroup(
            title = LocalizationManager.Strings.startupGroup,
            footer = LocalizationManager.Strings.startupStoreFooter
        ) {
            SettingRow(
                title = LocalizationManager.Strings.startupStartAtLogin,
                subtitle = LocalizationManager.Strings.startupManagedByWindows,
                onClick = { uri.openUri(settingsUri) }
            ) {
                androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(settingsUri) }) {
                    Text(
                        LocalizationManager.Strings.startupOpenWindowsSettings,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            SettingDivider()
            SettingSwitch(
                title = LocalizationManager.Strings.startupStartHidden,
                subtitle = LocalizationManager.Strings.startupStartHiddenHint,
                checked = startHidden,
                onChange = { startHidden = it; StartupPreferences.startHidden = it }
            )
        }
        return
    }
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

    SettingsGroup(title = LocalizationManager.Strings.startupGroup) {
        SettingSwitch(
            title = LocalizationManager.Strings.startupStartAtLogin,
            subtitle = LocalizationManager.Strings.startupStartAtLoginHint,
            checked = enabled,
            enabled = !busy,
            onChange = { want ->
                busy = true
                error = null
                scope.launch {
                    StartupLauncher.setEnabled(want)
                        .onFailure {
                            error =
                                LocalizationManager.Strings.startupCouldNotChange(it.message.toString())
                        }
                    enabled = StartupLauncher.isEnabled()
                    busy = false
                }
            }
        )
        SettingDivider()
        SettingSwitch(
            title = LocalizationManager.Strings.startupStartHidden,
            subtitle = LocalizationManager.Strings.startupStartHiddenHint,
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
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

