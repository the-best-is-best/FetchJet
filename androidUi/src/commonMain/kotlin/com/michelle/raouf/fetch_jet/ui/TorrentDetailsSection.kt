package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentPeerInfo
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentTrackerInfo
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.TorrentDetailsUi
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.michelle.raouf.fetch_jet.utils.formatBytes
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import com.michelle.raouf.fetch_jet.utils.ltr
import com.tbib.kmp_localization.LocalizationManager
import kotlin.math.roundToInt

private const val MAX_PEERS_SHOWN = 30

@Composable
internal fun TorrentDetailsSection(
    taskId: String,
    ui: TorrentDetailsUi?,
    speedDisplayUnit: String,
    onEvent: (DashboardUiEvent) -> Unit
) {
    val send by rememberUpdatedState(onEvent)
    DisposableEffect(taskId) {
        send(DashboardUiEvent.OnTorrentDetailsOpened(taskId))
        onDispose { send(DashboardUiEvent.OnTorrentDetailsClosed) }
    }

    val current = ui?.takeIf { it.taskId == taskId }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = LocalizationManager.Strings.torrentDetailsTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                current == null || current.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                }

                !current.supported -> Text(
                    text = LocalizationManager.Strings.torrentDetailsUnsupported,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> TorrentDetailsBody(taskId, current, speedDisplayUnit, send)
            }
        }
    }
}

@Composable
private fun TorrentDetailsBody(
    taskId: String,
    ui: TorrentDetailsUi,
    speedDisplayUnit: String,
    onEvent: (DashboardUiEvent) -> Unit
) {
    val details = ui.details
    val ratioText = ltr(((ui.ratio * 100f).roundToInt() / 100.0).toString())
    val stopText = if (ui.stopRatio > 0f) {
        " / " + ltr(((ui.stopRatio * 100f).roundToInt() / 100.0).toString())
    } else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TorrentStat(
            LocalizationManager.Strings.torrentDetailsRatio,
            ratioText + stopText,
            Modifier.weight(1f)
        )
        TorrentStat(
            LocalizationManager.Strings.torrentDetailsUploaded,
            formatBytes(details.uploadedBytes),
            Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TorrentStat(
            LocalizationManager.Strings.torrentDetailsPieces,
            ltr("${details.piecesDone} / ${details.pieceCount}"),
            Modifier.weight(1f)
        )
        TorrentStat(
            LocalizationManager.Strings.torrentDetailsPieceSize,
            if (details.pieceSize > 0L) formatBytes(details.pieceSize) else "—",
            Modifier.weight(1f)
        )
    }

    TorrentDivider()
    TorrentCaption("${LocalizationManager.Strings.torrentDetailsTrackers} (${details.trackers.size})")
    Spacer(modifier = Modifier.height(6.dp))
    if (details.trackers.isEmpty()) {
        TorrentEmptyLine(LocalizationManager.Strings.torrentDetailsNoTrackers)
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            details.trackers.forEach { tracker -> TrackerRow(tracker) }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    var newTracker by remember(taskId) { mutableStateOf("") }
    val trackerError = when (ui.trackerError) {
        "" -> null
        "invalid" -> LocalizationManager.Strings.torrentDetailsTrackerInvalid
        "refused" -> LocalizationManager.Strings.torrentDetailsTrackerRefused
        else -> ui.trackerError
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FetchJetTextField(
            value = newTracker,
            onValueChange = { newTracker = it },
            modifier = Modifier.weight(1f),
            placeholder = LocalizationManager.Strings.torrentDetailsTrackerHint,
            error = trackerError,
            height = 42.dp
        )
        Button(
            onClick = {
                val url = newTracker.trim()
                if (url.isNotEmpty()) {
                    onEvent(DashboardUiEvent.OnTorrentAddTracker(taskId, url))
                    newTracker = ""
                }
            },
            enabled = newTracker.isNotBlank(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(42.dp)
        ) {
            Text(
                LocalizationManager.Strings.torrentDetailsAddTracker,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }

    TorrentDivider()
    TorrentCaption("${LocalizationManager.Strings.torrentDetailsPeers} (${details.peers.size})")
    Spacer(modifier = Modifier.height(6.dp))
    if (details.peers.isEmpty()) {
        TorrentEmptyLine(LocalizationManager.Strings.torrentDetailsNoPeers)
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            details.peers.take(MAX_PEERS_SHOWN).forEach { peer -> PeerRow(peer, speedDisplayUnit) }
        }
    }

    TorrentDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalizationManager.Strings.torrentDetailsFirstLast,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = details.firstLastPiecesFirst,
            onCheckedChange = { onEvent(DashboardUiEvent.OnTorrentFirstLastChanged(taskId, it)) }
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TorrentActionButton(
            LocalizationManager.Strings.torrentDetailsRecheck,
            Modifier.weight(1f).height(42.dp)
        ) { onEvent(DashboardUiEvent.OnTorrentRecheck(taskId)) }
        TorrentActionButton(
            LocalizationManager.Strings.torrentDetailsCopyMagnet,
            Modifier.weight(1f).height(42.dp),
            enabled = details.magnetLink.isNotBlank()
        ) { onEvent(DashboardUiEvent.OnTorrentCopyMagnet(taskId)) }
    }
}

@Composable
private fun TrackerRow(tracker: TorrentTrackerInfo) {
    val (label, color) = when (tracker.state) {
        "working" -> LocalizationManager.Strings.torrentDetailsStateWorking to StatusCompleted
        "updating" -> LocalizationManager.Strings.torrentDetailsStateUpdating to MaterialTheme.colorScheme.primary
        "waiting" -> LocalizationManager.Strings.torrentDetailsStateWaiting to StatusPaused
        "error" -> LocalizationManager.Strings.torrentDetailsStateError to MaterialTheme.colorScheme.error
        "disabled" -> LocalizationManager.Strings.torrentDetailsStateDisabled to MaterialTheme.colorScheme.onSurfaceVariant
        else -> tracker.state to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ltr(trackerHost(tracker.url)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TorrentStateChip(label, color)
        }
        if (tracker.state == "error" && tracker.message.isNotBlank()) {
            Text(
                text = tracker.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PeerRow(peer: TorrentPeerInfo, speedDisplayUnit: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ltr(peer.address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ltr("${(peer.progress.coerceIn(0f, 1f) * 100).toInt()}%"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (peer.isSeed) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = peer.client,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "↓ ${formatSpeed(peer.downloadRate, speedDisplayUnit)}  ↑ ${
                    formatSpeed(peer.uploadRate, speedDisplayUnit)
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TorrentStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun TorrentCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TorrentEmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

@Composable
private fun TorrentDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun TorrentStateChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TorrentActionButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

private fun trackerHost(url: String): String {
    val rest = url.substringAfter("://", url).substringBefore('/').substringAfterLast('@')
    return rest.ifBlank { url }
}
