package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.update.AppRelease
import com.michelle.raouf.fetch_jet.core.update.PlatformUpdater
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.utils.formatBytes

/**
 * Put once at the root of the app: starts the automatic check and shows the
 * "new version" dialog when one is found.
 */
@Composable
fun UpdateDialogHost() {
    val state by UpdateManager.state.collectAsState()
    LaunchedEffect(Unit) { UpdateManager.onAppStart() }
    val release = state.available
    if (state.supported && state.showDialog && release != null) {
        UpdateDialog(state, release)
    }
}

@Composable
private fun UpdateDialog(state: UpdateState, release: AppRelease) {
    val uriHandler = LocalUriHandler.current
    val asset = PlatformUpdater.pickAsset(release.assets)
    val downloading = state.downloadProgress >= 0f
    val ready = state.installerPath != null

    AlertDialog(
        onDismissRequest = { if (!downloading) UpdateManager.dismiss() },
        shape = RoundedCornerShape(18.dp),
        title = {
            Column {
                Text("🎉 New version available", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "FetchJet ${release.version}  ·  you have ${state.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column {
                Text(
                    "What's new",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = plainNotes(release.notes).ifBlank { "No release notes." },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                when {
                    downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Downloading the update… ${(state.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (state.downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // size unknown
                        }
                    }

                    ready -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "The update is downloaded. FetchJet will close, install it and open again. " +
                                    "Running downloads are paused and can be resumed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    !state.canInstallInApp -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            installHint(asset?.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.message?.takeIf { it.startsWith("Update download failed") || it.startsWith("Could not start") }
                    ?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
            }
        },
        confirmButton = {
            when {
                state.canInstallInApp && ready -> Button(
                    onClick = { UpdateManager.installNow() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restart & update")
                }

                state.canInstallInApp && asset != null -> Button(
                    onClick = { UpdateManager.installNow() },
                    enabled = !downloading,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (downloading) "Downloading…" else "Download & install") }

                else -> Button(
                    onClick = {
                        uriHandler.openUri(asset?.url ?: release.pageUrl)
                        UpdateManager.dismiss()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (asset != null) "Download ${sizeText(asset.size)}" else "Open release page") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!downloading) {
                    TextButton(onClick = { UpdateManager.skip() }) { Text("Skip this version") }
                }
                OutlinedButton(
                    onClick = { UpdateManager.dismiss() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Later")
                }
            }
        }
    )
}

/** "Updates" card for the Settings page. */
@Composable
fun UpdatesSettingsCard() {
    val state by UpdateManager.state.collectAsState()
    if (!state.supported) return
    val uriHandler = LocalUriHandler.current

    SettingsGroup(title = "Updates") {
        SettingRow(
            title = "FetchJet ${state.currentVersion}",
            subtitle = state.available?.let { "Version ${it.version} is available" }
                ?: "You have the version installed here"
        ) {
            if (state.available != null) {
                Button(
                    onClick = { UpdateManager.showDialog() },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("See what's new") }
            } else {
                OutlinedButton(
                    onClick = { UpdateManager.checkNow() },
                    enabled = !state.checking,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.checking) "Checking…" else "Check now")
                }
            }
        }
        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Could not") || it.contains("failed")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        SettingDivider()
        SettingSwitch(
            title = "Check for updates automatically",
            subtitle = "Looks for a new version when FetchJet starts (at most twice a day).",
            checked = state.autoCheck,
            onChange = { UpdateManager.setAutoCheck(it) }
        )
        SettingDivider()
        if (state.canInstallInApp) {
            SettingSwitch(
                title = "Download updates automatically",
                subtitle = "Downloads a new version in the background; you only confirm the restart.",
                checked = state.autoInstall,
                enabled = state.autoCheck,
                onChange = { UpdateManager.setAutoInstall(it) }
            )
        } else {
            SettingRow(
                title = "Installing updates",
                subtitle = "On this device FetchJet tells you about a new version and links to it; you install it."
            )
        }
        SettingDivider()
        SettingRow(
            title = "Release notes",
            subtitle = "Every version and what changed",
            onClick = { uriHandler.openUri("https://github.com/${com.michelle.raouf.fetch_jet.core.update.UPDATE_REPOSITORY}/releases") }
        ) {
            Text("↗", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
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

private fun sizeText(bytes: Long): String = if (bytes > 0L) "(${formatBytes(bytes)})" else ""

private fun installHint(assetName: String?): String = when {
    assetName == null -> "Open the release page to download the new version."
    assetName.endsWith(
        ".apk",
        true
    ) -> "Download the APK and open it to install the update over this version."

    assetName.endsWith(
        ".ipa",
        true
    ) -> "Download the .ipa and install it with AltStore / Sideloadly, as you installed FetchJet."

    assetName.endsWith(
        ".dmg",
        true
    ) -> "Download the .dmg, open it and drag FetchJet to Applications (replace the old one)."

    assetName.endsWith(
        ".deb",
        true
    ) -> "Download the .deb and install it (double-click, or sudo apt install ./file.deb)."

    else -> "Download the new version and install it over this one."
}

/** GitHub release notes are markdown: show them as readable plain text. */
private fun plainNotes(md: String): String = md.lineSequence()
    .map { line ->
        line.trimEnd()
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace("**", "")
            .replace(Regex("\\[([^\\]]+)]\\((https?://[^)]+)\\)"), "$1")
            .replace(Regex("<!--.*?-->"), "")
    }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
