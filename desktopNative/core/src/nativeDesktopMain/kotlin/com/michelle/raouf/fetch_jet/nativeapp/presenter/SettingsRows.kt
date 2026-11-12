package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.browser.BrowserInfo
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.ProxyKind
import com.michelle.raouf.fetch_jet.core.network.SiteLogin
import com.michelle.raouf.fetch_jet.core.power.SpeedSchedule
import com.michelle.raouf.fetch_jet.core.power.SystemPower
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.setup.DefaultTorrentHandler
import com.michelle.raouf.fetch_jet.core.setup.MainDownloaderSetup
import com.michelle.raouf.fetch_jet.core.startup.StartupLauncher
import com.michelle.raouf.fetch_jet.core.update.EXTENSION_VERSION
import com.michelle.raouf.fetch_jet.core.update.PlatformUpdater
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.core.update.isOlderExtension
import com.michelle.raouf.fetch_jet.feature.device_sync.PairedDevice
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingButtonDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingFieldDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ToneCode
import com.tbib.kmp_localization.LocalizationManager

internal val THEME_MODES = listOf("system", "light", "dark")
internal val SPEED_UNITS = listOf("MB/s", "KB/s", "Mbps", "Kbps")
internal val CONNECTION_COUNTS = listOf(0, 1, 4, 8, 16, 32)
internal val QUEUE_COUNTS = listOf(1, 2, 3, 4, 5, 8, 0)

internal fun languageCodes(): List<String> = listOf(AppLanguage.SYSTEM) + appLanguages.map { it.localeCode }

private class Rows {
    val list = ArrayList<SettingRowDto>()
    private var dividerNext = false

    fun group(title: String? = null, footer: String? = null, body: () -> Unit) {
        val start = list.size
        dividerNext = false
        body()
        dividerNext = false
        if (list.size == start) return
        list[start] = list[start].copy(first = true, caption = title?.uppercase().orEmpty())
        val end = list.size - 1
        list[end] = list[end].copy(last = true, footer = footer.orEmpty())
    }

    fun bare(row: SettingRowDto) {
        list += row.copy(first = true, last = true, bare = true)
    }

    fun divider() {
        dividerNext = true
    }

    fun add(row: SettingRowDto) {
        list += if (dividerNext) row.copy(divider = true) else row
        dividerNext = false
    }
}

private fun switchRow(id: String, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true) = SettingRowDto(
    kind = SettingKind.SWITCH.ordinal,
    id = id,
    title = title,
    subtitle = subtitle.orEmpty(),
    checked = checked,
    enabled = enabled,
    clickable = enabled,
)

private fun infoRow(
    title: String,
    subtitle: String? = null,
    id: String = "",
    clickable: Boolean = false,
    status: String = "",
    statusTone: ToneCode = ToneCode.ON_SURFACE,
    side: String = "",
    buttons: List<SettingButtonDto> = emptyList(),
    compact: Boolean = false,
    subtitleEmoji: String = "",
) = SettingRowDto(
    kind = SettingKind.INFO.ordinal,
    id = id,
    title = title,
    subtitle = subtitle.orEmpty(),
    subtitleEmoji = subtitleEmoji,
    enabled = true,
    clickable = clickable,
    status = status,
    statusTone = statusTone.ordinal,
    side = side,
    sideTone = ToneCode.PRIMARY.ordinal,
    buttons = buttons,
    compact = compact,
)

private fun pickerRow(
    id: String,
    title: String,
    subtitle: String,
    options: List<String>,
    pick: Int,
    below: Boolean,
) = SettingRowDto(
    kind = SettingKind.PICKER.ordinal,
    id = id,
    title = title,
    subtitle = subtitle,
    enabled = true,
    options = options,
    pick = pick,
    below = below,
    top = if (below && title.isEmpty()) 16 else 0,
    bottom = if (!below) 0 else if (title.isEmpty()) 16 else 14,
)

private fun noteRow(text: String, tone: ToneCode, top: Int = 0, bottom: Int = 0, large: Boolean = false) = SettingRowDto(
    kind = SettingKind.NOTE.ordinal,
    title = text,
    tone = tone.ordinal,
    top = top,
    bottom = bottom,
    large = large,
)

private fun button(
    id: String,
    label: String,
    filled: Boolean = false,
    flat: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    strong: Boolean = false,
    round: Boolean = false,
    tone: ToneCode = ToneCode.ON_SURFACE,
) = SettingButtonDto(
    id = id,
    label = label,
    filled = filled,
    flat = flat,
    enabled = enabled,
    busy = busy,
    strong = strong,
    round = round,
    tone = tone.ordinal,
)

private fun Presenter.field(
    id: String,
    value: String,
    label: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    width: Int = 0,
    emoji: String = "",
    toggle: String = "",
    slim: Boolean = false,
) = SettingFieldDto(
    id = id,
    label = label,
    placeholder = placeholder,
    value = value,
    rev = settingsEcho.of(id).rev(value),
    enabled = enabled,
    secret = secret,
    width = width,
    emoji = emoji,
    toggle = toggle,
    toggleId = if (toggle.isEmpty()) "" else "$id.pm",
    slim = slim,
)

