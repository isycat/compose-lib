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
                        while (isActive) {
                            val delta = currentY - anchorY
                            // Dead zone near the anchor; speed scales with distance.
                            if (abs(delta) > 12f) state.scrollBy(delta * 0.06f)
                            withFrameNanos { }
                        }
                    }
                    try {
                        while (true) {
                            val e = awaitPointerEvent()
                            e.changes.firstOrNull()?.let { currentY = it.position.y }
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
