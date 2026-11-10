package com.isycat.compose.ui

import androidx.compose.ui.input.pointer.PointerEvent

// The desktop's scroll is already in notches.
actual fun PointerEvent.wheelNotches(): Float = changes.first().scrollDelta.y

actual fun listenForWheelNotches() = Unit
