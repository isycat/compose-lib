package com.isycat.compose.startup

import androidx.compose.ui.graphics.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupImagesTest {
    private val colors = SplashColors(Color(0xFF1A1624), Color(0xFF3A3248), Color(0xFF282236), Color(0xFFC9B1F1), Color(0xFFADA2C6))
    private val icon = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).apply { setRGB(4, 4, 0xFFFF0000.toInt()) }

    @Test
    fun theSplashIsRoundCorneredOpaqueInsideAndShowsItsNameInEachWordsColour() {
        val art = SplashArt(listOf(SplashWord("ditto", Color.White), SplashWord("CAD", Color(0xFFC9B1F1))), icon, colors, cornerRadius = 12f)
        val image = art.image(Startup.FIRST_STEP)
        assertEquals(SplashArt.WIDTH to SplashArt.HEIGHT, image.width to image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24, "outside the round corner is transparent")
        assertEquals(0xFF, image.getRGB(SplashArt.WIDTH / 2, 20) ushr 24, "inside is opaque")
        val pixels = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) and 0xFFFFFF } }.toSet()
        assertTrue(0xFFFFFF in pixels && 0xC9B1F1 in pixels, "both words are drawn in their own colours")
    }

    @Test
    fun anIconFileHoldsEachSizeAsA32BitBitmapWithItsAlpha() {
        val file = File.createTempFile("app", ".ico")
        try {
            val sizes = listOf(16, 32, 256)
            IconFile.write(sizes.map { BufferedImage(it, it, BufferedImage.TYPE_INT_ARGB) }, file)
            val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(listOf(0, 1, 3), listOf(bytes.getShort(0), bytes.getShort(2), bytes.getShort(4)).map(Short::toInt))
            val entries = sizes.indices.map { 6 + 16 * it }
            assertEquals(listOf(16, 32, 0), entries.map { bytes.get(it).toInt() and 0xFF }, "256 is written as 0")
            entries.forEach { entry ->
                val offset = bytes.getInt(entry + 12)
                assertEquals(32, bytes.getShort(entry + 6).toInt())
                assertEquals(40, bytes.getInt(offset), "each image is a bitmap with the standard header")
                assertEquals(32, bytes.getShort(offset + 14).toInt())
            }
            val last = entries.last()
            assertEquals(file.length(), (bytes.getInt(last + 12) + bytes.getInt(last + 8)).toLong(), "the images fill the file exactly")
        } finally {
            file.delete()
        }
    }
}
