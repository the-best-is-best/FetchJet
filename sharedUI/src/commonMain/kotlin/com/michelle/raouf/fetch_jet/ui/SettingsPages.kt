package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.core.power.SpeedSchedule
import com.michelle.raouf.fetch_jet.core.power.SpeedWindow
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.tbib.kmp_localization.LocalizationManager
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
        val visibleToDevices by com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
            .visibleToDevices.collectAsState()
        SettingsGroup(title = LocalizationManager.Strings.sysDeviceSendTitle) {
            SettingSwitch(
                title = LocalizationManager.Strings.visibleToDevices,
                subtitle = LocalizationManager.Strings.visibleToDevicesHint,
                checked = visibleToDevices,
                onChange = { com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.setVisibleToDevices(it) }
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
        WhenDoneSettingsCard()
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
    onDefaultConnectionsChanged: (Int) -> Unit,
    lowBytes: Long = 256L * 1024,
    lowUnit: String = "KB/s",
    mediumBytes: Long = 2L * 1024 * 1024,
    mediumUnit: String = "MB/s",
    fastBytes: Long = 3L * 1024 * 1024,
    fastUnit: String = "MB/s",
    highBytes: Long = 20L * 1024 * 1024,
    highUnit: String = "MB/s",
    onPresetChanged: (com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset, Long, String) -> Unit = { _, _, _ -> }
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
            title = LocalizationManager.Strings.speedPresetsTitle,
            footer = LocalizationManager.Strings.speedPresetsFooter
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LocalizationManager.Strings.speedPresetHigh,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = LocalizationManager.Strings.speedLimitUnlimited,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            SettingDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                SpeedPresetRow(
                    name = LocalizationManager.Strings.speedPresetFast,
                    bytes = fastBytes,
                    unit = fastUnit,
                    onChanged = { b, u ->
                        onPresetChanged(
                            com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset.FAST,
                            b, u
                        )
                    }
                )
            }
            SettingDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                SpeedPresetRow(
                    name = LocalizationManager.Strings.speedPresetMedium,
                    bytes = mediumBytes,
                    unit = mediumUnit,
                    onChanged = { b, u ->
                        onPresetChanged(
                            com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset.MEDIUM,
                            b, u
                        )
                    }
                )
            }
            SettingDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                SpeedPresetRow(
                    name = LocalizationManager.Strings.speedPresetLow,
                    bytes = lowBytes,
                    unit = lowUnit,
                    onChanged = { b, u ->
                        onPresetChanged(
                            com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset.LOW,
                            b, u
                        )
                    }
                )
            }
        }
        SpeedScheduleCard()
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

/**
 * Time-of-day speed schedule (reads/writes [SpeedSchedule] directly). Each window has a start /
 * end time and a speed cap; outside every window the speed is unlimited.
 */
