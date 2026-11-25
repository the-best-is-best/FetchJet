package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1L)
    val shown = dragging ?: (positionMs.coerceIn(0L, total).toFloat() / total)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            MediaPlayback.formatTime((shown * total).toLong()),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * total).toLong()) }
                dragging = null
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            MediaPlayback.formatTime(durationMs),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

internal enum class TrimHandle { START, END }

internal fun formatTrimTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val millis = (t % 1000).toString().padStart(3, '0')
    return "${MediaPlayback.formatTime(t)}.$millis"
}

@Composable
internal fun TrimTimeline(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    frames: List<ImageBitmap?>,
    selected: TrimHandle,
    minLengthMs: Long,
    onSelect: (TrimHandle) -> Unit,
    onHandleMoved: (TrimHandle, Long) -> Unit,
    onDragFinished: () -> Unit,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier,
    stripHeight: Dp = 60.dp,
    wave: List<Float>? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val total = durationMs.coerceAtLeast(1L)
    val handleWidth = 16.dp
    val currentStart by rememberUpdatedState(startMs)
    val currentEnd by rememberUpdatedState(endMs)
    val currentSelected by rememberUpdatedState(selected)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth().height(stripHeight + 8.dp)) {
            val density = LocalDensity.current
            val hwPx = with(density) { handleWidth.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val trackPx = (widthPx - 2 * hwPx).coerceAtLeast(1f)
            fun xOf(ms: Long): Float = hwPx + ms.coerceIn(0L, total).toFloat() / total * trackPx
            fun msOf(x: Float): Long = (((x - hwPx) / trackPx).coerceIn(0f, 1f) * total).toLong()
            fun nearest(x: Float): TrimHandle? {
                val ds = kotlin.math.abs(x - (xOf(currentStart) - hwPx / 2))
                val de = kotlin.math.abs(x - (xOf(currentEnd) + hwPx / 2))
                val reach = hwPx * 2.2f
                return when {
                    ds <= reach && (ds <= de || de > reach) -> TrimHandle.START
                    de <= reach -> TrimHandle.END
                    else -> null
                }
            }
            fun move(handle: TrimHandle, x: Float) {
                val ms = msOf(if (handle == TrimHandle.START) x + hwPx / 2 else x - hwPx / 2)
                val value = if (handle == TrimHandle.START) ms.coerceIn(0L, (currentEnd - minLengthMs).coerceAtLeast(0L))
                else ms.coerceIn((currentStart + minLengthMs).coerceAtMost(total), total)
                onHandleMoved(handle, value)
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = handleWidth, vertical = 4.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1C1C24))
            ) {
                if (wave != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (wave.isNotEmpty()) {
                            val step = size.width / wave.size
                            val bar = (step * 0.6f).coerceAtLeast(1f)
                            val mid = size.height / 2f
                            wave.forEachIndexed { i, v ->
                                val h = (v.coerceIn(0f, 1f) * size.height * 0.9f).coerceAtLeast(1.5f)
                                drawRect(
                                    Color(0xFF8FA3C8),
                                    topLeft = androidx.compose.ui.geometry.Offset(i * step + (step - bar) / 2f, mid - h / 2f),
                                    size = androidx.compose.ui.geometry.Size(bar, h)
                                )
                            }
                        }
                    }
                } else if (frames.isEmpty()) {
                    Spacer(Modifier.fillMaxSize())
                } else {
                    frames.forEach { frame ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .border(0.5.dp, Color(0x33000000))
                        ) {
                            if (frame != null) {
                                Image(
                                    bitmap = frame,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(Color(0xFF2A2A36)))
                            }
                        }
                    }
                }
            }

            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(total, widthPx) {
                        detectTapGestures { offset ->
                            val handle = nearest(offset.x)
                            if (handle != null) onSelect(handle) else onScrub(msOf(offset.x))
                        }
                    }
                    .pointerInput(total, widthPx) {
                        var dragged: TrimHandle? = null
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragged = nearest(offset.x)
                                dragged?.let { onSelect(it) }
                                if (dragged == null) onScrub(msOf(offset.x))
                            },
                            onDragEnd = {
                                if (dragged != null) onDragFinished()
                                dragged = null
                            },
                            onDragCancel = {
                                if (dragged != null) onDragFinished()
                                dragged = null
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val d = dragged
                                if (d != null) move(d, change.position.x) else onScrub(msOf(change.position.x))
                            }
                        )
                    }
            ) {
                val top = 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                val h = bottom - top
                val sx = xOf(startMs)
                val ex = xOf(endMs)
                val dim = Color(0xB3000000)
                if (sx > hwPx) drawRect(dim, Offset(hwPx, top), Size(sx - hwPx, h))
                if (ex < hwPx + trackPx) drawRect(dim, Offset(ex, top), Size(hwPx + trackPx - ex, h))
                val border = 3.dp.toPx()
                drawRect(accent, Offset(sx, top), Size(ex - sx, border))
                drawRect(accent, Offset(sx, bottom - border), Size(ex - sx, border))
                val radius = CornerRadius(4.dp.toPx())
                listOf(TrimHandle.START to sx - hwPx, TrimHandle.END to ex).forEach { (handle, left) ->
                    val active = handle == currentSelected
                    drawRoundRect(
                        if (active) accent else accent.copy(alpha = 0.75f),
                        Offset(left, top),
                        Size(hwPx, h),
                        radius
                    )
                    val cx = left + hwPx / 2
                    drawLine(
                        Color.White.copy(alpha = if (active) 1f else 0.8f),
                        Offset(cx, top + h * 0.32f),
                        Offset(cx, top + h * 0.68f),
                        2.dp.toPx(),
                        StrokeCap.Round
                    )
                }
                val px = xOf(positionMs)
                drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun TrimTimeBox(
    label: String,
    ms: Long,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.22f) else Color(0x22FFFFFF))
            .border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
        Text(
            formatTrimTime(ms),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
internal fun RepeatNudgeButton(text: String, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x33FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val job = scope.launch {
                        delay(400)
                        while (true) {
                            step()
                            delay(70)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TrimNudgeRow(
    startLabel: String,
    endLabel: String,
    lengthLabel: String,
    startMs: Long,
    endMs: Long,
    selected: TrimHandle,
    onSelect: (TrimHandle) -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RepeatNudgeButton("‹", onStep = { onNudge(-1) })
            TrimTimeBox(startLabel, startMs, selected == TrimHandle.START, { onSelect(TrimHandle.START) }, Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp)
            ) {
                Text(lengthLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                Text(
                    formatTrimTime(endMs - startMs),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            TrimTimeBox(endLabel, endMs, selected == TrimHandle.END, { onSelect(TrimHandle.END) }, Modifier.weight(1f))
            RepeatNudgeButton("›", onStep = { onNudge(1) })
        }
    }
}
