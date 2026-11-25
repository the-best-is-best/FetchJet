package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private const val TRIM_DEFAULT_WINDOW_MS = 120_000L
private const val TRIM_MIN_WINDOW_MS = 5_000L

internal val TRIM_TILE_LEVELS = longArrayOf(
    100, 200, 500, 1_000, 2_000, 3_000, 5_000, 10_000, 15_000, 20_000, 30_000, 60_000, 120_000,
    300_000, 600_000, 900_000, 1_800_000, 3_600_000, 7_200_000, 14_400_000
)

internal fun trimTileMs(targetMs: Float): Long {
    val target = targetMs.coerceAtLeast(1f)
    return TRIM_TILE_LEVELS.minBy { kotlin.math.abs(kotlin.math.ln(it / target)) }
}

internal data class TrimFrameRange(
    val tileMs: Long,
    val from: Long,
    val to: Long,
    val visibleFrom: Long,
    val visibleTo: Long
) {
    fun order(): List<Long> {
        val mid = (visibleFrom + visibleTo) / 2
        return (from..to).sortedBy { i ->
            when {
                i in visibleFrom..visibleTo -> kotlin.math.abs(i - mid)
                i < visibleFrom -> 1_000_000L + (visibleFrom - i) * 2
                else -> 1_000_000L + (i - visibleTo) * 2 + 1
            }
        }
    }
}

@Stable
internal class TrimTimelineState {
    var totalMs by mutableLongStateOf(0L)
        private set
    var trackPx by mutableFloatStateOf(0f)
        private set
    var msPerPx by mutableFloatStateOf(0f)
        private set
    var offsetMs by mutableFloatStateOf(0f)
        private set

    val ready: Boolean get() = totalMs > 0L && trackPx > 0f && msPerPx > 0f
    val visibleMs: Float get() = msPerPx * trackPx

    private fun minMsPerPx(): Float = minOf(TRIM_MIN_WINDOW_MS, totalMs).toFloat() / trackPx
    private fun maxMsPerPx(): Float = totalMs.toFloat() / trackPx
    private fun clampOffset(v: Float): Float = v.coerceIn(0f, (totalMs - visibleMs).coerceAtLeast(0f))

    fun setup(total: Long, track: Float, revealMs: Long) {
        if (total <= 0L || track <= 0f) return
        if (total == totalMs && track == trackPx && msPerPx > 0f) return
        val same = ready && total == totalMs
        val window = if (same) visibleMs else minOf(TRIM_DEFAULT_WINDOW_MS, total).toFloat()
        val anchor = if (same) offsetMs else null
        totalMs = total
        trackPx = track
        msPerPx = (window / track).coerceIn(minMsPerPx(), maxMsPerPx())
        offsetMs = clampOffset(anchor ?: (revealMs - visibleMs * 0.1f))
    }

    fun scrollByPx(px: Float): Boolean {
        if (!ready) return false
        val old = offsetMs
        offsetMs = clampOffset(offsetMs + px * msPerPx)
        return offsetMs != old
    }

    fun centerOn(ms: Float) {
        if (ready) offsetMs = clampOffset(ms - visibleMs / 2f)
    }

    fun zoomBy(factor: Float, focusPx: Float) {
        if (!ready || factor <= 0f) return
        val focus = offsetMs + focusPx * msPerPx
        msPerPx = (msPerPx / factor).coerceIn(minMsPerPx(), maxMsPerPx())
        offsetMs = clampOffset(focus - focusPx * msPerPx)
    }

    fun reveal(ms: Long) {
        if (!ready) return
        val margin = visibleMs * 0.05f
        if (ms < offsetMs + margin || ms > offsetMs + visibleMs - margin) centerOn(ms.toFloat())
    }

    fun follow(ms: Long) {
        if (!ready) return
        if (ms < offsetMs || ms > offsetMs + visibleMs) offsetMs = clampOffset(ms - visibleMs * 0.1f)
    }

