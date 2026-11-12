package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.isAllAudio
import com.michelle.raouf.fetch_jet.nativeapp.dto.LinkLineCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.LinkRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.LinkTagCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.LinkListEntry
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun qualityText(quality: MediaQualityEntry): String =
    quality.qualityLabel + if (quality.size > 0L) " · " + formatBytes(quality.size) else ""

private fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%' && i + 2 <= text.lastIndex) {
            val b = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (b != null) {
                bytes.add(b.toByte())
                i += 3
                continue
            }
        }
        c.toString().encodeToByteArray().forEach { bytes.add(it) }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private fun linkHost(url: String): String =
    if (url.startsWith("magnet:", ignoreCase = true)) "BitTorrent"
    else url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@').removePrefix("www.")

private fun linkTitle(url: String): String {
    val strings = LocalizationManager.Strings
    if (url.startsWith("magnet:", ignoreCase = true)) {
        val name = url.substringAfter("dn=", "").substringBefore('&')
        return decodeUnicodeText(percentDecode(name.replace('+', ' '))).ifBlank { strings.linkListMagnet }
    }
    val path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
    val last = percentDecode(path.trimEnd('/').substringAfterLast('/'))
    val host = linkHost(url)
    return when {
        host.contains("youtube.com") || host.contains("youtu.be") -> {
            val id = url.substringAfter("v=", "").substringBefore('&').ifBlank { last }
            strings.linkListYouTubeVideo(id)
        }

        last.isNotBlank() -> decodeUnicodeText(last)
        else -> host
    }
}

private fun linkRow(index: Int, link: LinkListEntry): LinkRowDto {
    val strings = LocalizationManager.Strings
    val (tag, tagCode) = when (link.engine) {
        EngineType.MEDIA_STREAM ->
            if (link.mediaQualities.isAllAudio()) strings.linkTagAudio to LinkTagCode.AUDIO
            else strings.linkTagVideo to LinkTagCode.VIDEO

        EngineType.BITTORRENT -> strings.linkTagTorrent to LinkTagCode.TORRENT
        EngineType.HTTP_DIRECT -> strings.linkTagFile to LinkTagCode.FILE
    }
    val title = link.mediaTitle?.let { decodeUnicodeText(it) } ?: linkTitle(link.url)
    var line = LinkLineCode.NONE
    var lineText = ""
    var qualities = emptyList<String>()
    var qualityIndexes = emptyList<Int>()
    var qualityPick = 0
    if (link.isSelected && link.engine == EngineType.MEDIA_STREAM) {
        when {
            link.isInspectingMedia -> {
                line = LinkLineCode.INSPECTING
                lineText = strings.inspectingVideoQualities
            }

            link.mediaQualities.isNotEmpty() -> {
                val current = link.mediaQualities.firstOrNull { it.isSelected } ?: link.mediaQualities.first()
                line = LinkLineCode.QUALITIES
                lineText = qualityText(current)
                qualities = link.mediaQualities.map { qualityText(it) }
                qualityIndexes = link.mediaQualities.map { it.index }
                qualityPick = link.mediaQualities.indexOfFirst { it.index == current.index } + 1
            }

            link.mediaInspectFailed -> {
                line = LinkLineCode.FAILED
                lineText = strings.sysFailedToInspectMediaQualities
            }
        }
    } else if (link.isSelected && link.engine == EngineType.BITTORRENT) {
        when {
            link.isInspecting -> {
                line = LinkLineCode.INSPECTING
                lineText = strings.readingTorrentFiles
            }

            link.torrentFiles.isNotEmpty() -> line = LinkLineCode.FILES
            link.inspectError != null -> {
                line = LinkLineCode.ERROR
                lineText = strings.couldNotReadTorrentFiles(link.inspectError.orEmpty())
            }
        }
    }
    return LinkRowDto(
        index = index,
        title = title,
        host = linkHost(link.url),
        tag = tag,
        tagCode = tagCode.ordinal,
        selected = link.isSelected,
        line = line.ordinal,
        lineText = lineText,
        qualities = qualities,
        qualityIndexes = qualityIndexes,
        qualityPick = qualityPick,
        files = if (line == LinkLineCode.FILES) link.torrentFiles.map { torrentFileRow(it) } else emptyList(),
        filesName = if (line == LinkLineCode.FILES) link.torrentName ?: strings.torrentFiles else "",
        filesAllSelected = line == LinkLineCode.FILES && link.torrentFiles.all { it.isSelected },
    )
}

internal fun Presenter.startAddLinks() {
    scope.launch {
        combine(vm.uiState.map { it.linkList }.distinctUntilChanged(), AppLanguage.current) { links, _ ->
            links.mapIndexed { index, link -> linkRow(index, link) }
        }.collect { addLinks.publish(it) }
    }
}
