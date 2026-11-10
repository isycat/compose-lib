@file:OptIn(ExperimentalWasmJsInterop::class)

package com.isycat.compose.web

/**
 * Makes a press on Compose's canvas count when the canvas doesn't hold the page's focus. Compose for the browser drops
 * a press that moves the page's focus onto its canvas: the first click after typing in a text field (whose backing
 * input then holds the focus), or after the page lost focus to anything else, does nothing, and only a second click
 * acts. Focusing the canvas as the press begins, before Compose hears of it, makes every press count. Call once, before
 * the first press.
 */
fun focusCanvasOnPress(): Unit = js(
    """{
        if (globalThis.composeLibFocusingPresses) return;
        globalThis.composeLibFocusingPresses = true;
        window.addEventListener('pointerdown', (e) => {
            const canvas = e.composedPath().find((node) => node instanceof HTMLCanvasElement);
            if (canvas && canvas.getRootNode().activeElement !== canvas) canvas.focus({ preventScroll: true });
        }, { capture: true });
    }""",
)