internal fun presetUnitOf(page: SettingsPage, id: String, unit: String): String =
    page.presetUnit[id] ?: (if (unit in speedPresetUnits) unit else "MB/s")

internal fun presetTextOf(page: SettingsPage, id: String, bytes: Long, unit: String): String =
    page.presetText[id] ?: speedPresetValueText(bytes, presetUnitOf(page, id, unit))

private fun Presenter.presetRow(id: String, name: String, bytes: Long, unit: String, page: SettingsPage, top: Int = 16): SettingRowDto {
    val currentUnit = presetUnitOf(page, id, unit)
    return SettingRowDto(
        kind = SettingKind.PRESET.ordinal,
        id = id,
        top = top,
        bottom = 16,
        title = name,
        side = speedPresetShortLabel(bytes),
        sideTone = ToneCode.PRIMARY.ordinal,
        one = field(
            id = id,
            value = presetTextOf(page, id, bytes, unit),
            placeholder = LocalizationManager.Strings.speedLimitValue,
            emoji = "⚡",
            width = 130,
            slim = true,
        ),
        options = speedPresetUnits,
        pick = speedPresetUnits.indexOf(currentUnit) + 1,
    )
}

internal fun minutesToHm12(minutes: Int): String {
    val mm = ((minutes % 1440) + 1440) % 1440
    val h24 = mm / 60
    val min = mm % 60
    val h12 = when (h24 % 12) {
        0 -> 12
        else -> h24 % 12
    }
    return "$h12:${min.toString().padStart(2, '0')}"
}

