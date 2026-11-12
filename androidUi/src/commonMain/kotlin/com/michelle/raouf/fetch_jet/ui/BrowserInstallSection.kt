package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.browser.AddExtensionResult
import com.michelle.raouf.fetch_jet.core.browser.BrowserInfo
import com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
import com.michelle.raouf.fetch_jet.core.browser.EDGE_EXTENSION_ID
import com.michelle.raouf.fetch_jet.core.browser.ExtensionStore
import com.michelle.raouf.fetch_jet.core.browser.FIREFOX_ADDON_URL
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.update.EXTENSION_VERSION
import com.michelle.raouf.fetch_jet.core.update.isOlderExtension
import com.tbib.kmp_localization.LocalizationManager

private const val EDGE_STORE_PAGE =
    "https://microsoftedge.microsoft.com/addons/detail/$EDGE_EXTENSION_ID"

/** The browser id the bridge sees in a browser's User-Agent ([BridgePairing.noteBrowser]). */
private fun seenId(browserId: String): String? = when {
    browserId == "brave" -> null
    browserId.startsWith("org.mozilla.") -> "firefox"
    else -> browserId
}

/**
 * One row per browser: installed or not, extension connected or not, and the button that adds
 * the extension. A browser that is not installed shows a warning with a link to download it.
 */
