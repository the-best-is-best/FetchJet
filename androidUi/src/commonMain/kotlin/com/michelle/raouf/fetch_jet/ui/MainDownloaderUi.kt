package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.setup.DefaultTorrentHandler
import com.michelle.raouf.fetch_jet.core.setup.MainDownloaderSetup
import com.michelle.raouf.fetch_jet.core.setup.MainDownloaderState
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Asks the settings panel to open one of its pages (from outside the panel). */
internal object SettingsJump {
    val section = MutableStateFlow<SettingsSection?>(null)
}

/** Opens Settings > Browser extension (the existing install flow). */
fun openBrowserExtensionSettings() {
    SettingsJump.section.value = SettingsSection.BROWSER
}

/**
 * First run: "Make FetchJet your main downloader". One tap does every step the platform allows
 * ([MainDownloaderSetup]); "Not now" only remembers that the question was answered.
 */
@Composable
fun MainDownloaderPrompt(onOpenSettings: () -> Unit) {
    if (!MainDownloaderSetup.available) return
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MainDownloaderState?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        state = MainDownloaderSetup.stateToAsk()
    }
    val current = state ?: return
    val s = LocalizationManager.Strings
    AlertDialog(
        onDismissRequest = {},
        title = { Text(s.mainSetupTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.mainSetupText, style = MaterialTheme.typography.bodyMedium)
                val steps = buildList {
                    if (current.torrentToDo) add(s.mainSetupTorrents)
                    if (current.clipboardToDo) add(s.mainSetupClipboard)
                    if (current.startupToDo) add(s.mainSetupStartup)
                    if (current.keepReadyToDo) add(s.mainSetupKeepReady)
                    if (current.extensionToDo) add(s.mainSetupExtension)
                }
                steps.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (current.torrentToDo && DefaultTorrentHandler.opensSystemPage) {
                    Text(
                        s.defaultAppSystemPageHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    s.mainSetupLater,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = MainDownloaderSetup.applyAll(current)
                        state = null
                        busy = false
                        if (result.openExtensionPage) {
                            openBrowserExtensionSettings()
                            onOpenSettings()
                        }
                    }
                }
            ) { Text(s.mainSetupAccept, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    MainDownloaderSetup.markAsked()
                    state = null
                }
            ) { Text(s.homeNotNow) }
        }
    )
}

/** Settings > General: is FetchJet the default for torrents, and does a browser have the extension. */
@Composable
internal fun MainDownloaderSettingsCard() {
    if (!MainDownloaderSetup.available) return
    val s = LocalizationManager.Strings
    val scope = rememberCoroutineScope()
    var isDefault by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(0) }
    val seenBrowsers by BridgePairing.seenBrowsers.collectAsState()

    LaunchedEffect(waiting) {
        isDefault = DefaultTorrentHandler.isDefault()
        if (waiting == 0) return@LaunchedEffect
        repeat(60) {
            if (isDefault == true) return@LaunchedEffect
            delay(2000)
            isDefault = DefaultTorrentHandler.isDefault()
        }
    }

    SettingsGroup(title = s.mainSetupGroup) {
        SettingRow(
            title = s.defaultAppRowTitle,
            subtitle = when (isDefault) {
                true -> s.defaultAppIsDefault
                false -> if (DefaultTorrentHandler.opensSystemPage) s.defaultAppNotDefault + "\n" + s.defaultAppSystemPageHint
                else s.defaultAppNotDefault
                null -> null
            }
        ) {
            if (isDefault == true) {
                StatusLabel(s.settingsReady, StatusCompleted)
            } else {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            DefaultTorrentHandler.makeDefault()
                            busy = false
                            waiting++
                        }
                    }
                ) { Text(s.defaultAppMakeDefault, fontWeight = FontWeight.SemiBold) }
            }
        }
        SettingDivider()
        SettingRow(
            title = s.mainSetupExtensionRow,
            subtitle = if (seenBrowsers.isNotEmpty()) s.mainSetupExtensionConnected else s.mainSetupExtensionMissing,
            onClick = { openBrowserExtensionSettings() }
        ) {
            if (seenBrowsers.isNotEmpty()) StatusLabel(s.settingsReady, StatusCompleted)
            else OutlinedButton(onClick = { openBrowserExtensionSettings() }) {
                Text(s.open, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
