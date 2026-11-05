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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.core.update.AppRelease
import com.michelle.raouf.fetch_jet.core.update.MICROSOFT_STORE_PAGE
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
    val fromStore = PlatformUpdater.updatesFromStore
    val asset = if (fromStore) null else PlatformUpdater.pickAsset(release.assets)
    val downloading = state.downloadProgress >= 0f
    val ready = state.installerPath != null

    AlertDialog(
        onDismissRequest = { if (!downloading) UpdateManager.dismiss() },
        shape = RoundedCornerShape(18.dp),
        title = {
            Column {
                Text(
                    "🎉 ${LocalizationManager.Strings.updateNewVersionAvailable}",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    LocalizationManager.Strings.updateVersionLine(
                        release.version,
                        state.currentVersion
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column {
                Text(
                    LocalizationManager.Strings.updateWhatsNew,
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
                            text = plainNotes(release.notesForDisplay).ifBlank { LocalizationManager.Strings.updateNoReleaseNotes },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                when {
                    downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            LocalizationManager.Strings.downloadingUpdate((state.downloadProgress * 100).toInt()),
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
                            if (PlatformUpdater.platformKey == "android")
                                LocalizationManager.Strings.updateDownloadedAndroid
                            else LocalizationManager.Strings.updateDownloadedDesktop,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    !state.canInstallInApp -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (fromStore) LocalizationManager.Strings.storeInstallsItselfOpenNow
                            else installHint(asset?.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.message?.takeIf { state.messageIsInstallError }
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
                    Text(if (PlatformUpdater.platformKey == "android") LocalizationManager.Strings.updateInstall else LocalizationManager.Strings.updateRestartAndUpdate)
                }

                state.canInstallInApp && asset != null -> Button(
                    onClick = { UpdateManager.installNow() },
                    enabled = !downloading,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (downloading) LocalizationManager.Strings.updateDownloading else LocalizationManager.Strings.updateDownloadAndInstall) }

                fromStore -> Button(
                    onClick = {
                        uriHandler.openUri(MICROSOFT_STORE_PAGE)
                        UpdateManager.dismiss()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(LocalizationManager.Strings.updateOpenMicrosoftStore) }

                else -> Button(
                    onClick = {
                        uriHandler.openUri(asset?.url ?: release.pageUrl)
                        UpdateManager.dismiss()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (asset != null) LocalizationManager.Strings.updateDownloadWithSize(
                            sizeText(asset.size)
                        ) else LocalizationManager.Strings.updateOpenReleasePage
                    )
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!downloading) {
                    TextButton(onClick = { UpdateManager.skip() }) { Text(LocalizationManager.Strings.updateSkipThisVersion) }
                }
                OutlinedButton(
                    onClick = { UpdateManager.dismiss() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(LocalizationManager.Strings.updateLater)
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

    SettingsGroup(title = LocalizationManager.Strings.sectionUpdates) {
        SettingRow(
            title = "FetchJet ${state.currentVersion}",
            subtitle = state.available?.let { LocalizationManager.Strings.versionIsAvailable(it.version) }
                ?: LocalizationManager.Strings.youHaveLatest
        ) {
            if (state.available != null) {
                Button(
                    onClick = { UpdateManager.showDialog() },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(LocalizationManager.Strings.updateSeeWhatsNew) }
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
                    Text(if (state.checking) LocalizationManager.Strings.updateChecking else LocalizationManager.Strings.updateCheckNow)
                }
            }
        }
        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.messageIsInstallError || state.messageIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        SettingDivider()
        SettingSwitch(
            title = LocalizationManager.Strings.checkUpdatesAutomatically,
            subtitle = LocalizationManager.Strings.checkUpdatesAutomaticallyHint,
            checked = state.autoCheck,
            onChange = { UpdateManager.setAutoCheck(it) }
        )
        SettingDivider()
        if (state.canInstallInApp) {
            SettingSwitch(
                title = LocalizationManager.Strings.downloadUpdatesAutomatically,
                subtitle = if (PlatformUpdater.platformKey == "android")
                    LocalizationManager.Strings.downloadUpdatesAutomaticallyHintAndroid
                else LocalizationManager.Strings.downloadUpdatesAutomaticallyHint,
                checked = state.autoInstall,
                enabled = state.autoCheck,
                onChange = { UpdateManager.setAutoInstall(it) }
            )
        } else {
            SettingRow(
                title = LocalizationManager.Strings.installingUpdates,
                subtitle = if (PlatformUpdater.updatesFromStore)
                    LocalizationManager.Strings.installingUpdatesStore
                else LocalizationManager.Strings.installingUpdatesManual
            )
        }
        SettingDivider()
        SettingRow(
            title = LocalizationManager.Strings.updateReleaseNotes,
            subtitle = LocalizationManager.Strings.updateReleaseNotesHint,
            onClick = { uriHandler.openUri("https://github.com/${com.michelle.raouf.fetch_jet.core.update.UPDATE_REPOSITORY}/releases") }
        ) {
            Text("↗", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun sizeText(bytes: Long): String = if (bytes > 0L) "(${formatBytes(bytes)})" else ""

private fun installHint(assetName: String?): String = when {
    assetName == null -> LocalizationManager.Strings.installHintReleasePage
    assetName.endsWith(
        ".apk",
        true
    ) -> LocalizationManager.Strings.installHintApk

    assetName.endsWith(
        ".ipa",
        true
    ) -> LocalizationManager.Strings.installHintIpa

    assetName.endsWith(
        ".dmg",
        true
    ) -> LocalizationManager.Strings.installHintDmg

    assetName.endsWith(
        ".deb",
        true
    ) -> LocalizationManager.Strings.installHintDeb

    else -> LocalizationManager.Strings.installHintOther
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
