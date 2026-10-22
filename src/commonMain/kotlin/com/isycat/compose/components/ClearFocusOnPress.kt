package com.isycat.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Clears keyboard focus whenever a pointer is pressed inside this element, before anything in it handles the press.
 * Compose leaves a text field focused when you click somewhere that can't take focus (a canvas, a list of items), so
 * its caret keeps blinking and keys keep going to it; put this on such areas. Avoid wrapping text fields in it: a click
 * in one would blur it before it refocuses.
 */
@Composable
fun Modifier.clearFocusOnPress(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) focusManager.clearFocus()
            }
        }
    }
}
