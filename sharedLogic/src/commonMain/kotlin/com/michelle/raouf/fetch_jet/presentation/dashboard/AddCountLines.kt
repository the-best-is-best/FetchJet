package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.allTicked
import com.michelle.raouf.fetch_jet.feature.media_extractor.anySelectable
import com.tbib.kmp_localization.LocalizationManager

data class AddCountLines(val count: String, val before: String)

fun downloadedBeforeText(n: Int): String =
    if (n > 0) LocalizationManager.Strings.grabDownloadedCount.replace("%1", n.toString()) else ""

fun linkCountLines(selected: Int, total: Int, before: Int): AddCountLines =
    AddCountLines(LocalizationManager.Strings.selectedOfLinks(selected, total), downloadedBeforeText(before))

fun playlistCountLines(playlist: MediaPlaylist): AddCountLines = AddCountLines(
    LocalizationManager.Strings.playlistSummary(playlist.entries.size, playlist.entries.count { it.isSelected }),
    downloadedBeforeText(playlist.entries.count { it.downloadedBefore })
)

fun playlistAnySelectable(playlist: MediaPlaylist): Boolean = playlist.anySelectable

fun playlistAllTicked(playlist: MediaPlaylist): Boolean = playlist.allTicked
