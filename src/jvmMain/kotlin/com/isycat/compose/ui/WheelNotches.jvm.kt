package com.isycat.compose.ui

import androidx.compose.ui.input.pointer.PointerEvent

// The desktop reports notches already.
actual fun PointerEvent.wheelNotches(): Float = changes.firstOrNull()?.scrollDelta?.y ?: 0f

actual fun listenForWheelNotches() = Unit
