package com.isycat.compose.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onDrag] with how far the pointer moved while the middle button is held, anywhere over this element: the
 * element sees the events before anything inside it, and uses them all up, so a middle-drag that starts on a child
 * (a button, a draggable item) never reaches it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.onMiddleDrag(onDrag: (Offset) -> Unit): Modifier {
    val current by rememberUpdatedState(onDrag)
    return pointerInput(Unit) {
        awaitEachGesture {
            var event = awaitPointerEvent(PointerEventPass.Initial)
            while (!(event.type == PointerEventType.Press && event.button == PointerButton.Tertiary)) {
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
            var last = event.changes.first().position
            event.changes.forEach { it.consume() }
            while (event.buttons.isTertiaryPressed) {
                event = awaitPointerEvent(PointerEventPass.Initial)
                val position = event.changes.first().position
                if (event.buttons.isTertiaryPressed) current(position - last)
                last = position
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * Middle-button drag scrolling for any vertical [ScrollableState] (a `ScrollState` or a `LazyListState`): hold the
 * middle button and the content follows the pointer, the way a canvas pans. The other choice is
 * [middleClickAutoScroll], which scrolls at a speed set by how far the pointer is from where it was pressed.
 */
@Composable
fun Modifier.middleDragScroll(state: ScrollableState): Modifier = onMiddleDrag { delta -> state.dispatchRawDelta(-delta.y) }

/** What holding the middle mouse button over a scrolling area does. */
enum class MiddleClickScroll {
    /** Scrolls at a speed set by how far the pointer moves from where it was pressed, as browsers do. */
    AutoScroll,

    /** Drags the content along with the pointer. */
    Drag,

    /** Nothing. */
    None,
}
