package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.getPlatform
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused

/** The pages of Settings, in the order of the side list. */
private enum class SettingsSection(val title: String, val glyph: String, val blurb: String) {
    GENERAL("General", "◐", "Look of the app and how it starts"),
    DOWNLOADS("Downloads", "↓", "Where files go and how many run at once"),
    BROWSER("Browser extension", "⧉", "Send links and videos from your browser to FetchJet"),
    SPEED("Speed limits", "≋", "Cap the bandwidth FetchJet uses"),
    MEDIA("Media tools", "▶", "yt-dlp and FFmpeg for video sites"),
    TORRENT("BitTorrent", "⇅", "Peers, uploading and the torrent engine"),
    UPDATES("Updates", "↻", "Your version and new releases")
}

/**
 * Settings: a list of pages on the left (each with a live one-line summary: "Dark · MB/s",
 * "3 at a time", "Setup needed"...) and the chosen page on the right. On narrow screens the
 * list is the first screen and a page opens over it with a back arrow.
 * Every change is saved and applied at once - there is no Save button to forget.
 */
@Composable
fun FdmSettingsPanel(
    destinationPath: String,
    disableTorrentUpload: Boolean,
    isDarkMode: Boolean,
    globalDownloadSpeedLimitBytes: Long,
    globalUploadSpeedLimitBytes: Long,
    speedDisplayUnit: String = "MB/s",
    ytDlpStatus: String,
    isYtDlpInstalled: Boolean,
    isFfmpegInstalled: Boolean,
    toolsDirectoryPath: String,
    torrentEngineStatus: String = "",
    isTorrentEngineReady: Boolean = true,
    isDownloadingYtDlp: Boolean,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit,
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    onThemeToggled: () -> Unit,
    onGlobalDownloadSpeedLimitChanged: (Long) -> Unit,
    onGlobalUploadSpeedLimitChanged: (Long) -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit = {},
    onDownloadYtDlpClicked: () -> Unit,
    onApplySettings: () -> Unit
) {
    val isJvm = getPlatform().isJvm
    val updateState by UpdateManager.state.collectAsState()
    val maxAtOnce by QueueManagerRepository.getInstance().maxConcurrentDownloads.collectAsState()
    val sections = SettingsSection.entries.filter {
        when (it) {
            SettingsSection.MEDIA -> isJvm
            SettingsSection.TORRENT -> AppFeatures.torrent
            SettingsSection.UPDATES -> updateState.supported
            else -> true
        }
    }
    val unitKey = when (speedDisplayUnit) {
        "BYTES_PER_SEC", "BOTH" -> "MB/s"
        "BITS_PER_SEC" -> "Mbps"
        else -> speedDisplayUnit
    }
    val toolsReady = isYtDlpInstalled && isFfmpegInstalled

    // The live one-line summary under each page name.
    fun summary(s: SettingsSection): Pair<String, Color?> = when (s) {
        SettingsSection.GENERAL -> (if (isDarkMode) "Dark" else "Light") + " · " + unitKey to null
        SettingsSection.DOWNLOADS -> folderName(destinationPath) + " · " +
                (if (maxAtOnce <= 0) "no limit" else "$maxAtOnce at a time") to null

        SettingsSection.SPEED ->
            if (globalDownloadSpeedLimitBytes <= 0 && globalUploadSpeedLimitBytes <= 0) "No limit" to null
            else listOfNotNull(
                if (globalDownloadSpeedLimitBytes > 0) "↓ " + limitText(
                    globalDownloadSpeedLimitBytes
                ) else null,
                if (globalUploadSpeedLimitBytes > 0) "↑ " + limitText(globalUploadSpeedLimitBytes) else null
            ).joinToString("  ") to StatusPaused

        SettingsSection.MEDIA -> if (toolsReady) "Ready" to StatusCompleted else "Setup needed" to StatusPaused
        SettingsSection.TORRENT -> (if (disableTorrentUpload) "Uploading off" else "Uploading on") to null
        SettingsSection.BROWSER -> (if (isJvm) "Edge · Firefox · Chrome" else "Firefox for Android") to null
        SettingsSection.UPDATES -> updateState.available?.let { "${it.version} available" to StatusCompleted }
            ?: ("Version " + updateState.currentVersion to null)
    }

    var openSection by remember { mutableStateOf<SettingsSection?>(null) }

    @Composable
    fun page(s: SettingsSection) {
        when (s) {
            SettingsSection.GENERAL -> GeneralPage(
                isDarkMode,
                unitKey,
                onThemeToggled,
                onSpeedDisplayUnitChanged
            )

            SettingsSection.DOWNLOADS -> DownloadsPage(
                destinationPath,
                pathResolver,
                onDestinationPathChanged
            )

            SettingsSection.SPEED -> SpeedPage(
                globalDownloadSpeedLimitBytes, globalUploadSpeedLimitBytes,
                onGlobalDownloadSpeedLimitChanged, onGlobalUploadSpeedLimitChanged
            )

            SettingsSection.MEDIA -> MediaToolsPage(
                ytDlpStatus, isYtDlpInstalled, isFfmpegInstalled, toolsDirectoryPath,
                isDownloadingYtDlp, pathResolver, onDownloadYtDlpClicked
            )

            SettingsSection.TORRENT -> TorrentPage(
                disableTorrentUpload, onDisableTorrentUploadToggled,
                showEngine = isJvm, isTorrentEngineReady, torrentEngineStatus
            )

            SettingsSection.UPDATES -> UpdatesSettingsCard()
            SettingsSection.BROWSER -> BrowserExtensionPage(isJvm)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            val current = openSection?.takeIf { it in sections } ?: sections.first()
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(250.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sections.forEach { s ->
                        val (hint, hintColor) = summary(s)
                        SectionNavItem(s, hint, hintColor, selected = s == current) {
                            openSection = s
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Changes are saved automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 28.dp)
                ) {
                    Column(modifier = Modifier.widthIn(max = 720.dp)) {
                        PageHeader(current)
                        page(current)
                    }
                }
            }
        } else {
            val current = openSection?.takeIf { it in sections }
            if (current == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sections.forEach { s ->
                        val (hint, hintColor) = summary(s)
                        SectionNavItem(
                            s,
                            hint,
                            hintColor,
                            selected = false,
                            chevron = true
                        ) { openSection = s }
                    }
                    Text(
                        "Changes are saved automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { openSection = null }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("←", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "All settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    PageHeader(current)
                    page(current)
                }
            }
        }
    }
}

