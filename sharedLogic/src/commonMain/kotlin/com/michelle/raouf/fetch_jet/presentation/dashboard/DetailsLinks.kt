package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.security.Secrets
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.http_downloader.linkCarriesAccess
import com.tbib.kmp_localization.LocalizationManager

data class DetailsLink(val label: String, val link: String, val note: String = "")

fun detailsLinks(task: DownloadTask): List<DetailsLink> {
    val s = LocalizationManager.Strings
    return listOf(
        s.detailsSource to task.url.takeUnless { task.hidden }.orEmpty(),
        s.detailsPage to (task.requestHeaders["Referer"] ?: task.originalUrl.orEmpty()),
        s.detailsPlaylist to playlistLinkOf(task)
    ).filter { (_, link) -> link.isNotBlank() && !Secrets.isProtected(link) }
        .distinctBy { (_, link) -> link }
        .map { (label, link) ->
            if (linkCarriesAccess(link)) DetailsLink(label, "", s.detailsLinkSensitive) else DetailsLink(label, link)
        }
}