internal fun parseHm12(text: String, isPm: Boolean): Int? {
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

internal fun timeTextOf(page: SettingsPage, id: String, minutes: Int): String = page.timeText[id] ?: minutesToHm12(minutes)

internal fun timePmOf(page: SettingsPage, id: String, minutes: Int): Boolean =
    page.timePm[id] ?: ((((minutes % 1440) + 1440) % 1440) >= 12 * 60)

private fun Presenter.timeField(id: String, label: String, minutes: Int, page: SettingsPage) = field(
    id = id,
    value = timeTextOf(page, id, minutes),
    label = label,
    placeholder = "8:00",
    toggle = if (timePmOf(page, id, minutes)) "PM" else "AM",
    slim = true,
)

internal fun Presenter.buildSettingsRows(section: SettingsSection, v: SettingsSlice, page: SettingsPage): List<SettingRowDto> {
    val rows = Rows()
    when (section) {
        SettingsSection.GENERAL -> generalRows(rows, v, page)
        SettingsSection.DOWNLOADS -> downloadsRows(rows, v, page)
        SettingsSection.BROWSER -> browserRows(rows, page)
        SettingsSection.DEVICES -> devicesRows(rows, v)
        SettingsSection.NETWORK -> networkRows(rows, page)
        SettingsSection.MEDIA -> mediaRows(rows, v)
        SettingsSection.TORRENT -> torrentRows(rows, v, page)
        SettingsSection.UPDATES -> updatesRows(rows, updateState())
    }
    return rows.list
}

private fun generalRows(rows: Rows, v: SettingsSlice, page: SettingsPage) {
    val s = LocalizationManager.Strings
    val choice = AppLanguage.choice
    val currentName = if (choice == AppLanguage.SYSTEM) "${s.systemLanguage} (${s.languageName})"
    else appLanguages.firstOrNull { it.localeCode == choice }?.languageName ?: choice
    val codes = languageCodes()
    rows.group(title = s.language) {
        rows.add(
            SettingRowDto(
                kind = SettingKind.MENU.ordinal,
                id = "language",
                title = s.language,
                subtitle = s.languageHint,
                enabled = true,
                clickable = true,
                side = currentName,
                options = codes.map { code ->
                    if (code == AppLanguage.SYSTEM) s.systemLanguage else appLanguages.first { it.localeCode == code }.languageName
                },
                pick = codes.indexOf(choice) + 1,
                hanOption = codes.indexOf("zh") + 1,
            )
        )
    }
    rows.group(title = s.clipboardGroup, footer = s.clipboardFooter) {
        rows.add(
            switchRow(
                "watchClipboard",
                s.watchClipboard,
                if (v.monitorClipboard) s.watchClipboardOn else s.settingOff,
                v.monitorClipboard,
            )
        )
        rows.divider()
        rows.add(
            switchRow("clipboardVideoPages", s.alsoVideoPages, s.alsoVideoPagesHint, v.clipboardVideoPages, enabled = v.monitorClipboard)
        )
    }
    rows.group(title = s.appearanceGroup) {
        rows.add(
            pickerRow(
                "theme",
                s.themeTitle,
                s.themeHint,
                THEME_MODES.map { themeModeLabel(it) },
                THEME_MODES.indexOf(v.themeMode) + 1,
                below = false,
            )
        )
        rows.divider()
        rows.add(pickerRow("speedUnit", s.speedShownIn, s.speedShownInHint, SPEED_UNITS, SPEED_UNITS.indexOf(v.unitKey) + 1, below = true))
    }
    if (MainDownloaderSetup.available) {
        rows.group(title = s.mainSetupGroup) {
            rows.add(
                infoRow(
                    title = s.defaultAppRowTitle,
                    subtitle = when (page.defaultApp) {
                        true -> s.defaultAppIsDefault
                        false -> if (DefaultTorrentHandler.opensSystemPage) s.defaultAppNotDefault + "\n" + s.defaultAppSystemPageHint
                        else s.defaultAppNotDefault
                        null -> null
                    },
                    status = if (page.defaultApp == true) s.settingsReady else "",
                    statusTone = ToneCode.COMPLETED,
                    buttons = if (page.defaultApp == true) emptyList()
                    else listOf(button("defaultApp", s.defaultAppMakeDefault, strong = true, round = true, busy = page.defaultAppBusy, enabled = !page.defaultAppBusy)),
                )
            )
            rows.divider()
            val connected = BridgePairing.seenBrowsers.value.isNotEmpty()
            rows.add(
                infoRow(
                    title = s.mainSetupExtensionRow,
                    subtitle = if (connected) s.mainSetupExtensionConnected else s.mainSetupExtensionMissing,
                    id = "openBrowserPage",
                    clickable = true,
                    status = if (connected) s.settingsReady else "",
                    statusTone = ToneCode.COMPLETED,
                    buttons = if (connected) emptyList() else listOf(button("openBrowserPage", s.open, round = true)),
                )
            )
        }
    }
    if (StartupLauncher.managedBySystemUri != null) {
        rows.group(title = s.startupGroup, footer = s.startupStoreFooter) {
            rows.add(
                infoRow(
                    title = s.startupStartAtLogin,
                    subtitle = s.startupManagedByWindows,
                    id = "openStartupSettings",
                    clickable = true,
                    buttons = listOf(button("openStartupSettings", s.startupOpenWindowsSettings, strong = true, round = true)),
                )
            )
            rows.divider()
            rows.add(switchRow("startHidden", s.startupStartHidden, s.startupStartHiddenHint, page.startHidden))
        }
    } else if (StartupLauncher.isSupported) {
        rows.group(title = s.startupGroup) {
            rows.add(
                switchRow("startAtLogin", s.startupStartAtLogin, s.startupStartAtLoginHint, page.startupEnabled, enabled = !page.startupBusy)
            )
            rows.divider()
            rows.add(
                switchRow(
                    "startHidden",
                    s.startupStartHidden,
                    s.startupStartHiddenHint,
                    page.startHidden,
                    enabled = page.startupEnabled && !page.startupBusy,
                )
            )
            page.startupError?.let { rows.add(noteRow(it, ToneCode.ERROR, bottom = 12)) }
        }
    }
    if (SystemPower.isSupported) {
        rows.group(title = s.whenDoneGroup, footer = s.whenDoneHint) {
            rows.add(
                pickerRow(
                    "whenDone",
                    "",
                    "",
                    WhenDoneAction.entries.map { whenDoneLabel(it) },
                    WhenDone.action.value.ordinal + 1,
                    below = true,
                )
            )
        }
    }
}

private fun Presenter.downloadsRows(rows: Rows, v: SettingsSlice, page: SettingsPage) {
    val s = LocalizationManager.Strings
    val defaultDownloads = pathResolver.getDownloadsDirectory()
    rows.group(title = s.saveLocationGroup, footer = s.saveLocationFooter) {
        rows.add(
            SettingRowDto(
                kind = SettingKind.FIELDS.ordinal,
                top = 16,
                one = field("destination", v.destinationPath, placeholder = s.settingsFolderPath, emoji = "📁"),
            )
        )
        rows.add(
            SettingRowDto(
                kind = SettingKind.BUTTONS.ordinal,
                top = 10,
                bottom = 16,
                buttons = buildList {
                    add(button("chooseFolder", s.settingsChooseFolder, filled = true, strong = true))
                    if (pathResolver.isOpenFolderSupported()) add(button("openFolder", s.open))
                    if (v.destinationPath != defaultDownloads) add(button("useDownloads", s.settingsUseDownloads))
                },
            )
        )
    }
    rows.group(title = s.settingsConnectionsGroup, footer = s.connectionsFooter) {
        rows.add(
            SettingRowDto(
                kind = SettingKind.CHIPS.ordinal,
                id = "connections",
                title = s.settingsConnectionsPerDownload,
                options = CONNECTION_COUNTS.map { if (it == 0) s.settingsConnectionsAuto else it.toString() },
                pick = CONNECTION_COUNTS.indexOf(v.defaultConnections) + 1,
            )
        )
    }
    rows.group(title = s.speedPresetsTitle, footer = s.speedPresetsFooter) {
        rows.add(
            SettingRowDto(
                kind = SettingKind.PRESET.ordinal,
                top = 16,
                bottom = 16,
                title = s.speedPresetHigh,
                side = s.speedLimitUnlimited,
                sideTone = ToneCode.MUTED.ordinal,
            )
        )
        rows.divider()
        rows.add(presetRow("preset.fast", s.speedPresetFast, v.fastBytes, v.fastUnit, page))
        rows.divider()
        rows.add(presetRow("preset.medium", s.speedPresetMedium, v.mediumBytes, v.mediumUnit, page))
        rows.divider()
        rows.add(presetRow("preset.low", s.speedPresetLow, v.lowBytes, v.lowUnit, page))
    }
    if (com.michelle.raouf.fetch_jet.core.power.UserIdle.idleMs() != null) {
        rows.group(footer = s.speedLimitWhileInUseFooter) {
            rows.add(
                switchRow(
                    "speedLimitOnlyInUse",
                    s.speedLimitWhileInUseTitle,
                    null,
                    BehaviorSettings.speedLimitOnlyInUse.value,
                )
            )
        }
    }
    val scheduleOn = SpeedSchedule.enabled.value
    val windows = SpeedSchedule.windows.value
    rows.group(footer = s.speedScheduleFooter) {
        rows.add(
            switchRow(
                "schedule",
                s.speedScheduleTitle,
                if (scheduleOn && SpeedSchedule.activeCap.value != null) s.speedScheduleActive else null,
                scheduleOn,
            )
        )
        if (scheduleOn) {
            if (windows.isEmpty()) {
                rows.divider()
                rows.add(noteRow(s.speedScheduleEmpty, ToneCode.MUTED, top = 16, bottom = 16, large = true))
            }
            windows.forEachIndexed { index, window ->
                rows.divider()
                rows.add(
                    SettingRowDto(
                        kind = SettingKind.FIELDS.ordinal,
                        top = 16,
                        one = timeField("window.$index.from", s.speedScheduleFrom, window.startMinutes, page),
                        two = timeField("window.$index.to", s.speedScheduleTo, window.endMinutes, page),
                        removeId = "window.$index.remove",
                    )
                )
                rows.add(presetRow("window.$index.cap", "", window.capBytes, window.capUnit, page, top = 10))
            }
            rows.divider()
            rows.add(
                SettingRowDto(
                    kind = SettingKind.BUTTONS.ordinal,
                    top = 16,
                    bottom = 16,
                    buttons = listOf(button("addWindow", "+  " + s.speedScheduleAdd, filled = true, strong = true)),
                )
            )
        }
    }
    rows.group(title = s.organizeGroup, footer = s.organizeFooter) {
        rows.add(
            switchRow(
                "sortIntoFolders",
                s.sortIntoFolders,
                if (v.sortIntoFolders) s.sortIntoFoldersOn else s.sortIntoFoldersOff,
                v.sortIntoFolders,
            )
        )
    }
    val max = QueueManagerRepository.getInstance().maxConcurrentDownloads.value
    rows.group(title = s.homeQueueTitle, footer = s.homeQueueFooter) {
        rows.add(
            pickerRow(
                "queue",
                s.homeDownloadsAtSameTime,
                if (max <= 0) s.homeNoLimit else s.homeAtATime(max),
                QUEUE_COUNTS.map { if (it == 0) s.homeNoLimit else it.toString() },
                QUEUE_COUNTS.indexOf(max) + 1,
                below = true,
            )
        )
        if (com.michelle.raouf.fetch_jet.core.power.WakeForSchedule.isSupported) {
            rows.divider()
            rows.add(
                switchRow(
                    "wakeForSchedule",
                    s.wakeForScheduleTitle,
                    s.wakeForScheduleHint,
                    BehaviorSettings.wakeForSchedule.value,
                )
            )
        }
    }
}

internal fun currentProxyDraft(page: SettingsPage): ProxyDraft {
    val saved = NetworkSettings.proxy.value
    return page.proxy?.takeIf { it.base == saved } ?: ProxyDraft.from(saved)
}

private fun Presenter.networkRows(rows: Rows, page: SettingsPage) {
    val s = LocalizationManager.Strings
    val saved = NetworkSettings.proxy.value
    val draft = currentProxyDraft(page)
    val wanted = draft.settings()
    val invalid = draft.kind != ProxyKind.NONE && !wanted.isOn
    val changed = wanted != saved
    rows.group(title = s.netProxyGroup, footer = if (draft.kind == ProxyKind.HTTP) s.netProxyHint + " " + s.torrentProxyHint else s.netProxyHint) {
        rows.add(pickerRow("proxyKind", "", "", ProxyKind.entries.map { proxyKindLabel(it) }, draft.kind.ordinal + 1, below = true))
        if (draft.kind != ProxyKind.NONE) {
            val showError = invalid && (draft.host.isNotBlank() || draft.port.isNotBlank())
            rows.add(
                SettingRowDto(
                    kind = SettingKind.FIELDS.ordinal,
                    one = field("proxyHost", draft.host, label = s.netProxyHost, placeholder = "127.0.0.1"),
                    two = field(
                        "proxyPort",
                        draft.port,
                        label = s.netProxyPort,
                        placeholder = if (draft.kind == ProxyKind.SOCKS5) "1080" else "8080",
                        width = 110,
                    ),
                )
            )
            rows.add(
                SettingRowDto(
                    kind = SettingKind.FIELDS.ordinal,
                    top = 10,
                    bottom = if (showError) 0 else 16,
                    one = field("proxyUser", draft.user, label = s.netProxyUser),
                    two = field(
                        "proxyPassword",
                        draft.password,
                        label = s.netProxyPassword,
                        enabled = draft.user.isNotBlank(),
                        secret = true,
                    ),
                )
            )
            if (showError) rows.add(noteRow(s.netProxyBadPort, ToneCode.ERROR, top = 10, bottom = 16))
        }
        rows.add(
            SettingRowDto(
                kind = SettingKind.BUTTONS.ordinal,
                bottom = 12,
                alignEnd = true,
                status = if (page.proxyJustSaved && !changed) s.netSaved else "",
                statusTone = ToneCode.COMPLETED.ordinal,
                buttons = listOf(button("proxySave", s.netSave, filled = true, enabled = changed && !invalid, round = true)),
            )
        )
    }
    val logins = NetworkSettings.logins.value
    val canAdd = SiteLogin.cleanSite(page.loginSite).contains('.') && page.loginUser.isNotBlank()
    rows.group(title = s.netLoginsGroup, footer = s.netLoginsHint) {
        if (logins.isEmpty()) rows.add(infoRow(title = s.netLoginsEmpty))
        logins.forEach { login ->
            rows.add(
                infoRow(
                    title = login.site,
                    subtitle = login.user,
                    buttons = listOf(button("login.remove." + login.site, s.netRemove, flat = true, tone = ToneCode.ERROR)),
                )
            )
            rows.divider()
        }
        rows.add(
            SettingRowDto(
                kind = SettingKind.FIELDS.ordinal,
                top = 16,
                one = field("loginSite", page.loginSite, label = s.netLoginSite, placeholder = "example.com"),
            )
        )
        rows.add(
            SettingRowDto(
                kind = SettingKind.FIELDS.ordinal,
                top = 10,
                one = field("loginUser", page.loginUser, label = s.netUser),
                two = field("loginPassword", page.loginPassword, label = s.netProxyPassword, secret = true),
            )
        )
        rows.add(
            SettingRowDto(
                kind = SettingKind.BUTTONS.ordinal,
                top = 10,
                bottom = 16,
                alignEnd = true,
                buttons = listOf(button("loginAdd", s.netLoginAdd, filled = true, enabled = canAdd, round = true)),
            )
        )
    }
}

private fun mediaRows(rows: Rows, v: SettingsSlice) {
    val s = LocalizationManager.Strings
    val ready = v.toolsReady
    rows.group(title = s.mediaToolsGroup) {
        rows.add(
            infoRow(
                title = "yt-dlp",
                subtitle = s.ytDlpHint,
                status = if (v.isYtDlpInstalled) s.settingsReady else s.settingsMissing,
                statusTone = if (v.isYtDlpInstalled) ToneCode.COMPLETED else ToneCode.ERROR,
            )
        )
        rows.divider()
        rows.add(
            infoRow(
                title = "FFmpeg",
                subtitle = s.ffmpegHint,
                status = if (v.isFfmpegInstalled) s.settingsReady else s.ffmpegMissing,
                statusTone = if (v.isFfmpegInstalled) ToneCode.COMPLETED else ToneCode.PAUSED,
            )
        )
        if (v.jsRuntimeName.isNotBlank()) {
            rows.divider()
            rows.add(
                infoRow(
                    title = s.jsRuntimeTitle(v.jsRuntimeName),
                    subtitle = s.jsRuntimeHint,
                    status = if (v.isJsRuntimeReady) s.settingsReady
                    else s.missingPress(if (ready) s.mediaUpdateTools else s.mediaDownloadTheTools),
                    statusTone = if (v.isJsRuntimeReady) ToneCode.COMPLETED else ToneCode.PAUSED,
                )
            )
        }
        if (v.toolsDirectoryPath.isNotBlank()) {
            rows.divider()
            rows.add(
                infoRow(
                    title = s.mediaToolsFolder,
                    subtitle = v.toolsDirectoryPath,
                    id = "toolsFolder",
                    clickable = true,
                    side = s.open,
                )
            )
        }
    }
    val name = v.toolDownloadName
    val fraction = v.toolDownloadFraction
    rows.bare(
        SettingRowDto(
            kind = SettingKind.BUTTONS.ordinal,
            buttons = listOf(
                button(
                    id = "tools",
                    label = when {
                        !v.isDownloadingYtDlp -> if (ready) s.mediaUpdateTools else s.mediaDownloadTheTools
                        name == null -> if (ready) s.mediaUpdatingTools else s.mediaDownloadingTools
                        fraction == null -> s.mediaDownloadingTool(name)
                        else -> s.mediaDownloadingToolPercent(name, (fraction * 100).toInt())
                    },
                    filled = !ready,
                    enabled = !v.isDownloadingYtDlp,
                    busy = v.isDownloadingYtDlp,
                    strong = !v.isDownloadingYtDlp,
                )
            ),
        )
    )
    if (v.isDownloadingYtDlp && name != null) {
        rows.bare(
            SettingRowDto(
                kind = SettingKind.PROGRESS.ordinal,
                progress = fraction ?: 0f,
                indeterminate = fraction == null,
            )
        )
    }
    if (v.ytDlpStatus.isNotBlank()) rows.bare(noteRow(localizeStep(v.ytDlpStatus), ToneCode.MUTED))
}

private fun updatesRows(rows: Rows, update: UpdateState) {
    val s = LocalizationManager.Strings
    val available = update.available
    rows.group(title = s.sectionUpdates) {
        rows.add(
            infoRow(
                title = "FetchJet ${update.currentVersion}",
                subtitle = available?.let { s.versionIsAvailable(it.version) } ?: s.youHaveLatest,
                buttons = listOf(
                    if (available != null) button("updateSee", s.updateSeeWhatsNew, filled = true)
                    else button(
                        "updateCheck",
                        if (update.checking) s.updateChecking else s.updateCheckNow,
                        enabled = !update.checking,
                        busy = update.checking,
                    )
                ),
            )
        )
        update.message?.let {
            rows.add(
                noteRow(
                    it,
                    if (update.messageIsInstallError || update.messageIsError) ToneCode.ERROR else ToneCode.MUTED,
                    bottom = 12,
                )
            )
        }
        rows.divider()
        rows.add(switchRow("autoCheck", s.checkUpdatesAutomatically, s.checkUpdatesAutomaticallyHint, update.autoCheck))
        rows.divider()
        if (update.canInstallInApp) {
            rows.add(
                switchRow(
                    "autoInstall",
                    s.downloadUpdatesAutomatically,
                    s.downloadUpdatesAutomaticallyHint,
                    update.autoInstall,
                    enabled = update.autoCheck,
                )
            )
        } else {
            rows.add(
                infoRow(
                    title = s.installingUpdates,
                    subtitle = if (PlatformUpdater.updatesFromStore) s.installingUpdatesStore else s.installingUpdatesManual,
                )
            )
        }
        rows.divider()
        rows.add(
            infoRow(
                title = s.updateReleaseNotes,
                subtitle = s.updateReleaseNotesHint,
                id = "releaseNotes",
                clickable = true,
                side = "↗",
            )
        )
    }
}

internal val UPLOAD_PRESETS = listOf(0L, 512L * 1024L, 1024L * 1024L, 5L * 1024L * 1024L, 10L * 1024L * 1024L)

internal fun limitBytes(value: Double, unit: String): Long = when (unit) {
    "KB/s" -> (value * 1024.0).toLong()
    "MB/s" -> (value * 1024.0 * 1024.0).toLong()
    "Kbps" -> (value * 1000.0 / 8.0).toLong()
    "Mbps" -> (value * 1000.0 * 1000.0 / 8.0).toLong()
    else -> (value * 1024.0 * 1024.0).toLong()
}

private fun megabytesText(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb == mb.toLong().toDouble()) mb.toLong().toString() else ((mb * 10).toLong() / 10.0).toString()
}

