package com.isycat.compose.ui

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AwtFontsTest {
    @Test
    fun robotoIsTheBundledFontAtTheSizeAsked() {
        val font = AwtFonts.roboto(20f)
        assertTrue(font.family.startsWith("Roboto"), "is Roboto, not a system font (${font.family})")
        assertEquals(20f, font.size2D)
        assertTrue(font.canDisplayUpTo("cardSTUDIO Starting…") == -1, "draws every letter")
    }

    @Test
    fun robotoDrawsText() {
        val image = BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.font = AwtFonts.roboto(24f)
        g.drawString("Roboto", 4, 30)
        g.dispose()
        val inked = (0 until image.width).sumOf { x -> (0 until image.height).count { y -> image.getRGB(x, y) and 0xFFFFFF != 0 } }
        assertTrue(inked > 100, "some text was drawn ($inked pixels)")
    }
}