@Composable
private fun SpeedScheduleCard() {
    val enabled by SpeedSchedule.enabled.collectAsState()
    val windows by SpeedSchedule.windows.collectAsState()
    val activeCap by SpeedSchedule.activeCap.collectAsState()

    SettingsGroup(footer = LocalizationManager.Strings.speedScheduleFooter) {
        SettingSwitch(
            title = LocalizationManager.Strings.speedScheduleTitle,
            subtitle = if (enabled && activeCap != null)
                LocalizationManager.Strings.speedScheduleActive else null,
            checked = enabled,
            onChange = { SpeedSchedule.setEnabled(it) }
        )
        if (enabled) {
            if (windows.isEmpty()) {
                SettingDivider()
                Column(Modifier.padding(16.dp)) {
                    Text(
                        LocalizationManager.Strings.speedScheduleEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            windows.forEachIndexed { index, window ->
                SettingDivider()
                key(index) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            TimeField(
                                label = LocalizationManager.Strings.speedScheduleFrom,
                                minutes = window.startMinutes,
                                onChanged = {
                                    SpeedSchedule.updateWindow(
                                        index,
                                        window.copy(startMinutes = it)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TimeField(
                                label = LocalizationManager.Strings.speedScheduleTo,
                                minutes = window.endMinutes,
                                onChanged = {
                                    SpeedSchedule.updateWindow(index, window.copy(endMinutes = it))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                                FieldIconButton(
                                    text = "✕",
                                    onClick = { SpeedSchedule.removeWindow(index) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SpeedPresetRow(
                            name = "",
                            bytes = window.capBytes,
                            unit = window.capUnit,
                            onChanged = { b, u ->
                                SpeedSchedule.updateWindow(
                                    index,
                                    window.copy(capBytes = b, capUnit = u)
                                )
                            }
                        )
                    }
                }
            }
            SettingDivider()
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        SpeedSchedule.addWindow(
                            SpeedWindow(
                                startMinutes = 8 * 60,
                                endMinutes = 18 * 60,
                                capBytes = 1L * 1024 * 1024,
                                capUnit = "MB/s"
                            )
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "+  " + LocalizationManager.Strings.speedScheduleAdd,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    onChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(minutesToHm12(minutes)) }
    var isPm by remember { mutableStateOf((((minutes % 1440) + 1440) % 1440) >= 12 * 60) }
    FetchJetTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = raw.filter { it.isDigit() || it == ':' }.take(5)
            text = sanitized
            parseHm12(sanitized, isPm)?.let(onChanged)
        },
        modifier = modifier,
        label = label,
        placeholder = "8:00",
        height = 42.dp,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailing = {
            AmPmToggle(isPm) { next ->
                isPm = next
                parseHm12(text, next)?.let(onChanged)
            }
        }
    )
}

@Composable
private fun AmPmToggle(isPm: Boolean, onToggle: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(primary.copy(alpha = 0.14f))
            .clickable { onToggle(!isPm) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            if (isPm) "PM" else "AM",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = primary,
            maxLines = 1
        )
    }
}

private fun minutesToHm12(m: Int): String {
    val mm = ((m % 1440) + 1440) % 1440
    val h24 = mm / 60
    val min = mm % 60
    val h12 = when (h24 % 12) {
        0 -> 12
        else -> h24 % 12
    }
    return "$h12:${min.toString().padStart(2, '0')}"
}

private fun parseHm12(text: String, isPm: Boolean): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 1..12 || m !in 0..59) return null
    val h24 = when {
        isPm && h == 12 -> 12
        isPm -> h + 12
        !isPm && h == 12 -> 0
        else -> h
    }
    return h24 * 60 + m
}

@Composable
internal fun TorrentPage(
    disableTorrentUpload: Boolean,
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    uploadLimit: Long,
    onUploadLimit: (Long) -> Unit,
    showEngine: Boolean,
    isTorrentEngineReady: Boolean,
    torrentEngineStatus: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                TrafficLimitSelector(
                    title = LocalizationManager.Strings.speedLimitUploadTorrents,
                    speedBytesPerSec = uploadLimit,
                    onSpeedChanged = onUploadLimit
                )
            }
        }
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
        BrowserConnectionsGroup()
    }
}

/**
 * Extensions the user allowed or blocked at the pairing dialog ([BridgePairing]). Shown so the
 * user can see what got in and revoke it, or let a blocked origin ask again. Hidden when empty.
 */
@Composable
private fun BrowserConnectionsGroup() {
    val pairing = com.michelle.raouf.fetch_jet.core.network.BridgePairing
    val allowed by pairing.legacyAllowed.collectAsState()
    val blocked by pairing.blocked.collectAsState()
    if (allowed.isEmpty() && blocked.isEmpty()) return
    SettingsGroup(
        title = LocalizationManager.Strings.extConnectionsGroup,
        footer = LocalizationManager.Strings.extConnectionsFooter
    ) {
        var first = true
        allowed.sorted().forEach { origin ->
            if (!first) SettingDivider()
            first = false
            SettingRow(origin, LocalizationManager.Strings.extAllowedLabel) {
                OutlinedButton(
                    onClick = { pairing.revokeAllowed(origin) },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(LocalizationManager.Strings.extRevoke) }
            }
        }
        blocked.sorted().forEach { origin ->
            if (!first) SettingDivider()
            first = false
            SettingRow(origin, LocalizationManager.Strings.extBlockedLabel) {
                OutlinedButton(
                    onClick = { pairing.unblock(origin) },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(LocalizationManager.Strings.extUnblock) }
            }
        }
    }
}

@Composable
private fun LanguageSettingsCard(onLanguageChanged: (String) -> Unit) {
    val i18n = com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
    val s = LocalizationManager.Strings
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