internal fun limitUnitOf(page: SettingsPage, speed: Long): String =
    page.limitUnit ?: if (speed <= 0L || speed >= 1024L * 1024L) "MB/s" else "KB/s"

internal fun limitTextOf(page: SettingsPage, speed: Long): String = page.limitText ?: when {
    speed <= 0L -> ""
    speed >= 1024L * 1024L -> megabytesText(speed)
    else -> (speed / 1024L).toString()
}

internal fun limitIsCustom(page: SettingsPage, speed: Long): Boolean =
    page.limitCustomAt == speed || (speed > 0L && speed !in UPLOAD_PRESETS)

internal val STOP_RATIOS = listOf(0f, 0.5f, 1f, 1.5f, 2f, 3f, 5f)

private fun Presenter.torrentRows(rows: Rows, v: SettingsSlice, page: SettingsPage) {
    val s = LocalizationManager.Strings
    val speed = v.uploadLimit
    val custom = limitIsCustom(page, speed)
    val unit = limitUnitOf(page, speed)
    val text = limitTextOf(page, speed)
    rows.group {
        rows.add(
            SettingRowDto(
                kind = SettingKind.PRESET.ordinal,
                top = 16,
                title = s.speedLimitUploadTorrents,
                side = when {
                    speed <= 0L -> s.speedLimitNoLimit
                    speed >= 1024L * 1024L -> megabytesText(speed) + " MB/s"
                    else -> "${speed / 1024L} KB/s"
                },
                sideTone = if (speed <= 0L) ToneCode.MUTED.ordinal else ToneCode.PRIMARY.ordinal,
            )
        )
        rows.add(
            SettingRowDto(
                kind = SettingKind.CHIPS.ordinal,
                id = "uploadLimit",
                compact = true,
                top = 8,
                bottom = if (custom) 0 else 16,
                options = listOf(s.speedLimitUnlimited, "512 KB/s", "1 MB/s", "5 MB/s", "10 MB/s", s.speedLimitCustom),
                pick = if (custom) 6 else UPLOAD_PRESETS.indexOf(speed) + 1,
            )
        )
        if (custom) {
            val number = text.toDoubleOrNull() ?: 0.0
            val total = limitBytes(number, unit)
            rows.add(
                SettingRowDto(
                    kind = SettingKind.LIMIT_BOX.ordinal,
                    id = "uploadLimit.custom",
                    top = 10,
                    bottom = 16,
                    one = field(
                        id = "uploadLimit.custom",
                        value = text,
                        placeholder = s.speedLimitValue,
                        emoji = "⚡",
                        width = 130,
                        slim = true,
                    ),
                    options = speedPresetUnits,
                    pick = speedPresetUnits.indexOf(unit) + 1,
                    hint = if (total <= 0L) "" else when (unit) {
                        "MB/s" -> "= ${number.toLong()} MB/s"
                        "KB/s" -> "= ${number.toLong()} KB/s"
                        "Mbps" -> "= ${number.toLong()} Mbps (${total / (1024L * 1024L)} MB/s)"
                        "Kbps" -> "= ${number.toLong()} Kbps (${total / 1024L} KB/s)"
                        else -> "= $number $unit"
                    },
                )
            )
        }
    }
    rows.group(title = s.torrentSharingGroup) {
        rows.add(switchRow("leech", s.downloadOnlyNoUpload, s.downloadOnlyNoUploadHint, v.disableTorrentUpload))
    }
    rows.group(title = s.torrentStopRatioTitle, footer = s.torrentStopRatioHint) {
        rows.add(
            SettingRowDto(
                kind = SettingKind.CHIPS.ordinal,
                id = "stopRatio",
                title = s.torrentStopRatioTitle,
                options = STOP_RATIOS.map { if (it == 0f) "0" else it.toString().removeSuffix(".0") },
                pick = STOP_RATIOS.indexOf(com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.torrentStopRatio.value) + 1,
            )
        )
    }
    rows.group(title = s.torrentEngineGroup) {
        rows.add(
            infoRow(
                title = "libtorrent",
                subtitle = localizeStep(v.torrentEngineStatus).ifBlank { null },
                status = if (v.isTorrentEngineReady) s.settingsReady else s.settingsNotReady,
                statusTone = if (v.isTorrentEngineReady) ToneCode.COMPLETED else ToneCode.ERROR,
            )
        )
    }
}