@Composable
private fun PageHeader(s: SettingsSection) {
    Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        s.blurb,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun SectionNavItem(
    s: SettingsSection,
    hint: String,
    hintColor: Color?,
    selected: Boolean,
    chevron: Boolean = false,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                s.glyph,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                s.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                hint,
                fontSize = 12.sp,
                color = hintColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chevron) {
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun GeneralPage(
    isDarkMode: Boolean,
    unitKey: String,
    onThemeToggled: () -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
private fun DownloadsPage(
    destinationPath: String,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit
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
                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = onDestinationPathChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Folder path") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
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
        QueueSettingsCard()
    }
}

@Composable
private fun SpeedPage(
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
private fun MediaToolsPage(
    ytDlpStatus: String,
    isYtDlpInstalled: Boolean,
    isFfmpegInstalled: Boolean,
    toolsDirectoryPath: String,
    isDownloadingYtDlp: Boolean,
    pathResolver: PathResolver,
    onDownloadYtDlpClicked: () -> Unit
) {
    val ready = isYtDlpInstalled && isFfmpegInstalled
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(title = "Tools") {
            SettingRow("yt-dlp", "Finds the video and audio on YouTube and other sites") {
                if (isYtDlpInstalled) StatusLabel("Ready", StatusCompleted)
                else StatusLabel("Missing", MaterialTheme.colorScheme.error)
            }
            SettingDivider()
            SettingRow("FFmpeg", "Joins video + audio (1080p and up), cuts clips, makes MP3") {
                if (isFfmpegInstalled) StatusLabel("Ready", StatusCompleted)
                else StatusLabel("Missing · up to 720p", StatusPaused)
            }
            if (toolsDirectoryPath.isNotBlank()) {
                SettingDivider()
                SettingRow(
                    title = "Tools folder",
                    subtitle = toolsDirectoryPath,
                    onClick = { pathResolver.openDirectoryInExplorer(toolsDirectoryPath) }
                ) {
                    Text(
                        "Open",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val content: @Composable () -> Unit = {
                if (isDownloadingYtDlp) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (ready) "Updating…" else "Downloading tools…")
                } else {
                    Text(
                        if (ready) "Update tools" else "Download the tools",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (ready) {
                OutlinedButton(
                    onClick = onDownloadYtDlpClicked,
                    enabled = !isDownloadingYtDlp,
                    shape = RoundedCornerShape(10.dp)
                ) { content() }
            } else {
                Button(
                    onClick = onDownloadYtDlpClicked,
                    enabled = !isDownloadingYtDlp,
                    shape = RoundedCornerShape(10.dp)
                ) { content() }
            }
        }
        if (ytDlpStatus.isNotBlank()) {
            Text(
                ytDlpStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TorrentPage(
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

private fun folderName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

private fun limitText(bytes: Long): String =
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
private fun BrowserExtensionPage(isDesktop: Boolean) {
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
            }
        }
    }
}
