package com.isycat.compose.ui

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Isabella, 2026-11-03: "double clicking title does not count if mouse has not moved a single pixel". On Windows the
 * system handles the bar as the caption; this is what the window's hit test answers for each point.
 */
class CaptionHitTest {
    private val bar = Rect(0f, 0f, 1000f, 40f)
    private val tab = Rect(100f, 6f, 250f, 40f)
    private val button = Rect(900f, 0f, 1000f, 40f)

    private fun hit(x: Float, y: Float, maximized: Boolean = false) =
        captionHit(x, y, width = 1000f, caption = bar, clickable = listOf(tab, button), maximized = maximized, edge = 4f, corner = 8f)

    @Test
    fun `the bar's empty part is the caption`() {
        assertEquals(CustomTitleBar.HTCAPTION, hit(50f, 20f))
        assertEquals(CustomTitleBar.HTCAPTION, hit(500f, 20f))
    }

    @Test
    fun `tabs and buttons are the window's own`() {
        assertNull(hit(150f, 20f))
        assertNull(hit(950f, 20f))
    }

    @Test
    fun `below the bar is the window's own`() {
        assertNull(hit(500f, 300f))
    }

    @Test
    fun `the top edge resizes, its corners diagonally, unless maximised`() {
        assertEquals(CustomTitleBar.HTTOPLEFT, hit(2f, 1f))
        assertEquals(CustomTitleBar.HTTOP, hit(500f, 1f))
        assertEquals(CustomTitleBar.HTTOP, hit(150f, 1f), "over a tab too")
        assertEquals(CustomTitleBar.HTTOPRIGHT, hit(995f, 1f))
        assertEquals(CustomTitleBar.HTCAPTION, hit(500f, 1f, maximized = true))
    }

    @Test
    fun `without a bar reported, nothing is the caption`() {
        assertNull(captionHit(500f, 20f, 1000f, caption = null, clickable = emptyList(), maximized = false, edge = 4f, corner = 8f))
    }
}