private fun deviceGroup(
    rows: Rows,
    title: String,
    empty: String,
    devices: List<PairedDevice>,
    action: String,
    danger: Boolean,
    prefix: String,
) {
    rows.group(title = title) {
        if (devices.isEmpty()) rows.add(noteRow(empty, ToneCode.MUTED, top = 16, bottom = 16))
        devices.sortedBy { it.name.lowercase() }.forEachIndexed { index, device ->
            if (index > 0) rows.divider()
            val host = device.name.substringBefore(" · ")
            val platform = device.name.substringAfter(" · ", "").ifBlank { device.platform }
            rows.add(
                infoRow(
                    title = host,
                    subtitle = platform.takeIf { it.isNotBlank() && !it.equals(host, ignoreCase = true) },
                    compact = true,
                    buttons = listOf(
                        button(prefix + device.id, action, tone = if (danger) ToneCode.ERROR else ToneCode.PRIMARY)
                    ),
                )
            )
        }
    }
}

private fun devicesRows(rows: Rows, v: SettingsSlice) {
    val s = LocalizationManager.Strings
    rows.group(title = s.sysDeviceSendTitle) {
        rows.add(switchRow("visibleToDevices", s.visibleToDevices, s.visibleToDevicesHint, BehaviorSettings.visibleToDevices.value))
    }
    deviceGroup(rows, s.pairedDevicesTitle, s.noPairedDevices, v.pairedDevices, s.unpairDevice, danger = true, prefix = "unpair.")
    deviceGroup(rows, s.blockedDevicesTitle, s.noBlockedDevices, v.blockedDevices, s.unblockDevice, danger = false, prefix = "unblock.")
}