    fun frameRange(tileTargetPx: Float): TrimFrameRange? {
        if (!ready) return null
        val tile = trimTileMs(tileTargetPx * msPerPx)
        val last = (totalMs - 1) / tile
        val visibleFrom = (offsetMs / tile).toLong().coerceIn(0L, last)
        val visibleTo = ((offsetMs + visibleMs) / tile).toLong().coerceIn(visibleFrom, last)
        val screen = visibleTo - visibleFrom + 1
        return TrimFrameRange(
            tile,
            (visibleFrom - screen).coerceAtLeast(0L),
            (visibleTo + screen).coerceAtMost(last),
            visibleFrom,
            visibleTo
        )
    }
}

@Composable
internal fun rememberTrimTimelineState(): TrimTimelineState = remember { TrimTimelineState() }

private fun DrawScope.drawCropped(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val aspect = width / height
    val (sw, sh) = if (iw / ih > aspect) (ih * aspect) to ih else iw to (iw / aspect)
    drawImage(
        image,
        srcOffset = IntOffset(((iw - sw) / 2f).roundToInt(), ((ih - sh) / 2f).roundToInt()),
        srcSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
    )
}

@Composable
internal fun TrimTimeline(
    state: TrimTimelineState,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    selected: TrimHandle,
    minLengthMs: Long,
    onSelect: (TrimHandle) -> Unit,
    onHandleMoved: (TrimHandle, Long) -> Unit,
    onDragFinished: () -> Unit,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier,
    stripHeight: Dp = 60.dp,
    wave: List<Float>? = null,
    frameAt: ((Long, Long) -> ImageBitmap?)? = null,
    onNeedFrames: (TrimFrameRange) -> Unit = {},
    following: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    val handleWidth = 16.dp
    val currentStart by rememberUpdatedState(startMs)
    val currentEnd by rememberUpdatedState(endMs)
    val currentMin by rememberUpdatedState(minLengthMs)
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val moveHandle by rememberUpdatedState(onHandleMoved)
    val dragFinished by rememberUpdatedState(onDragFinished)
    val scrub by rememberUpdatedState(onScrub)
    val needFrames by rememberUpdatedState(onNeedFrames)
    val scope = rememberCoroutineScope()
    val tileTargetPx = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(positionMs, following) { if (following) state.follow(positionMs) }
    if (frameAt != null) LaunchedEffect(state, tileTargetPx) {
        snapshotFlow { state.frameRange(tileTargetPx) }
            .distinctUntilChanged()
            .collect { range -> if (range != null) needFrames(range) }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(stripHeight + 8.dp)) {
                val density = LocalDensity.current
                val hwPx = with(density) { handleWidth.toPx() }
                val widthPx = with(density) { maxWidth.toPx() }
                val trackPx = (widthPx - 2 * hwPx).coerceAtLeast(1f)
                LaunchedEffect(durationMs, trackPx) {
                    if (durationMs > 0L) state.setup(durationMs, trackPx, currentStart)
                }
                fun xOf(ms: Float): Float = hwPx + (ms - state.offsetMs) / state.msPerPx
                fun msOf(x: Float): Long =
                    (state.offsetMs + (x - hwPx) * state.msPerPx).toLong().coerceIn(0L, state.totalMs)
                fun nearest(x: Float): TrimHandle? {
                    if (!state.ready) return null
                    val ds = kotlin.math.abs(x - (xOf(currentStart.toFloat()) - hwPx / 2))
                    val de = kotlin.math.abs(x - (xOf(currentEnd.toFloat()) + hwPx / 2))
                    val reach = hwPx * 2.2f
                    return when {
                        ds <= reach && (ds <= de || de > reach) -> TrimHandle.START
                        de <= reach -> TrimHandle.END
                        else -> null
                    }
                }
                fun move(handle: TrimHandle, x: Float) {
                    val total = state.totalMs
                    val ms = msOf(if (handle == TrimHandle.START) x + hwPx / 2 else x - hwPx / 2)
                    val value = if (handle == TrimHandle.START) ms.coerceIn(0L, (currentEnd - currentMin).coerceAtLeast(0L))
                    else ms.coerceIn((currentStart + currentMin).coerceAtMost(total), total)
                    moveHandle(handle, value)
                }

                Canvas(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(state, hwPx, trackPx) {
                            var flingJob: Job? = null
                            val edge = 44.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                flingJob?.cancel()
                                val handle = nearest(down.position.x)
                                if (handle != null) {
                                    down.consume()
                                    select(handle)
                                    var lastX = down.position.x
                                    var dragged = false
                                    val auto = scope.launch {
                                        var last = 0L
                                        while (isActive) {
                                            val now = withFrameNanos { it }
                                            if (last != 0L && dragged) {
                                                val dt = (now - last) / 1_000_000_000f
                                                val leftDepth = (hwPx + edge - lastX) / edge
                                                val rightDepth = (lastX - (hwPx + trackPx - edge)) / edge
                                                val speed = when {
                                                    leftDepth > 0f -> -leftDepth.coerceAtMost(1.5f)
                                                    rightDepth > 0f -> rightDepth.coerceAtMost(1.5f)
                                                    else -> 0f
                                                }
                                                if (speed != 0f && state.scrollByPx(speed * trackPx * 0.9f * dt)) move(handle, lastX)
                                            }
                                            last = now
                                        }
                                    }
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            if (change.positionChanged()) {
                                                change.consume()
                                                dragged = true
                                                lastX = change.position.x
                                                move(handle, lastX)
                                            }
                                        }
                                    } finally {
                                        auto.cancel()
                                        dragFinished()
                                    }
                                } else {
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    var travelled = 0f
                                    var panning = false
                                    var pinched = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            pinched = true
                                            val zoom = event.calculateZoom()
                                            val centre = event.calculateCentroid()
                                            if (zoom != 1f && centre.isSpecified) state.zoomBy(zoom, centre.x - hwPx)
                                            val pan = event.calculatePan()
                                            if (pan.x != 0f) state.scrollByPx(-pan.x)
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else if (!pinched) {
                                            val c = pressed[0]
                                            val dx = c.position.x - c.previousPosition.x
                                            travelled += kotlin.math.abs(dx) + kotlin.math.abs(c.position.y - c.previousPosition.y)
                                            if (!panning && travelled > viewConfiguration.touchSlop) panning = true
                                            if (panning && c.positionChanged()) {
                                                state.scrollByPx(-dx)
                                                c.consume()
                                            }
                                            tracker.addPosition(c.uptimeMillis, c.position)
                                        }
                                    }
                                    if (!panning && !pinched) {
                                        if (state.ready) scrub(msOf(down.position.x))
                                    } else if (panning && !pinched) {
                                        val vx = tracker.calculateVelocity().x
                                        if (kotlin.math.abs(vx) > 80f) flingJob = scope.launch {
                                            var lastValue = 0f
                                            AnimationState(0f, -vx).animateDecay(exponentialDecay()) {
                                                val delta = value - lastValue
                                                lastValue = value
                                                if (!state.scrollByPx(delta)) cancelAnimation()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    if (!state.ready) return@Canvas
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    val h = bottom - top
                    val trackLeft = hwPx
                    val trackRight = hwPx + trackPx
                    val total = state.totalMs
                    val offset = state.offsetMs
                    val visible = state.visibleMs
                    val endX = xOf(total.toFloat()).coerceAtMost(trackRight)
                    drawRoundRect(Color(0xFF1C1C24), Offset(trackLeft, top), Size(trackRight - trackLeft, h), CornerRadius(6.dp.toPx()))
                    clipRect(trackLeft, top, endX, bottom) {
                        val shownTo = minOf(offset + visible, total.toFloat())
                        if (wave != null) {
                            if (wave.isNotEmpty()) {
                                val barMs = total.toFloat() / wave.size
                                val colPx = maxOf(barMs / state.msPerPx, 3.dp.toPx())
                                val colMs = colPx * state.msPerPx
                                val mid = top + h / 2f
                                val bar = (colPx * 0.6f).coerceAtLeast(1f)
                                var c = kotlin.math.floor(offset / colMs).toLong()
                                while (c * colMs < shownTo) {
                                    val t = c * colMs
                                    val i0 = (t / barMs).toInt().coerceIn(0, wave.size - 1)
                                    val i1 = ((t + colMs) / barMs).toInt().coerceIn(i0 + 1, wave.size)
                                    var peak = 0f
                                    for (i in i0 until i1) if (wave[i] > peak) peak = wave[i]
                                    val bh = (peak.coerceIn(0f, 1f) * h * 0.9f).coerceAtLeast(1.5f)
                                    drawRect(Color(0xFF8FA3C8), Offset(xOf(t) + (colPx - bar) / 2f, mid - bh / 2f), Size(bar, bh))
                                    c++
                                }
                            }
                        } else if (frameAt != null) {
                            val tile = trimTileMs(tileTargetPx * state.msPerPx)
                            val tilePx = tile / state.msPerPx
                            var k = (offset / tile).toLong().coerceAtLeast(0L)
                            while (k * tile < shownTo) {
                                val x0 = xOf((k * tile).toFloat())
                                val image = frameAt(tile, k)
                                if (image != null) drawCropped(image, x0, top, tilePx, h)
                                else drawRect(Color(0xFF2A2A36), Offset(x0 + 1f, top), Size(tilePx - 2f, h))
                                drawRect(Color(0x55000000), Offset(x0, top), Size(1f, h))
                                k++
                            }
                        }
                    }
                    val sx = xOf(currentStart.toFloat())
                    val ex = xOf(currentEnd.toFloat())
                    val dim = Color(0xB3000000)
                    val dimLeft = sx.coerceIn(trackLeft, endX)
                    val dimRight = ex.coerceIn(trackLeft, endX)
                    if (dimLeft > trackLeft) drawRect(dim, Offset(trackLeft, top), Size(dimLeft - trackLeft, h))
                    if (dimRight < endX) drawRect(dim, Offset(dimRight, top), Size(endX - dimRight, h))
                    clipRect(0f, 0f, size.width, size.height) {
                        val border = 3.dp.toPx()
                        val bl = sx.coerceAtLeast(trackLeft)
                        val br = ex.coerceAtMost(endX)
                        if (br > bl) {
                            drawRect(accent, Offset(bl, top), Size(br - bl, border))
                            drawRect(accent, Offset(bl, bottom - border), Size(br - bl, border))
                        }
                        val radius = CornerRadius(4.dp.toPx())
                        listOf(TrimHandle.START to sx - hwPx, TrimHandle.END to ex).forEach { (handle, left) ->
                            if (left + hwPx < 0f || left > size.width) return@forEach
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
                        val px = xOf(positionMs.toFloat())
                        if (px >= trackLeft - 1f && px <= trackRight + 1f) {
                            drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            }
            TrimOverview(state, startMs, endMs, positionMs, accent)
        }
    }
}

@Composable
private fun TrimZoomButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrimOverview(
    state: TrimTimelineState,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun jump(x: Float) {
                            if (state.ready && size.width > 0) state.centerOn(x / size.width * state.totalMs)
                        }
                        down.consume()
                        jump(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChanged()) {
                                change.consume()
                                jump(change.position.x)
                            }
                        }
                    }
                }
        ) {
            if (!state.ready || state.visibleMs >= state.totalMs * 0.999f) return@Canvas
            val total = state.totalMs.toFloat()
            val w = size.width
            val cy = size.height / 2f
            val track = 4.dp.toPx()
            drawRoundRect(Color(0x14FFFFFF), Offset(0f, cy - track / 2f), Size(w, track), CornerRadius(track / 2f))
            val thumb = 6.dp.toPx()
            val tw = (state.visibleMs / total * w).coerceIn(24.dp.toPx(), w)
            val tx = (state.offsetMs / total * w).coerceIn(0f, w - tw)
            drawRoundRect(Color(0x59FFFFFF), Offset(tx, cy - thumb / 2f), Size(tw, thumb), CornerRadius(thumb / 2f))
        }
        TrimZoomButton("−") { state.zoomBy(0.5f, state.trackPx / 2f) }
        TrimZoomButton("+") { state.zoomBy(2f, state.trackPx / 2f) }
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
