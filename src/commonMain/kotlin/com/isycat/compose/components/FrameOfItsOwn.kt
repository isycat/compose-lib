package com.isycat.compose.components

import androidx.compose.runtime.withFrameNanos

/**
 * Waits for the next frame, so what follows runs in a frame of its own. An effect that scrolls or reveals starts while
 * the composition that asked for it is still being applied; a scroll there measures the list anew inside it, which the
 * runtime refuses ("pending composition has not been applied") and the recomposer stops, leaving the window as it was
 * (dittoCAD's elements panel on Wabeco, 2026-11-17). Every effect that scrolls or reveals calls this first.
 */
suspend fun inFrameOfItsOwn() {
    withFrameNanos { }
}
