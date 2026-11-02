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
    onSpeedDisplayUnitChanged: (String) -> Unit,
    onWatchClipboardChanged: (Boolean) -> Unit,
    onClipboardVideoPagesChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(
            title = "Clipboard",
            footer = "Copy a link to a file (zip, mp4, pdf, apk…) or a magnet link anywhere, and FetchJet asks whether to download it."
        ) {
            SettingSwitch(
                title = "Watch the clipboard",
                subtitle = if (watchClipboard) "On · copied file links are offered for download" else "Off",
                checked = watchClipboard,
                onChange = onWatchClipboardChanged
            )
            SettingDivider()
            SettingSwitch(
                title = "Also video pages",
                subtitle = "YouTube, Facebook, TikTok, Vimeo… links are offered too (not only files)",
                checked = clipboardVideoPages,
                enabled = watchClipboard,
                onChange = onClipboardVideoPagesChanged
            )
        }
        val diag = com.michelle.raouf.fetch_jet.core.log.DiagnosticsLog
        if (diag.canExport) {
            var savedTo by remember { mutableStateOf<String?>(null) }
            SettingsGroup(
                title = "Diagnostics",
                footer = savedTo?.let { "Saved: $it — send this file when reporting a problem." }
                    ?: "Saves the app's log (what yt-dlp and the engines did) to your Downloads folder."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Save log to Downloads",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { savedTo = diag.export() ?: "could not save" },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save") }
                }
            }
        }
        SettingsGroup(title = "Appearance") {
            SettingRow("Theme", "Light or dark look for every screen") {
                SegmentedPicker(
                    options = listOf(false, true),
                    selected = isDarkMode,
                    label = { if (it) "Dark" else "Light" },
                    onSelect = { if (it != isDarkMode) onThemeToggled() }
                )
            }
            SettingDivider()
            SettingRow(
                "Speed shown in",
                "Bytes (MB/s) like file sizes, or bits (Mbps) like your internet plan"
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
            title = "Save location",
            footer = "New downloads go here unless you pick another folder when adding them."
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FetchJetTextField(
                    value = destinationPath,
                    onValueChange = onDestinationPathChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Folder path",
                    leading = { Text("📁", fontSize = 15.sp) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDirectoryPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Choose folder…", fontWeight = FontWeight.SemiBold) }
                    if (pathResolver.isOpenFolderSupported()) {
                        OutlinedButton(
                            onClick = { pathResolver.openDirectoryInExplorer(destinationPath) },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Open") }
                    }
                    if (destinationPath != defaultDownloads) {
                        OutlinedButton(
                            onClick = { onDestinationPathChanged(defaultDownloads) },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Use Downloads") }
                    }
                }
            }
        }
        SettingsGroup(
            title = "Connections",
            footer = "How many pieces a direct download is split into. Each piece is a connection; when one finishes it takes half of the biggest remaining piece, so none sits idle. Auto picks by file size (4–8). Some servers limit connections per client."
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Connections per download",
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
                        val label = if (n == 0) "Auto" else n.toString()
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
            title = "Organize",
            footer = "Sub-folders are created inside the save location: Video, Music, Archives, Documents, Programs, Images, Torrents."
        ) {
            SettingSwitch(
                title = "Sort into folders by type",
                subtitle = if (sortIntoFolders) "On · e.g. Downloads/Video/lecture.mp4" else "Off · everything goes straight into the folder",
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
            footer = "Applied right away and shared between the downloads that are running." +
                    if (ios) " On iOS, HTTP downloads already running in the background keep their speed; " +
                            "the limit applies to the ones started after it." else ""
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TrafficLimitSelector(
                    title = "Download",
                    speedBytesPerSec = downloadLimit,
                    onSpeedChanged = onDownloadLimit
                )
            }
            SettingDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                TrafficLimitSelector(
                    title = "Upload (torrents)",
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
        SettingsGroup(title = "Sharing") {
            SettingSwitch(
                title = "Download only (don't upload)",
                subtitle = "Saves your upload bandwidth and data quota. Some trackers download slower for peers that don't share.",
                checked = disableTorrentUpload,
                onChange = onDisableTorrentUploadToggled
            )
        }
        if (showEngine) {
            SettingsGroup(title = "Engine") {
                SettingRow(
                    title = "libtorrent",
                    subtitle = torrentEngineStatus.ifBlank { null }
                ) {
                    if (isTorrentEngineReady) StatusLabel("Ready", StatusCompleted)
                    else StatusLabel("Not ready", MaterialTheme.colorScheme.error)
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
            title = "Install",
            footer = "With the extension, a \"Download with FetchJet\" button shows on videos and links, " +
                    "and the browser can hand its downloads to FetchJet. It talks only to this app on your device."
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
                            ?: if (registered) "Added · restart Edge and click \"Turn on extension\" when it asks"
                            else "From Microsoft Edge Add-ons · updates by itself"
                    ) {
                        if (registered) storeButton(
                            "Open in Edge store",
                            EDGE_STORE_URL,
                            primary = false
                        )
                        else Button(onClick = {
                            bi.addToEdge()
                                .onSuccess {
                                    registered = true; note =
                                    "Done: restart Edge, it will ask to turn FetchJet on."
                                }
                                .onFailure { uri.openUri(EDGE_STORE_URL) }
                        }, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "Add to Edge",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    SettingRow(
                        "Microsoft Edge",
                        "From Microsoft Edge Add-ons · updates by itself"
                    ) {
                        storeButton("Add to Edge", EDGE_STORE_URL)
                    }
                }
                SettingDivider()
                SettingRow("Firefox", "From Firefox Add-ons · updates by itself") {
                    storeButton("Add to Firefox", AMO_URL)
                }
                SettingDivider()
                SettingRow(
                    "Chrome, Brave, Opera, Vivaldi",
                    "Not in the Chrome Web Store: install the .zip in developer mode (steps on the page). It does not update by itself."
                ) {
                    storeButton("Get the .zip", MANUAL_INSTALL_URL, primary = false)
                }
            } else {
                SettingRow(
                    "Firefox for Android",
                    "Open this in Firefox on this phone. Links you send go straight to this app."
                ) {
                    storeButton("Add to Firefox", AMO_URL)
                }
                val bi = com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
                if (bi.canKeepReadyForBrowser) {
                    var keepReady by remember { mutableStateOf(bi.isKeptReadyForBrowser()) }
                    SettingDivider()
                    SettingSwitch(
                        title = "Keep ready for the browser",
                        subtitle = if (keepReady)
                            "Video qualities show right in Firefox. A quiet notification stays while this is on."
                        else
                            "Off: Firefox opens FetchJet and you pick the quality here. On: qualities show in Firefox (keeps a notification).",
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
                        title = "Pop up over the browser",
                        subtitle = if (allowed) "On · FetchJet opens by itself when Firefox sends a link"
                        else "Off · a notification appears instead; tap to allow \"display over other apps\"",
                        checked = allowed,
                        onChange = { bi.requestPopUpPermission() }
                    )
                }
            }
        }
    }
}
