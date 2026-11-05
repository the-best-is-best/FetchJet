package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.ProxyKind
import com.michelle.raouf.fetch_jet.core.network.ProxySettings
import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.tbib.kmp_localization.LocalizationManager

internal fun networkSummary(p: ProxySettings, n: Int): String {
    val proxy =
        if (p.isOn) "${p.kind.label()} ${p.host}:${p.port}" else LocalizationManager.Strings.netProxyOff
    return if (n == 0) proxy else proxy + " · " + LocalizationManager.Strings.netLoginsCount(n)
}

private fun ProxyKind.label(): String = when (this) {
    ProxyKind.NONE -> LocalizationManager.Strings.netProxyOff
    ProxyKind.HTTP -> "HTTP"
    ProxyKind.SOCKS5 -> "SOCKS5"
}

/** Settings > Network: proxy and saved site logins (HTTP basic auth). */
@Composable
internal fun NetworkPage(isIos: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ProxyCard(isIos)
        SiteLoginsCard()
    }
}

@Composable
private fun ProxyCard(isIos: Boolean) {
    val saved by NetworkSettings.proxy.collectAsState()
    var kind by remember(saved) { mutableStateOf(saved.kind) }
    var host by remember(saved) { mutableStateOf(saved.host) }
    var port by remember(saved) { mutableStateOf(if (saved.port > 0) saved.port.toString() else "") }
    var user by remember(saved) { mutableStateOf(saved.user) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    var justSaved by remember { mutableStateOf(false) }

    val draft =
        ProxySettings(kind, host.trim(), port.trim().toIntOrNull() ?: 0, user.trim(), password)
    val invalid = kind != ProxyKind.NONE && !draft.isOn
    val changed = draft != saved

    SettingsGroup(
        title = LocalizationManager.Strings.netProxyGroup,
        footer = if (isIos) LocalizationManager.Strings.netProxyHintIos else LocalizationManager.Strings.netProxyHint
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SegmentedPicker(
                options = ProxyKind.entries,
                selected = kind,
                label = { it.label() },
                onSelect = { kind = it; justSaved = false }
            )
        }
        if (kind != ProxyKind.NONE) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FetchJetTextField(
                        value = host,
                        onValueChange = { host = it; justSaved = false },
                        label = LocalizationManager.Strings.netProxyHost,
                        placeholder = "127.0.0.1",
                        modifier = Modifier.weight(1f)
                    )
                    FetchJetTextField(
                        value = port,
                        onValueChange = { v ->
                            port = v.filter { it.isDigit() }.take(5); justSaved = false
                        },
                        label = LocalizationManager.Strings.netProxyPort,
                        placeholder = if (kind == ProxyKind.SOCKS5) "1080" else "8080",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FetchJetTextField(
                        value = user,
                        onValueChange = { user = it; justSaved = false },
                        label = LocalizationManager.Strings.netProxyUser,
                        modifier = Modifier.weight(1f)
                    )
                    FetchJetTextField(
                        value = password,
                        onValueChange = { password = it; justSaved = false },
                        label = LocalizationManager.Strings.netProxyPassword,
                        enabled = user.isNotBlank(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (invalid && (host.isNotBlank() || port.isNotBlank())) {
                    Text(
                        LocalizationManager.Strings.netProxyBadPort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (justSaved && !changed) {
                StatusLabel(LocalizationManager.Strings.netSaved, StatusCompleted)
            }
            Box(modifier = Modifier.width(12.dp))
            Button(
                enabled = changed && !invalid,
                onClick = {
                    NetworkSettings.setProxy(
                        if (kind == ProxyKind.NONE) saved.copy(kind = ProxyKind.NONE)
                        else draft.copy(password = if (draft.user.isEmpty()) "" else draft.password)
                    )
                    justSaved = true
                }
            ) { Text(LocalizationManager.Strings.netSave) }
        }
    }
}

@Composable
private fun SiteLoginsCard() {
    val logins by NetworkSettings.logins.collectAsState()
    var site by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canAdd = SiteLogin.cleanSite(site).contains('.') && user.isNotBlank()

    SettingsGroup(
        title = LocalizationManager.Strings.netLoginsGroup,
        footer = LocalizationManager.Strings.netLoginsHint
    ) {
        if (logins.isEmpty()) {
            SettingRow(title = LocalizationManager.Strings.netLoginsEmpty)
        }
        logins.forEach { login ->
            SettingRow(title = login.site, subtitle = login.user) {
                TextButton(onClick = { NetworkSettings.removeLogin(login.site) }) {
                    Text(
                        LocalizationManager.Strings.netRemove,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            SettingDivider()
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FetchJetTextField(
                value = site,
                onValueChange = { site = it },
                label = LocalizationManager.Strings.netLoginSite,
                placeholder = "example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FetchJetTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = LocalizationManager.Strings.netUser,
                    modifier = Modifier.weight(1f)
                )
                FetchJetTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = LocalizationManager.Strings.netProxyPassword,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = canAdd,
                    onClick = {
                        NetworkSettings.saveLogin(SiteLogin(site, user, password))
                        site = ""; user = ""; password = ""
                    }
                ) { Text(LocalizationManager.Strings.netLoginAdd) }
            }
        }
    }
}
