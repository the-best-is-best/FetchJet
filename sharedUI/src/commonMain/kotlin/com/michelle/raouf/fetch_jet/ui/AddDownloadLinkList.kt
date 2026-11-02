package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.presentation.dashboard.LinkListEntry
import com.michelle.raouf.fetch_jet.utils.decodeUnicodeText
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun LinkListSection(
    links: List<LinkListEntry>,
    onToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onTorrentFileToggled: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    onTorrentFilePriority: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onTorrentSelectAll: (Int, Boolean) -> Unit = { _, _ -> },
    onQualitySelected: (Int, Int) -> Unit = { _, _ -> },
    onRetryInspect: (Int) -> Unit = {}
) {
    val selected = links.count { it.isSelected }
    val allSelected = selected == links.size
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            LocalizationManager.Strings.selectedOfLinks(selected, links.size),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onSelectAll(!allSelected) }) {
            Text(
                if (allSelected) LocalizationManager.Strings.selectNone else LocalizationManager.Strings.selectAll,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            links.forEachIndexed { index, link ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
                LinkRow(link) { onToggled(index, it) }
                if (link.engine == EngineType.MEDIA_STREAM && link.isSelected) {
                    VideoQualityLine(link, onRetry = { onRetryInspect(index) }) {
                        onQualitySelected(
                            index,
                            it
                        )
                    }
                }
                if (link.engine == EngineType.BITTORRENT && link.isSelected) {
                    Column(
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 10.dp
                        )
                    ) {
                        when {
                            link.isInspecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    LocalizationManager.Strings.readingTorrentFiles,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            link.torrentFiles.isNotEmpty() -> TorrentFilesSelectionCard(
                                torrentName = link.torrentName,
                                files = link.torrentFiles,
                                onFileSelectionToggled = { file, on ->
                                    onTorrentFileToggled(
                                        index,
                                        file,
                                        on
                                    )
                                },
                                onFilePriorityChanged = { file, p ->
                                    onTorrentFilePriority(
                                        index,
                                        file,
                                        p
                                    )
                                },
                                onSelectAll = { onTorrentSelectAll(index, it) }
                            )

                            link.inspectError != null -> Text(
                                LocalizationManager.Strings.couldNotReadTorrentFiles(link.inspectError.orEmpty()),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: LinkListEntry, onToggle: (Boolean) -> Unit) {
    val (tag, tagColor) = when (link.engine) {
        EngineType.MEDIA_STREAM -> LocalizationManager.Strings.linkTagVideo to Color(0xFFE879F9)
        EngineType.BITTORRENT -> LocalizationManager.Strings.linkTagTorrent to Color(0xFF34D399)
        EngineType.HTTP_DIRECT -> LocalizationManager.Strings.linkTagFile to Color(0xFF38BDF8)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!link.isSelected) }
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = link.isSelected,
            onCheckedChange = onToggle,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                link.mediaTitle?.let { decodeUnicodeText(it) } ?: linkTitle(link.url),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                linkHost(link.url),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tagColor)
        }
    }
}

@Composable
private fun VideoQualityLine(link: LinkListEntry, onRetry: () -> Unit, onSelected: (Int) -> Unit) {
    Box(modifier = Modifier.padding(start = 48.dp, end = 12.dp, bottom = 8.dp)) {
        when {
            link.isInspectingMedia -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    LocalizationManager.Strings.inspectingVideoQualities,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            link.mediaQualities.isNotEmpty() -> {
                var open by remember { mutableStateOf(false) }
                val current = link.mediaQualities.firstOrNull { it.isSelected }
                    ?: link.mediaQualities.first()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { open = true }
                ) {
                    Text(
                        qualityText(current) + "  ▾",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    link.mediaQualities.forEach { q ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    qualityText(q),
                                    fontSize = 13.sp,
                                    fontWeight = if (q.index == current.index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                open = false
                                onSelected(q.index)
                            }
                        )
                    }
                }
            }

            link.mediaInspectFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalizationManager.Strings.sysFailedToInspectMediaQualities,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRetry) {
                    Text(
                        LocalizationManager.Strings.retry,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun qualityText(q: MediaQualityEntry): String =
    q.qualityLabel + if (q.size > 0L) " · " + formatBytes(q.size) else ""

private fun linkTitle(url: String): String {
    if (url.startsWith("magnet:", ignoreCase = true)) {
        val name = url.substringAfter("dn=", "").substringBefore('&')
        return decodeUnicodeText(
            percentDecode(
                name.replace(
                    '+',
                    ' '
                )
            )
        ).ifBlank { LocalizationManager.Strings.linkListMagnet }
    }
    val path =
        url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
    val last = percentDecode(path.trimEnd('/').substringAfterLast('/'))
    val host = linkHost(url)
    return when {
        host.contains("youtube.com") || host.contains("youtu.be") -> {
            val id = url.substringAfter("v=", "").substringBefore('&').ifBlank { last }
            LocalizationManager.Strings.linkListYouTubeVideo(id)
        }

        last.isNotBlank() -> decodeUnicodeText(last)
        else -> host
    }
}

private fun linkHost(url: String): String =
    if (url.startsWith("magnet:", ignoreCase = true)) "BitTorrent"
    else url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")

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
