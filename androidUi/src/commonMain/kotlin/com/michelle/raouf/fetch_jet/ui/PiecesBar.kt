package com.michelle.raouf.fetch_jet.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import com.michelle.raouf.fetch_jet.domain.model.displayChunks
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import kotlin.math.max

/**
 * IDM-style piece map, FetchJet flavour.
 *
 *  - The file as one bar, split into its pieces. Each piece fills left→right as it downloads;
 *    a piece a connection is writing right now glows with a moving sheen, a piece the server
 *    turned away (429 / 503) is amber, a finished piece is solid.
 *  - Under it, one small card per connection: number, live speed, its own progress line.
 */
@Composable
fun PiecesBar(task: DownloadTask, speedDisplayUnit: String) {
    val chunks = task.displayChunks()
    // The map's own span: real bytes for a file, virtual equal slots for HLS segments.
    val total = chunks.maxOfOrNull { it.endByte + 1 }?.takeIf { it > 0 } ?: return
    val manySegments = chunks.size > 40
    val scheme = MaterialTheme.colorScheme

    val done = scheme.primary
    val activeA = scheme.primary
    val activeB = Color(0xFF7DF9FF)
    val waiting = StatusPaused
    val track = scheme.surfaceVariant.copy(alpha = 0.55f)
    val gap = scheme.surface

    val active = chunks.filter { it.isActive }
    val stalled = chunks.filter { !it.isActive && !it.isCompleted && it.note != null }
    val finished = chunks.count { it.isCompleted }

    // The sheen on active pieces slides forever.
    val sheen by rememberInfiniteTransition(label = "sheen").animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen"
    )

    Column {
        // header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                LocalizationManager.Strings.homePiecesLabel,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Pill(
                if (manySegments) LocalizationManager.Strings.homeSegmentsCount(chunks.size) else LocalizationManager.Strings.homePiecesCount(
                    chunks.size
                ),
                scheme.onSurfaceVariant,
                scheme.surfaceVariant.copy(alpha = 0.5f)
            )
            if (active.isNotEmpty()) Pill(
                LocalizationManager.Strings.homeActiveCount(active.size),
                activeB,
                activeB.copy(alpha = 0.14f),
                dot = true
            )
            if (stalled.isNotEmpty()) Pill(
                LocalizationManager.Strings.homeWaitingCount(stalled.size),
                waiting,
                waiting.copy(alpha = 0.14f)
            )
            if (finished > 0) Pill(
                LocalizationManager.Strings.homeDoneCount(finished),
                StatusCompleted,
                StatusCompleted.copy(alpha = 0.14f)
            )
        }
        Spacer(Modifier.height(8.dp))

        // the bar
        Canvas(
            modifier = Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp))
        ) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(3f, 3f)
            // Hairline gaps: on a narrow pane with many pieces a wide gap turned the bar into stripes.
            val gapPx = when {
                w / chunks.size > 60f -> 2f
                w / chunks.size > 6f -> 1f
                else -> 0f
            }
            chunks.forEach { c ->
                val len = max(1L, c.endByte - c.startByte + 1)
                val x0 = w * (c.startByte.toFloat() / total) + gapPx / 2
                val x1 = w * ((c.endByte + 1).toFloat() / total) - gapPx / 2
                val segW = max(1f, x1 - x0)
                val fill = (c.downloadedBytes.toFloat() / len).coerceIn(0f, 1f)

                drawRoundRect(track, Offset(x0, 0f), Size(segW, h), r)

                if (fill > 0f) {
                    val fw = segW * fill
                    when {
                        c.isCompleted -> drawRoundRect(done, Offset(x0, 0f), Size(segW, h), r)
                        c.isActive -> {
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(activeA, activeB), x0, x0 + fw),
                                Offset(x0, 0f), Size(fw, h), r
                            )
                            val sx = x0 + fw * sheen
                            drawRoundRect(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.45f),
                                        Color.Transparent
                                    ),
                                    sx - fw * 0.18f, sx + fw * 0.18f
                                ),
                                Offset(x0, 0f), Size(fw, h), r
                            )
                        }

                        else -> drawRoundRect(
                            if (c.note != null) waiting.copy(alpha = 0.85f) else done.copy(alpha = 0.6f),
                            Offset(x0, 0f), Size(fw, h), r
                        )
                    }
                }
                // a held piece with nothing yet: thin amber base line so it reads as "waiting"
                if (fill == 0f && c.note != null) {
                    drawRoundRect(waiting.copy(alpha = 0.5f), Offset(x0, h - 3f), Size(segW, 3f), r)
                }
            }
            if (gapPx > 0f) chunks.drop(1).forEach { c ->
                val x = w * (c.startByte.toFloat() / total)
                drawRect(gap, Offset(x - gapPx / 2, 0f), Size(gapPx, h))
            }
        }

        // connections
        val shown = (active + stalled).sortedBy { it.chunkIndex }
        if (manySegments) {
            // Hundreds of HLS segments: a line instead of a card per connection.
            Spacer(Modifier.height(6.dp))
            Text(
                text = LocalizationManager.Strings.homeSegmentsProgress(
                    finished,
                    chunks.size,
                    if (active.isNotEmpty()) active.joinToString(", ") { "#${it.chunkIndex + 1}" } else null
                ),
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (shown.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            shown.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { c ->
                        ConnectionCard(
                            c = c,
                            speedDisplayUnit = speedDisplayUnit,
                            tint = if (c.isActive) activeB else waiting,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color, dot: Boolean = false) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@Composable
private fun ConnectionCard(
    c: DownloadChunk,
    speedDisplayUnit: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val len = max(1L, c.endByte - c.startByte + 1)
    val frac = (c.downloadedBytes.toFloat() / len).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(5.dp)).background(tint.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    "#${c.chunkIndex + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (c.isActive) formatSpeed(c.speedBytesPerSec, speedDisplayUnit)
                else LocalizationManager.Strings.homeRetryingNote(c.note),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (c.isActive) scheme.onSurface else tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(frac * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface.copy(alpha = 0.85f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceVariant)
        ) {
            if (frac > 0f) Box(
                Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(tint)
            )
        }
    }
}
