@file:OptIn(ExperimentalWasmJsInterop::class)

package com.isycat.compose.ui

import androidx.compose.ui.input.pointer.PointerEvent

// Compose hands on the browser's own amounts without saying in what unit. The page's wheel events say: a listener that
// hears each one before Compose does keeps it in notches, at most one, for the event Compose is handling to read.
actual fun PointerEvent.wheelNotches(): Float = lastWheelNotches().toFloat()

actual fun listenForWheelNotches(): Unit = js(
    """{
        if (globalThis.composeLibWheel !== undefined) return;
        globalThis.composeLibWheel = 0;
        window.addEventListener('wheel', (e) => {
            const perNotch = e.deltaMode === 1 ? 3 : e.deltaMode === 2 ? 1 : 100;
            globalThis.composeLibWheel = Math.max(-1, Math.min(1, e.deltaY / perNotch));
        }, { capture: true, passive: true });
    }""",
)

private fun lastWheelNotches(): Double = js("globalThis.composeLibWheel ?? 0")