internal fun isFirefoxBrowser(id: String): Boolean = id == "firefox" || id.startsWith("org.mozilla.")

internal fun seenBrowserId(id: String): String? = when {
    id == "brave" || id == "arc" -> null
    id.startsWith("org.mozilla.") -> "firefox"
    else -> id
}

internal fun browserNeedsStoreLookup(browser: BrowserInfo, seen: Set<String>, versions: Map<String, String>): Boolean {
    val key = seenBrowserId(browser.id)
    val connected = key != null && key in seen
    val version = key?.let { versions[it] }
    val outdated = connected && (version == null || isOlderExtension(version))
    return browser.installed && (outdated || (!connected && isFirefoxBrowser(browser.id)))
}

internal enum class BrowserStep { MISSING, FROM_GITHUB, OPEN_STORE, ADD }

internal data class BrowserPlan(
    val subtitle: String,
    val warning: Boolean,
    val label: String,
    val filled: Boolean,
    val step: BrowserStep,
)

internal fun browserPlan(browser: BrowserInfo, page: SettingsPage): BrowserPlan {
    val s = LocalizationManager.Strings
    val key = seenBrowserId(browser.id)
    val seen = BridgePairing.seenBrowsers.value
    val versions = BridgePairing.seenVersions.value
    val connected = key != null && key in seen
    val version = key?.let { versions[it] }
    val outdated = connected && (version == null || isOlderExtension(version))
    val firefox = isFirefoxBrowser(browser.id)
    val storeVersion = page.storeVersions[browser.id]
    val githubXpi = if (firefox) page.githubXpi else null
    val storeHasIt = storeVersion != null && !isOlderExtension(storeVersion)
    val githubHasIt = firefox && githubXpi != null && !isOlderExtension(githubXpi.version)
    val fromGitHub = (outdated || (!connected && firefox && storeVersion != null)) && !storeHasIt && githubHasIt
    val waitingForStore = outdated && !storeHasIt && !browser.hasLocalCopy && !githubHasIt
    val subtitle = when {
        !browser.installed -> s.browserNotInstalled
        outdated -> (if (version == null) s.extensionOldVersion(EXTENSION_VERSION)
        else s.extensionNeedsUpdate(version, EXTENSION_VERSION)) + "\n" + when {
            storeHasIt -> s.storeHasUpdate(storeVersion.orEmpty())
            browser.hasLocalCopy -> s.updateFromAppHint
            githubHasIt -> s.updateFromGitHubHint(githubXpi?.version.orEmpty())
            else -> s.storeWaiting(EXTENSION_VERSION)
        }

        connected -> "✓ " + s.extensionConnectedVersion(version.orEmpty())
        else -> s.extensionNotAddedYet
    }
    val label = when {
        page.browserBusy == browser.id -> "…"
        fromGitHub -> s.updateFromGitHub
        outdated && storeHasIt -> s.openStore
        outdated && browser.hasLocalCopy -> s.updateFromApp
        outdated -> s.openStore
        else -> s.addExtension
    }
    val step = when {
        !browser.installed -> BrowserStep.MISSING
        fromGitHub && githubXpi != null -> BrowserStep.FROM_GITHUB
        outdated && storeHasIt -> BrowserStep.OPEN_STORE
        else -> BrowserStep.ADD
    }
    return BrowserPlan(
        subtitle = subtitle,
        warning = !browser.installed || outdated,
        label = label,
        filled = browser.installed && (!connected || (outdated && !waitingForStore)),
        step = step,
    )
}