@Composable
internal fun BrowserInstallRows() {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val browsers = remember { BrowserIntegration.browsers() }
    val seen by BridgePairing.seenBrowsers.collectAsState()
    val versions by BridgePairing.seenVersions.collectAsState()
    var missing by remember { mutableStateOf<BrowserInfo?>(null) }
    var steps by remember { mutableStateOf<Pair<BrowserInfo, AddExtensionResult.LoadUnpacked>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var fromFile by remember { mutableStateOf<Pair<BrowserInfo, String>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun handle(b: BrowserInfo, r: AddExtensionResult) {
        when (r) {
            AddExtensionResult.NotInstalled -> missing = b
            is AddExtensionResult.LoadUnpacked -> {
                clipboard.setText(AnnotatedString(r.folder))
                steps = b to r
            }
            is AddExtensionResult.InstallFromFile -> fromFile = b to r.fileName
            is AddExtensionResult.Failed -> failure = r.reason
            AddExtensionResult.Opened -> Unit
        }
    }

    browsers.forEachIndexed { i, b ->
        if (i > 0) SettingDivider()
        val key = seenId(b.id)
        val connected = key?.let { it in seen } == true
        val version = key?.let { versions[it] }
        val outdated = connected && (version == null || isOlderExtension(version))
        val firefox = b.id == "firefox" || b.id.startsWith("org.mozilla.")
        // Where the version the app needs is: the store when it already has it (the browser
        // updates by itself), else the copy inside the app (Chromium on desktop), else the
        // signed build attached to the GitHub release (Firefox), else it waits for the store.
        var storeVersion by remember(b.id) { mutableStateOf<String?>(null) }
        var githubXpi by remember(b.id) { mutableStateOf<ExtensionStore.SignedXpi?>(null) }
        if (b.installed && (outdated || (!connected && firefox))) LaunchedEffect(b.id) {
            storeVersion = ExtensionStore.versionFor(b.id)
            if (firefox) githubXpi = ExtensionStore.githubXpi()
        }
        val storeHasIt = storeVersion?.let { !isOlderExtension(it) } == true
        val githubHasIt = firefox && githubXpi?.let { !isOlderExtension(it.version) } == true
        val fromGitHub = (outdated || (!connected && firefox && storeVersion != null)) && !storeHasIt && githubHasIt
        val waitingForStore = outdated && !storeHasIt && !b.hasLocalCopy && !githubHasIt
        val subtitle = when {
            !b.installed -> "⚠️ " + LocalizationManager.Strings.browserNotInstalled
            outdated -> "⚠️ " + (if (version == null) LocalizationManager.Strings.extensionOldVersion(EXTENSION_VERSION)
            else LocalizationManager.Strings.extensionNeedsUpdate(version, EXTENSION_VERSION)) + "\n" + when {
                storeHasIt -> LocalizationManager.Strings.storeHasUpdate(storeVersion!!)
                b.hasLocalCopy -> LocalizationManager.Strings.updateFromAppHint
                githubHasIt -> LocalizationManager.Strings.updateFromGitHubHint(githubXpi!!.version)
                else -> LocalizationManager.Strings.storeWaiting(EXTENSION_VERSION)
            }
            connected -> "✓ " + LocalizationManager.Strings.extensionConnectedVersion(version!!)
            else -> LocalizationManager.Strings.extensionNotAddedYet
        }
        SettingRow(title = b.name, subtitle = subtitle) {
            val onClick: () -> Unit = {
                val xpi = githubXpi
                when {
                    !b.installed -> missing = b
                    fromGitHub && xpi != null -> {
                        busy = b.id
                        scope.launch {
                            val bytes = ExtensionStore.download(xpi)
                            busy = null
                            if (bytes == null) failure = LocalizationManager.Strings.extensionDownloadFailed
                            else handle(b, BrowserIntegration.installXpi(b.id, bytes, xpi.fileName))
                        }
                    }
                    outdated && storeHasIt -> uri.openUri(if (b.id == "edge") EDGE_STORE_PAGE else FIREFOX_ADDON_URL)
                    else -> handle(b, BrowserIntegration.addExtension(b.id))
                }
            }
            val label = when {
                busy == b.id -> "…"
                fromGitHub -> LocalizationManager.Strings.updateFromGitHub
                outdated && storeHasIt -> LocalizationManager.Strings.openStore
                outdated && b.hasLocalCopy -> LocalizationManager.Strings.updateFromApp
                outdated -> LocalizationManager.Strings.openStore
                else -> LocalizationManager.Strings.addExtension
            }
            if (b.installed && (!connected || (outdated && !waitingForStore))) Button(
                onClick = onClick, enabled = busy == null, shape = RoundedCornerShape(10.dp)
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
            } else OutlinedButton(onClick = onClick, enabled = busy == null, shape = RoundedCornerShape(10.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    fromFile?.let { (b, name) ->
        AlertDialog(
            onDismissRequest = { fromFile = null },
            title = { Text(LocalizationManager.Strings.installFromFileTitle(b.name), fontWeight = FontWeight.Bold) },
            text = { Text(LocalizationManager.Strings.installFromFileSteps(b.name, name)) },
            confirmButton = {
                Button(onClick = { BrowserIntegration.openBrowser(b.id); fromFile = null }) {
                    Text(LocalizationManager.Strings.openBrowser(b.name))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fromFile = null }) { Text(LocalizationManager.Strings.close) }
            }
        )
    }

    missing?.let { b ->
        AlertDialog(
            onDismissRequest = { missing = null },
            title = { Text(LocalizationManager.Strings.browserMissingTitle(b.name), fontWeight = FontWeight.Bold) },
            text = { Text(LocalizationManager.Strings.browserMissingMessage(b.name)) },
            confirmButton = {
                Button(onClick = { uri.openUri(b.downloadUrl); missing = null }) {
                    Text(LocalizationManager.Strings.downloadBrowser(b.name))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { missing = null }) { Text(LocalizationManager.Strings.close) }
            }
        )
    }

    steps?.let { (b, r) ->
        AlertDialog(
            onDismissRequest = { steps = null },
            title = { Text(LocalizationManager.Strings.loadUnpackedTitle(b.name), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(LocalizationManager.Strings.loadUnpackedSteps(b.name, r.page))
                    SelectionContainer {
                        Text(
                            r.folder,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        )
                    }
                    Text(
                        LocalizationManager.Strings.loadUnpackedUpdates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { clipboard.setText(AnnotatedString(r.folder)) }) {
                    Text(LocalizationManager.Strings.copyPath)
                }
            },
            dismissButton = {
                TextButton(onClick = { BrowserIntegration.openFolder(r.folder) }) {
                    Text(LocalizationManager.Strings.detailsOpenFolder)
                }
                TextButton(onClick = { steps = null }) { Text(LocalizationManager.Strings.close) }
            }
        )
    }

    failure?.let { reason ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text(LocalizationManager.Strings.addExtension, fontWeight = FontWeight.Bold) },
            text = { Text(reason) },
            confirmButton = { Button(onClick = { failure = null }) { Text(LocalizationManager.Strings.close) } }
        )
    }
}
