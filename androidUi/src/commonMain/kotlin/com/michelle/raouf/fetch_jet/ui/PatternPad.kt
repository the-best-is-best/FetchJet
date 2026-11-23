package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private fun cellCenter(index: Int, width: Float, height: Float): Offset =
    Offset((index % 3 + 0.5f) * width / 3f, (index / 3 + 0.5f) * height / 3f)

@Composable
internal fun PatternPad(
    enabled: Boolean,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember { mutableStateListOf<Int>() }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val haptic = LocalHapticFeedback.current
    val report by rememberUpdatedState(onDone)
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .size(240.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val radius = minOf(w, h) / 3f * 0.35f
                fun add(index: Int) {
                    if (index in cells) return
                    val last = cells.lastOrNull()
                    if (last != null) {
                        val r = last / 3 + index / 3
                        val c = last % 3 + index % 3
                        if (r % 2 == 0 && c % 2 == 0) {
                            val mid = r / 2 * 3 + c / 2
                            if (mid !in cells) cells.add(mid)
                        }
                    }
                    cells.add(index)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                fun hit(at: Offset) {
                    val index = (0 until 9).firstOrNull { (cellCenter(it, w, h) - at).getDistance() <= radius } ?: return
                    add(index)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    cells.clear()
                    touch = down.position
                    hit(down.position)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        touch = change.position
                        hit(change.position)
                        change.consume()
                    }
                    val secret = cells.joinToString("") { (it + 1).toString() }
                    cells.clear()
                    touch = null
                    if (secret.isNotEmpty()) report(secret)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val dot = minOf(w, h) / 3f * 0.09f
        val stroke = dot * 0.9f
        for (i in 1 until cells.size) {
            drawLine(active, cellCenter(cells[i - 1], w, h), cellCenter(cells[i], w, h), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        val end = touch
        val last = cells.lastOrNull()
        if (end != null && last != null) {
            drawLine(active.copy(alpha = 0.6f), cellCenter(last, w, h), end, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val on = i in cells
            drawCircle(if (on) active else idle.copy(alpha = if (enabled) 1f else 0.4f), radius = if (on) dot * 1.6f else dot, center = cellCenter(i, w, h))
        }
    }
}
