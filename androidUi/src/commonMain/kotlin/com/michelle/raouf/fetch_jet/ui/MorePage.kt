package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.more.MoreLinks
import com.michelle.raouf.fetch_jet.core.update.UpdateManager
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun MorePage(
    onOpenSettings: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenBrowser: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onToast: (String) -> Unit
) {
    val s = LocalizationManager.Strings
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val update by UpdateManager.state.collectAsState()
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SettingsGroup {
                    SettingRow(title = "FetchJet", subtitle = s.summaryVersion(update.currentVersion))
                    if (update.supported) {
                        SettingDivider()
                        SettingRow(
                            title = update.available?.let { s.versionIsAvailable(it.version) } ?: s.youHaveLatest
                        ) {
                            if (update.available != null) {
                                Button(
                                    onClick = { UpdateManager.showDialog() },
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text(s.updateSeeWhatsNew) }
                            } else {
                                OutlinedButton(
                                    onClick = { UpdateManager.checkNow() },
                                    enabled = !update.checking,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (update.checking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (update.checking) s.updateChecking else s.updateCheckNow)
                                }
                            }
                        }
                        update.message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (update.messageIsInstallError || update.messageIsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }
                }

                SettingsGroup {
                    SettingRow(
                        title = s.homeSettings,
                        subtitle = s.moreSettingsHint,
                        onClick = onOpenSettings
                    ) { MoreChevron() }
                }

                SettingsGroup {
                    SettingRow(
                        title = s.browserTitle,
                        subtitle = s.browserHint,
                        onClick = onOpenBrowser
                    ) { MoreChevron() }
                    SettingDivider()
                    SettingRow(
                        title = s.hiddenTitle,
                        onClick = onOpenHidden
                    ) { MoreChevron() }
                }

                SettingsGroup {
                    SettingRow(
                        title = s.backupExport,
                        subtitle = s.backupExportHint,
                        onClick = onBackup
                    ) { MoreChevron() }
                    SettingDivider()
                    SettingRow(
                        title = s.backupRestore,
                        subtitle = s.backupRestoreHint,
                        onClick = onRestore
                    ) { MoreChevron() }
                }

                SettingsGroup {
                    SettingRow(
                        title = s.moreEverywhereTitle,
                        onClick = { uri.openUri(MoreLinks.SITE) }
                    ) { MoreOpenArrow() }
                    SettingDivider()
                    SettingRow(
                        title = s.moreShare,
                        onClick = {
                            MoreLinks.share { text ->
                                clipboard.setText(AnnotatedString(text))
                                onToast(s.copied)
                            }
                        }
                    ) { MoreShareIcon(MaterialTheme.colorScheme.primary) }
                }

                SettingsGroup(title = s.moreCommunityTitle) {
                    MoreLinks.community.forEachIndexed { index, link ->
                        if (index > 0) SettingDivider()
                        SettingRow(
                            title = MoreLinks.communityTitle(link.key),
                            onClick = { uri.openUri(link.url) }
                        ) { MoreOpenArrow() }
                    }
                }
            }
        }
        AppScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun MoreChevron() {
    Text(
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MoreOpenArrow() {
    Text(
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↖" else "↗",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MoreShareIcon(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
        val r = 2.4.dp.toPx()
        val a = androidx.compose.ui.geometry.Offset(size.width - r, r)
        val b = androidx.compose.ui.geometry.Offset(r, size.height / 2f)
        val c = androidx.compose.ui.geometry.Offset(size.width - r, size.height - r)
        val w = 1.4.dp.toPx()
        drawLine(color, a, b, w)
        drawLine(color, b, c, w)
        drawCircle(color, r, a)
        drawCircle(color, r, b)
        drawCircle(color, r, c)
    }
}
