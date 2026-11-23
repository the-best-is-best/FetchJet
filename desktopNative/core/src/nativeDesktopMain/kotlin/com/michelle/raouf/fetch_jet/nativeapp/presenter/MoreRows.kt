package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.more.MoreLinks
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ToneCode
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.update

internal const val MORE_SETTINGS = "more.settings"
internal const val MORE_SHARE = "more.share"
internal const val MORE_SITE = "more.site"
internal const val MORE_LINK = "more.link."

private fun moreHeaderRows(rows: Rows, update: UpdateState) {
    val s = LocalizationManager.Strings
    val available = update.available
    rows.group {
        rows.add(infoRow(title = "FetchJet", subtitle = s.summaryVersion(update.currentVersion)))
        if (update.supported) {
            rows.divider()
            rows.add(
                infoRow(
                    title = available?.let { s.versionIsAvailable(it.version) } ?: s.youHaveLatest,
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
        }
    }
}

internal fun Presenter.buildMoreRows(): List<SettingRowDto> {
    val s = LocalizationManager.Strings
    val rows = Rows()
    moreHeaderRows(rows, updateState())
    rows.group {
        rows.add(infoRow(title = s.homeSettings, subtitle = s.moreSettingsHint, id = MORE_SETTINGS, clickable = true, side = "›"))
    }
    rows.group {
        rows.add(infoRow(title = s.moreEverywhereTitle, id = MORE_SITE, clickable = true, side = "↗"))
        rows.divider()
        rows.add(infoRow(title = s.moreShare, id = MORE_SHARE, clickable = true, side = "⤴"))
    }
    rows.group(title = s.moreCommunityTitle) {
        for ((index, link) in MoreLinks.community.withIndex()) {
            if (index > 0) rows.divider()
            rows.add(infoRow(title = MoreLinks.communityTitle(link.key), id = MORE_LINK + link.key, clickable = true, side = "↗"))
        }
    }
    return rows.list
}

internal fun Presenter.moreClick(id: String): Boolean {
    when {
        id == MORE_SETTINGS -> settingsLocal.update { it.copy(inSettings = true) }
        id == MORE_SHARE -> {
            val text = MoreLinks.shareText()
            sharedTexts.update { (it + text).takeLast(20) }
            if (!headless) DesktopOs.setClipboardText(text)
            showToast(LocalizationManager.Strings.copied)
        }
        id == MORE_SITE -> openExternal(MoreLinks.SITE)
        id.startsWith(MORE_LINK) -> {
            val link = MoreLinks.community.firstOrNull { it.key == id.removePrefix(MORE_LINK) } ?: return true
            openExternal(link.url)
        }
        else -> return false
    }
    return true
}
