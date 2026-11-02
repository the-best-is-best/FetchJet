package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.getPlatform
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun GeneralPage(
    isDarkMode: Boolean,
    unitKey: String,
    watchClipboard: Boolean,
    clipboardVideoPages: Boolean,
    onThemeToggled: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit,
    onWatchClipboardChanged: (Boolean) -> Unit,
    onClipboardVideoPagesChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        LanguageSettingsCard(onLanguageChanged)
        SettingsGroup(
            title = LocalizationManager.Strings.clipboardGroup,
            footer = LocalizationManager.Strings.clipboardFooter
        ) {
            SettingSwitch(
                title = LocalizationManager.Strings.watchClipboard,
                subtitle = if (watchClipboard) LocalizationManager.Strings.watchClipboardOn else LocalizationManager.Strings.settingOff,
                checked = watchClipboard,
                onChange = onWatchClipboardChanged
            )
            SettingDivider()
            SettingSwitch(
                title = LocalizationManager.Strings.alsoVideoPages,
                subtitle = LocalizationManager.Strings.alsoVideoPagesHint,
                checked = clipboardVideoPages,
                enabled = watchClipboard,
                onChange = onClipboardVideoPagesChanged
            )
        }
        val diag = com.michelle.raouf.fetch_jet.core.log.DiagnosticsLog
        if (diag.canExport) {
            var savedTo by remember { mutableStateOf<String?>(null) }
            SettingsGroup(
                title = LocalizationManager.Strings.diagnosticsGroup,
                footer = savedTo?.let { LocalizationManager.Strings.logSavedTo(it) }
                    ?: LocalizationManager.Strings.diagnosticsFooter
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        LocalizationManager.Strings.saveLogToDownloads,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            savedTo = diag.export() ?: LocalizationManager.Strings.logCouldNotSave
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(LocalizationManager.Strings.save) }
                }
            }
        }
        SettingsGroup(title = LocalizationManager.Strings.appearanceGroup) {
            SettingRow(
                LocalizationManager.Strings.themeTitle,
                LocalizationManager.Strings.themeHint
            ) {
                SegmentedPicker(
                    options = listOf(false, true),
                    selected = isDarkMode,
                    label = { if (it) LocalizationManager.Strings.settingsThemeDark else LocalizationManager.Strings.settingsThemeLight },
                    onSelect = { if (it != isDarkMode) onThemeToggled() }
                )
            }
            SettingDivider()
            SettingRow(
                LocalizationManager.Strings.speedShownIn,
                LocalizationManager.Strings.speedShownInHint
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                SegmentedPicker(
                    options = listOf("MB/s", "KB/s", "Mbps", "Kbps"),
                    selected = unitKey,
                    label = { it },
                    onSelect = onSpeedDisplayUnitChanged
                )
            }
        }
        if (StartupLauncher.isSupported) StartupSettingsCard()
    }
}

