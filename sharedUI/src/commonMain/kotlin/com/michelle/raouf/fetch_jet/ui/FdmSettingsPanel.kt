package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.getPlatform
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.tbib.kmp_localization.LocalizationManager

/** The pages of Settings, in the order of the side list. */
internal enum class SettingsSection(val glyph: String) {
    GENERAL("◐"),
    DOWNLOADS("↓"),
    BROWSER("⧉"),
    NETWORK("◎"),
    MEDIA("▶"),
    TORRENT("⇅"),
    UPDATES("↻");

    val title: String
        get() = when (this) {
            GENERAL -> LocalizationManager.Strings.sectionGeneral
            DOWNLOADS -> LocalizationManager.Strings.sectionDownloads
            BROWSER -> LocalizationManager.Strings.sectionBrowser
            NETWORK -> LocalizationManager.Strings.sectionNetwork
            MEDIA -> LocalizationManager.Strings.sectionMedia
            TORRENT -> LocalizationManager.Strings.sectionTorrent
            UPDATES -> LocalizationManager.Strings.sectionUpdates
        }

    val blurb: String
        get() = when (this) {
            GENERAL -> LocalizationManager.Strings.sectionGeneralBlurb
            DOWNLOADS -> LocalizationManager.Strings.sectionDownloadsBlurb
            BROWSER -> LocalizationManager.Strings.sectionBrowserBlurb
            NETWORK -> LocalizationManager.Strings.sectionNetworkBlurb
            MEDIA -> LocalizationManager.Strings.sectionMediaBlurb
            TORRENT -> LocalizationManager.Strings.sectionTorrentBlurb
            UPDATES -> LocalizationManager.Strings.sectionUpdatesBlurb
        }
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
    speedPresetLowBytes: Long = 256L * 1024,
    speedPresetLowUnit: String = "KB/s",
    speedPresetMediumBytes: Long = 5L * 1024 * 1024,
    speedPresetMediumUnit: String = "MB/s",
    speedPresetHighBytes: Long = 20L * 1024 * 1024,
    speedPresetHighUnit: String = "MB/s",
    ytDlpStatus: String,
    isYtDlpInstalled: Boolean,
    isFfmpegInstalled: Boolean,
    jsRuntimeName: String = "",
    isJsRuntimeReady: Boolean = true,
    toolsDirectoryPath: String,
    torrentEngineStatus: String = "",
    isTorrentEngineReady: Boolean = true,
    isDownloadingYtDlp: Boolean,
    toolDownloadName: String? = null,
    toolDownloadFraction: Float? = null,
    monitorClipboard: Boolean = false,
    clipboardVideoPages: Boolean = false,
    sortIntoFolders: Boolean = false,
    defaultConnections: Int = 0,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit,
    onMonitorClipboardChanged: (Boolean) -> Unit = {},
    onClipboardVideoPagesChanged: (Boolean) -> Unit = {},
    onSortIntoFoldersChanged: (Boolean) -> Unit = {},
    onDefaultConnectionsChanged: (Int) -> Unit = {},
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    onThemeToggled: () -> Unit,
    onLanguageChanged: (String) -> Unit = {},
    onGlobalDownloadSpeedLimitChanged: (Long) -> Unit,
    onGlobalUploadSpeedLimitChanged: (Long) -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit = {},
    onSpeedPresetChanged: (com.michelle.raouf.fetch_jet.presentation.dashboard.SpeedPreset, Long, String) -> Unit = { _, _, _ -> },
    onDownloadYtDlpClicked: () -> Unit,
    onApplySettings: () -> Unit
) {
    val isJvm = getPlatform().isJvm
    val isIos = getPlatform().name.startsWith("iOS", ignoreCase = true)
    val updateState by UpdateManager.state.collectAsState()
    val maxAtOnce by QueueManagerRepository.getInstance().maxConcurrentDownloads.collectAsState()
    val netProxy by NetworkSettings.proxy.collectAsState()
    val netLogins by NetworkSettings.logins.collectAsState()
    val sections = SettingsSection.entries.filter {
        when (it) {
            SettingsSection.MEDIA -> isJvm || (isIos && AppFeatures.mediaExtractor)
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
        SettingsSection.GENERAL -> (if (isDarkMode) LocalizationManager.Strings.settingsThemeDark else LocalizationManager.Strings.settingsThemeLight) + " · " + unitKey to null
        SettingsSection.DOWNLOADS -> folderName(destinationPath) + " · " +
                (if (maxAtOnce <= 0) LocalizationManager.Strings.summaryNoQueueLimit else LocalizationManager.Strings.summaryAtATime(
                    maxAtOnce
                )) to null

        SettingsSection.NETWORK -> networkSummary(netProxy, netLogins.size) to
                (if (netProxy.isOn) StatusCompleted else null)

        SettingsSection.MEDIA -> if (toolsReady) LocalizationManager.Strings.settingsReady to StatusCompleted else LocalizationManager.Strings.summarySetupNeeded to StatusPaused
        SettingsSection.TORRENT -> (if (disableTorrentUpload) LocalizationManager.Strings.summaryUploadingOff else LocalizationManager.Strings.summaryUploadingOn) to null
        SettingsSection.BROWSER -> (if (isJvm) "Edge · Firefox · Chrome" else "Firefox for Android") to null
        SettingsSection.UPDATES -> updateState.available?.let {
            LocalizationManager.Strings.summaryVersionAvailable(
                it.version
            ) to StatusCompleted
        }
            ?: (LocalizationManager.Strings.summaryVersion(updateState.currentVersion) to null)
    }

    var openSection by remember { mutableStateOf<SettingsSection?>(null) }

    @Composable
    fun page(s: SettingsSection) {
        when (s) {
            SettingsSection.GENERAL -> GeneralPage(
                isDarkMode,
                unitKey,
                monitorClipboard,
                clipboardVideoPages,
                onThemeToggled,
                onLanguageChanged,
                onSpeedDisplayUnitChanged,
                onMonitorClipboardChanged,
                onClipboardVideoPagesChanged
            )

            SettingsSection.DOWNLOADS -> DownloadsPage(
                destinationPath,
                sortIntoFolders,
                defaultConnections,
                pathResolver,
                onDestinationPathChanged,
                onSortIntoFoldersChanged,
                onDefaultConnectionsChanged,
                speedPresetLowBytes, speedPresetLowUnit,
                speedPresetMediumBytes, speedPresetMediumUnit,
                speedPresetHighBytes, speedPresetHighUnit,
                onSpeedPresetChanged
            )

            SettingsSection.NETWORK -> NetworkPage(isIos)

            SettingsSection.MEDIA -> MediaToolsPage(
                ytDlpStatus, isYtDlpInstalled, isFfmpegInstalled, jsRuntimeName, isJsRuntimeReady,
                if (isIos) "" else toolsDirectoryPath, isDownloadingYtDlp, toolDownloadName, toolDownloadFraction,
                pathResolver, onDownloadYtDlpClicked
            )

            SettingsSection.TORRENT -> TorrentPage(
                disableTorrentUpload, onDisableTorrentUploadToggled,
                globalUploadSpeedLimitBytes, onGlobalUploadSpeedLimitChanged,
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
                        LocalizationManager.Strings.changesSavedAutomatically,
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
                        LocalizationManager.Strings.changesSavedAutomatically,
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
                            LocalizationManager.Strings.settingsAllSettings,
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
internal fun PageHeader(s: SettingsSection) {
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
internal fun SectionNavItem(
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
