package com.isycat.compose.ui

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * How far this scroll turns the wheel, in notches as the desktop counts them, down being positive: what a wheel zoom
 * and the like step by. The browser reports pixels, lines or pages instead, a hundred or so to a notch, and a trackpad
 * many small amounts; there it's made notches, at most one an event, so a notch is a step and a trackpad glides. Call
 * [listenForWheelNotches] before the first scroll.
 */
expect fun PointerEvent.wheelNotches(): Float

/** Gets [wheelNotches] ready before the first scroll, where it has to listen for them (the browser); once is enough. */
expect fun listenForWheelNotches()