@Composable
internal fun DownloadsPage(
    destinationPath: String,
    sortIntoFolders: Boolean,
    defaultConnections: Int,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit,
    onSortIntoFoldersChanged: (Boolean) -> Unit,
    onDefaultConnectionsChanged: (Int) -> Unit
) {
    val defaultDownloads = remember { pathResolver.getDownloadsDirectory() }
    var showDirectoryPicker by remember { mutableStateOf(false) }
    if (showDirectoryPicker) {
        DirectoryPickerDialog(
            initialPath = destinationPath,
            pathResolver = pathResolver,
            onDirectorySelected = { selected ->
                onDestinationPathChanged(selected)
                showDirectoryPicker = false
            },
            onDismissRequest = { showDirectoryPicker = false }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(
            title = LocalizationManager.Strings.saveLocationGroup,
            footer = LocalizationManager.Strings.saveLocationFooter
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FetchJetTextField(
                    value = destinationPath,
                    onValueChange = onDestinationPathChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = LocalizationManager.Strings.settingsFolderPath,
                    leading = { Text("📁", fontSize = 15.sp) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDirectoryPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            LocalizationManager.Strings.settingsChooseFolder,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (pathResolver.isOpenFolderSupported()) {
                        OutlinedButton(
                            onClick = { pathResolver.openDirectoryInExplorer(destinationPath) },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(LocalizationManager.Strings.open) }
                    }
                    if (destinationPath != defaultDownloads) {
                        OutlinedButton(
                            onClick = { onDestinationPathChanged(defaultDownloads) },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(LocalizationManager.Strings.settingsUseDownloads) }
                    }
                }
            }
        }
        SettingsGroup(
            title = LocalizationManager.Strings.settingsConnectionsGroup,
            footer = LocalizationManager.Strings.connectionsFooter
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    LocalizationManager.Strings.settingsConnectionsPerDownload,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (listOf(0, 1, 4, 8, 16, 32)).forEach { n ->
                        val label =
                            if (n == 0) LocalizationManager.Strings.settingsConnectionsAuto else n.toString()
                        FilterChip(
                            selected = defaultConnections == n,
                            onClick = { onDefaultConnectionsChanged(n) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        SettingsGroup(
            title = LocalizationManager.Strings.organizeGroup,
            footer = LocalizationManager.Strings.organizeFooter
        ) {
            SettingSwitch(
                title = LocalizationManager.Strings.sortIntoFolders,
                subtitle = if (sortIntoFolders) LocalizationManager.Strings.sortIntoFoldersOn else LocalizationManager.Strings.sortIntoFoldersOff,
                checked = sortIntoFolders,
                onChange = onSortIntoFoldersChanged
            )
        }
        QueueSettingsCard()
    }
}

@Composable
internal fun SpeedPage(
    downloadLimit: Long,
    uploadLimit: Long,
    onDownloadLimit: (Long) -> Unit,
    onUploadLimit: (Long) -> Unit
) {
    val ios = getPlatform().name.startsWith("iOS", ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(
            footer = if (ios) LocalizationManager.Strings.speedLimitsFooterIos else LocalizationManager.Strings.speedLimitsFooter
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TrafficLimitSelector(
                    title = LocalizationManager.Strings.speedLimitDownload,
                    speedBytesPerSec = downloadLimit,
                    onSpeedChanged = onDownloadLimit
                )
            }
            SettingDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                TrafficLimitSelector(
                    title = LocalizationManager.Strings.speedLimitUploadTorrents,
                    speedBytesPerSec = uploadLimit,
                    onSpeedChanged = onUploadLimit
                )
            }
        }
    }
}

@Composable
internal fun TorrentPage(
    disableTorrentUpload: Boolean,
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    showEngine: Boolean,
    isTorrentEngineReady: Boolean,
    torrentEngineStatus: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(title = LocalizationManager.Strings.torrentSharingGroup) {
            SettingSwitch(
                title = LocalizationManager.Strings.downloadOnlyNoUpload,
                subtitle = LocalizationManager.Strings.downloadOnlyNoUploadHint,
                checked = disableTorrentUpload,
                onChange = onDisableTorrentUploadToggled
            )
        }
        if (showEngine) {
            SettingsGroup(title = LocalizationManager.Strings.torrentEngineGroup) {
                SettingRow(
                    title = "libtorrent",
                    subtitle = localizeStep(torrentEngineStatus).ifBlank { null }
                ) {
                    if (isTorrentEngineReady) StatusLabel(
                        LocalizationManager.Strings.settingsReady,
                        StatusCompleted
                    )
                    else StatusLabel(
                        LocalizationManager.Strings.settingsNotReady,
                        MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

internal fun folderName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

internal fun limitText(bytes: Long): String =
    if (bytes >= 1024L * 1024L) {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        (if (mb == mb.toLong().toDouble()) mb.toLong()
            .toString() else ((mb * 10).toLong() / 10.0).toString()) + " MB/s"
    } else "${bytes / 1024L} KB/s"

// ---------------------------------------------------------------- browser extension

private const val AMO_URL = "https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/"
private const val EDGE_STORE_URL =
    "https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj"
private const val MANUAL_INSTALL_URL = "https://the-best-is-best.github.io/FetchJet/#extension"

/**
 * Where to get the extension for each browser (like FDM's "Browser Integration"). The stores
 * keep it updated; Chrome has no store listing, so it gets the .zip with the developer-mode steps.
 */
@Composable
internal fun BrowserExtensionPage(isDesktop: Boolean) {
    val uri = LocalUriHandler.current

    @Composable
    fun storeButton(text: String, url: String, primary: Boolean = true) {
        if (primary) Button(onClick = { uri.openUri(url) }, shape = RoundedCornerShape(10.dp)) {
            Text(text, fontWeight = FontWeight.SemiBold)
        } else OutlinedButton(onClick = { uri.openUri(url) }, shape = RoundedCornerShape(10.dp)) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(
            title = LocalizationManager.Strings.extInstallGroup,
            footer = LocalizationManager.Strings.extensionInstallFooter
        ) {
            if (isDesktop) {
                val bi = com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
                if (bi.canAddToEdgeAutomatically) {
                    // Windows: the registry hand-off, like IDM. Edge asks to turn it on.
                    var registered by remember { mutableStateOf(bi.isEdgeRegistered()) }
                    var note by remember { mutableStateOf<String?>(null) }
                    SettingRow(
                        "Microsoft Edge",
                        note
                            ?: if (registered) LocalizationManager.Strings.edgeAddedHint
                            else LocalizationManager.Strings.edgeFromStoreHint
                    ) {
                        if (registered) storeButton(
                            LocalizationManager.Strings.openInEdgeStore,
                            EDGE_STORE_URL,
                            primary = false
                        )
                        else Button(onClick = {
                            bi.addToEdge()
                                .onSuccess {
                                    registered = true; note =
                                    LocalizationManager.Strings.edgeAddedNote
                                }
                                .onFailure { uri.openUri(EDGE_STORE_URL) }
                        }, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                LocalizationManager.Strings.extAddToEdge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    SettingRow(
                        "Microsoft Edge",
                        LocalizationManager.Strings.edgeFromStoreHint
                    ) {
                        storeButton(LocalizationManager.Strings.extAddToEdge, EDGE_STORE_URL)
                    }
                }
                SettingDivider()
                SettingRow("Firefox", LocalizationManager.Strings.firefoxFromStoreHint) {
                    storeButton(LocalizationManager.Strings.extAddToFirefox, AMO_URL)
                }
                SettingDivider()
                SettingRow(
                    "Chrome, Brave, Opera, Vivaldi",
                    LocalizationManager.Strings.chromeZipHint
                ) {
                    storeButton(
                        LocalizationManager.Strings.getTheZip,
                        MANUAL_INSTALL_URL,
                        primary = false
                    )
                }
            } else {
                SettingRow(
                    "Firefox for Android",
                    LocalizationManager.Strings.firefoxAndroidHint
                ) {
                    storeButton(LocalizationManager.Strings.extAddToFirefox, AMO_URL)
                }
                val bi = com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
                if (bi.canKeepReadyForBrowser) {
                    var keepReady by remember { mutableStateOf(bi.isKeptReadyForBrowser()) }
                    SettingDivider()
                    SettingSwitch(
                        title = LocalizationManager.Strings.keepReadyForBrowser,
                        subtitle = if (keepReady)
                            LocalizationManager.Strings.keepReadyOn
                        else
                            LocalizationManager.Strings.keepReadyOff,
                        checked = keepReady,
                        onChange = { keepReady = it; bi.setKeepReadyForBrowser(it) }
                    )
                }
                if (bi.canPopUpOverBrowser) {
                    var allowed by remember { mutableStateOf(bi.isPopUpAllowed()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(1500.milliseconds); allowed =
                                bi.isPopUpAllowed()
                        }
                    }
                    SettingDivider()
                    SettingSwitch(
                        title = LocalizationManager.Strings.popUpOverBrowser,
                        subtitle = if (allowed) LocalizationManager.Strings.popUpOverBrowserOn
                        else LocalizationManager.Strings.popUpOverBrowserOff,
                        checked = allowed,
                        onChange = { bi.requestPopUpPermission() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingsCard(onLanguageChanged: (String) -> Unit) {
    val i18n = com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
    val s = com.tbib.kmp_localization.LocalizationManager.Strings
    var open by remember { mutableStateOf(false) }
    val current = i18n.choice
    val currentName = if (current == i18n.SYSTEM) "${s.systemLanguage} (${s.languageName})"
    else com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages.firstOrNull { it.localeCode == current }?.languageName
        ?: current
    SettingsGroup(title = s.language) {
        SettingRow(s.language, s.languageHint, onClick = { open = true }) {
            Box {
                androidx.compose.material3.TextButton(onClick = { open = true }) {
                    Text("$currentName  ▾", fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false }) {
                    val options =
                        listOf(i18n.SYSTEM) + com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages.map { it.localeCode }
                    options.forEach { code ->
                        val name = if (code == i18n.SYSTEM) s.systemLanguage
                        else com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages.first { it.localeCode == code }.languageName
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    name,
                                    fontWeight = if (code == current) FontWeight.Bold else FontWeight.Normal,
                                    color = if (code == current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                open = false
                                if (code != current) onLanguageChanged(code)
                            }
                        )
                    }
                }
            }
        }
    }
}
