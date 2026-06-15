package com.isycat.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Stacks an [overlay] layer on top of [content], both filling the available space.
 *
 * Intended for things like a live video/preview surface with HUD-style annotations drawn over
 * it (suggestions, detected text, reticles, etc.). The overlay receives a [BoxScope] so callers
 * can align/position children freely.
 *
 * By default the overlay is non-interactive ([overlayPassthrough] = true): empty overlay regions
 * do not intercept pointer events, so clicks/drag reach [content], while any interactive children
 * placed in the overlay still work normally. Set it to false to make the overlay capture all
 * pointer input (modal-style), blocking interaction with the content beneath.
 */
@Composable
fun OverlayBox(
    modifier: Modifier = Modifier,
    overlayPassthrough: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        val overlayModifier = if (overlayPassthrough) {
            Modifier.matchParentSize()
        } else {
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        }

        Box(modifier = overlayModifier) {
            overlay()
        }
    }
}