private fun browserRows(rows: Rows, page: SettingsPage) {
    val s = LocalizationManager.Strings
    rows.group(title = s.extInstallGroup, footer = s.extensionInstallFooter) {
        page.browsers.forEachIndexed { index, browser ->
            if (index > 0) rows.divider()
            val plan = browserPlan(browser, page)
            rows.add(
                infoRow(
                    title = browser.name,
                    subtitle = plan.subtitle,
                    subtitleEmoji = if (plan.warning) "⚠️" else "",
                    buttons = listOf(
                        button(
                            "browser." + browser.id,
                            plan.label,
                            filled = plan.filled,
                            enabled = page.browserBusy == null,
                            strong = true,
                        )
                    ),
                )
            )
        }
    }
    val allowed = BridgePairing.legacyAllowed.value
    val blocked = BridgePairing.blocked.value
    val paired = BridgePairing.seenBrowsers.value
    if (allowed.isEmpty() && blocked.isEmpty() && paired.isEmpty()) return
    rows.group(title = s.extConnectionsGroup, footer = s.extConnectionsFooter) {
        var first = true
        if (paired.isNotEmpty()) {
            first = false
            rows.add(
                infoRow(
                    title = paired.keys.sorted().joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
                    subtitle = s.extPairedLabel,
                    buttons = listOf(button("unpairBrowsers", s.extUnpair))
                )
            )
        }
        allowed.sorted().forEach { origin ->
            if (!first) rows.divider()
            first = false
            rows.add(infoRow(title = origin, subtitle = s.extAllowedLabel, buttons = listOf(button("revoke.$origin", s.extRevoke))))
        }
        blocked.sorted().forEach { origin ->
            if (!first) rows.divider()
            first = false
            rows.add(infoRow(title = origin, subtitle = s.extBlockedLabel, buttons = listOf(button("allowAgain.$origin", s.extUnblock))))
        }
    }
}
