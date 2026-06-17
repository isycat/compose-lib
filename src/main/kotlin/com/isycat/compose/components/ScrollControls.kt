package com.isycat.compose.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Middle-mouse-button autoscroll: press and hold the middle button, then move up/down to scroll at
 * a speed proportional to the distance from the press point (classic browser-style autoscroll).
 * Works for any [ScrollableState] (a `ScrollState` from verticalScroll, or a `LazyListState`).
 *
 * The scroll rate is time-based (pixels per second), so it stays constant for a given hold distance
 * regardless of frame rate or how heavy the scrolled content is. The held middle-button moves are
 * consumed so a co-located scrollable (e.g. a `verticalScroll` on the same node) doesn't *also*
 * drag-scroll the same gesture — which otherwise compounds into a runaway "accelerating" scroll.
 */
@Composable
fun Modifier.middleClickAutoScroll(state: ScrollableState): Modifier {
    val scope = rememberCoroutineScope()
    var anchorY by remember { mutableStateOf(0f) }
    var currentY by remember { mutableStateOf(0f) }

    return this.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed) {
                    anchorY = event.changes.first().position.y
                    currentY = anchorY
                    val job = scope.launch {
                        var lastFrame = 0L
                        while (isActive) {
                            val now = withFrameNanos { it }
                            // Seconds since the previous frame, clamped so a stall can't produce a jump.
                            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                            lastFrame = now
                            val delta = currentY - anchorY
                            // Dead zone near the anchor; speed (px/sec) scales with hold distance.
                            if (abs(delta) > DEAD_ZONE_PX) state.scrollBy(delta * SPEED_PER_SEC * dt)
                        }
                    }
                    try {
                        while (true) {
                            val e = awaitPointerEvent()
                            e.changes.firstOrNull()?.let {
                                currentY = it.position.y
                                it.consume() // don't let a co-located scrollable also drag-scroll this move
                            }
                            if (!e.buttons.isTertiaryPressed) break
                        }
                    } finally {
                        job.cancel()
                    }
                }
            }
        }
    }
}

private const val DEAD_ZONE_PX = 12f
private const val SPEED_PER_SEC = 3.6f // px/sec per px of hold distance (≈ the old per-frame rate at 60fps)
