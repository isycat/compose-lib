package com.isycat.compose.startup

import androidx.compose.ui.graphics.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartupImagesTest {
    private val colors = SplashColors(Color(0xFF1A1624), Color(0xFF3A3248), Color(0xFF282236), Color(0xFFC9B1F1), Color(0xFFADA2C6))
    private val art = SplashArt(listOf(SplashWord("ditto", Color.White), SplashWord("CAD", Color(0xFFC9B1F1))), colors, cornerRadius = 12f)

    private fun opaque(image: BufferedImage) = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) and 0xFFFFFF } }.toSet()

    @Test
    fun theCardIsRoundCorneredOpaqueInsideAndShowsItsNameInEachWordsColour() {
        val image = art.image(icon = null, status = "Opening", progress = 0f)
        assertEquals(SplashArt.WIDTH to SplashArt.HEIGHT, image.width to image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24, "outside the round corner is clear")
        assertEquals(0xFF, image.getRGB(SplashArt.WIDTH / 2, 20) ushr 24, "inside is opaque")
        assertTrue(0xFFFFFF in opaque(image), "the first word is drawn in its colour")
    }

    @Test
    fun theIconAndTheNameAreCentredTogetherWhateverTheNamesLength() {
        val icon = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).apply { (0 until 8).forEach { x -> (0 until 8).forEach { y -> setRGB(x, y, 0xFF00FF00.toInt()) } } }
        listOf(listOf(SplashWord("ditto", Color.White), SplashWord("CAD", Color.White)), listOf(SplashWord("a much longer app name than fits", Color.White)))
            .forEach { words ->
                val image = SplashArt(words, colors, cornerRadius = 12f).image(icon, "", 0f)
                // Every column the icon or the name touches, anti-aliased edges included; the border and the clear round
                // corners are left out.
                val background = 0xFF1A1624.toInt()
                val inside = 4 until SplashArt.WIDTH - 4
                val columns = inside.filter { x -> (16 until SplashArt.HEIGHT - 64).any { y -> image.getRGB(x, y) != background } }
                val (first, last) = columns.first() to columns.last()
                // Centred by the glyphs' advances, as type is set, so the last letter's side bearing may show.
                assertTrue(kotlin.math.abs(first - (SplashArt.WIDTH - 1 - last)) <= 4, "${words.joinToString("") { it.text }}: drawn from $first to $last of ${SplashArt.WIDTH}")
                assertTrue(first >= 32 && last <= SplashArt.WIDTH - 32, "inside the margins: $first to $last")
            }
    }

    @Test
    fun theBarIsFilledOnlyAsFarAsItsProgressSays() {
        val accent = 0xC9B1F1
        val bar = { progress: Float -> art.image(null, "Opening", progress).let { image -> (32 until SplashArt.WIDTH - 32).count { x -> (image.getRGB(x, SplashArt.HEIGHT - 22) and 0xFFFFFF) == accent } } }
        assertEquals(0, bar(0f), "nothing measured, nothing filled")
        assertTrue(bar(0.5f) in 200..216, "half the bar's 416 pixels: ${bar(0.5f)}")
    }

    @Test
    fun aStartSharesTheBarByWhatEachStepTookLastTimeOrEvenlyWhenNotKnown() {
        val file = Files.createTempFile("opening", ".timings")
        try {
            val steps = listOf("Reading", "Opening the window")
            assertNull(OpeningCard.expected(steps, file), "an empty file names no step")
            Files.writeString(file, "Reading\t300\nOpening the window\t100\n")
            assertContentEquals(longArrayOf(300, 100), OpeningCard.expected(steps, file))
            assertContentEquals(doubleArrayOf(0.75, 0.25), OpeningCard.shares(2, OpeningCard.expected(steps, file)))
            assertContentEquals(doubleArrayOf(0.5, 0.5), OpeningCard.shares(2, null))
            assertNull(OpeningCard.expected(steps + "Another", file), "timings that miss a step are not used")
        } finally {
            Files.deleteIfExists(file)
        }
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
