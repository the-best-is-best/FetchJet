package com.michelle.raouf.fetch_jet.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.getPlatform
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A draggable scrollbar overlay. Works on every platform (pure Compose foundation, no desktop-only
 * APIs), so the content can be scrolled by dragging the thumb or clicking the track when the mouse
 * wheel is unavailable. Place it as the last child of a Box whose other child is the scrolling
 * content, aligned to the end edge.
 */
@Composable
fun AppScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ScrollbarCore(
        maxScroll = scrollState.maxValue.toFloat(),
        currentScroll = scrollState.value.toFloat(),
        active = scrollState.isScrollInProgress,
        onScrollTo = { target -> scope.launch { scrollState.scrollTo(target.roundToInt()) } },
        onScrollBy = { delta -> scope.launch { scrollState.scrollBy(delta) } },
        modifier = modifier
    )
}

@Composable
fun AppScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    val avgItem = if (visible.isEmpty()) 0f else visible.map { it.size }.average().toFloat()
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val content = avgItem * total
    val maxScroll = (content - viewport).coerceAtLeast(0f)
    val current = listState.firstVisibleItemIndex * avgItem +
            listState.firstVisibleItemScrollOffset

    ScrollbarCore(
        maxScroll = maxScroll,
        currentScroll = current,
        active = listState.isScrollInProgress,
        onScrollTo = { target -> scope.launch { listState.scrollBy(target - current) } },
        onScrollBy = { delta -> scope.launch { listState.scrollBy(delta) } },
        modifier = modifier
    )
}

@Composable
private fun ScrollbarCore(
    maxScroll: Float,
    currentScroll: Float,
    active: Boolean,
    onScrollTo: (Float) -> Unit,
    onScrollBy: (Float) -> Unit,
    modifier: Modifier
) {
    if (!getPlatform().isJvm) return
    if (maxScroll <= 0f) return
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val alpha by animateFloatAsState(if (hovered || active) 0.55f else 0.28f, tween(180))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .hoverable(interaction)
            .padding(vertical = 2.dp)
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        val contentPx = trackPx + maxScroll
        val thumbPx = (trackPx * (trackPx / contentPx)).coerceIn(28f, trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)
        val thumbTop = ((currentScroll / maxScroll) * travel).coerceIn(0f, travel)
        val thumbDp = with(density) { thumbPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(12.dp)
                .pointerInput(maxScroll, travel, thumbPx) {
                    detectTapGestures { offset ->
                        val topProgress = ((offset.y - thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        onScrollTo(topProgress * maxScroll)
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { translationY = thumbTop }
                .width(6.dp)
                .height(thumbDp)
                .clip(RoundedCornerShape(3.dp))
                .background(scheme.onSurface.copy(alpha = alpha))
                .pointerInput(maxScroll, travel) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onScrollBy(drag.y / travel * maxScroll)
                    }
                }
        )
    }
}
