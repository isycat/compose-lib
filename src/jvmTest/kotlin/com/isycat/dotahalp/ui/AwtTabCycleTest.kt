package com.isycat.dotahalp.ui

import com.isycat.compose.input.awtTabCycleMatchOrNull
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AwtTabCycleTest {

    private val sourceComponent: Component = object : Component() {}

    private fun keyEvent(
        id: Int,
        keyCode: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ): KeyEvent {
        val modifiers =
            (if (ctrl) InputEvent.CTRL_DOWN_MASK else 0) or
                (if (alt) InputEvent.ALT_DOWN_MASK else 0) or
                (if (shift) InputEvent.SHIFT_DOWN_MASK else 0) or
                (if (meta) InputEvent.META_DOWN_MASK else 0)

        return KeyEvent(
            sourceComponent,
            id,
            0L,
            modifiers,
            keyCode,
            KeyEvent.CHAR_UNDEFINED,
        )
    }

    private fun keyPressed(
        keyCode: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ): KeyEvent = keyEvent(
        id = KeyEvent.KEY_PRESSED,
        keyCode = keyCode,
        ctrl = ctrl,
        alt = alt,
        shift = shift,
        meta = meta,
    )

    private fun keyReleased(
        keyCode: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ): KeyEvent = keyEvent(
        id = KeyEvent.KEY_RELEASED,
        keyCode = keyCode,
        ctrl = ctrl,
        alt = alt,
        shift = shift,
        meta = meta,
    )

    @Test
    fun `ctrl tab cycles forward`() {
        assertEquals(1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_TAB, ctrl = true))?.delta)
    }

    @Test
    fun `ctrl shift tab cycles backward`() {
        assertEquals(-1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_TAB, ctrl = true, shift = true))?.delta)
    }

    @Test
    fun `meta tab cycles forward`() {
        assertEquals(1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_TAB, meta = true))?.delta)
    }

    @Test
    fun `ctrl alt left-right cycles`() {
        assertEquals(-1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_LEFT, ctrl = true, alt = true))?.delta)
        assertEquals(1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_RIGHT, ctrl = true, alt = true))?.delta)
    }

    @Test
    fun `ctrl alt l-r cycles`() {
        assertEquals(-1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_L, ctrl = true, alt = true))?.delta)
        assertEquals(1, awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_R, ctrl = true, alt = true))?.delta)
    }

    @Test
    fun `shortcut key release is consumed but does not cycle`() {
        assertEquals(null, awtTabCycleMatchOrNull(keyReleased(KeyEvent.VK_TAB, ctrl = true))?.delta)
        assertEquals(null, awtTabCycleMatchOrNull(keyReleased(KeyEvent.VK_LEFT, ctrl = true, alt = true))?.delta)
    }

    @Test
    fun `non matching keys return null`() {
        assertNull(awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_A, ctrl = true)))
        assertNull(awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_LEFT, alt = true)))
        assertNull(awtTabCycleMatchOrNull(keyPressed(KeyEvent.VK_LEFT, ctrl = true)))
    }
}
