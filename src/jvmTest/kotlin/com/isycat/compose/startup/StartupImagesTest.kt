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
        assertEquals(SplashArt.WIDTH to SplashArt(emptyList(), colors, 12f).height, image.width to image.height)
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
                val columns = inside.filter { x -> (16 until SplashArt(emptyList(), colors, 12f).height - 64).any { y -> image.getRGB(x, y) != background } }
                val (first, last) = columns.first() to columns.last()
                // Centred by the glyphs' advances, as type is set, so the last letter's side bearing may show.
                assertTrue(kotlin.math.abs(first - (SplashArt.WIDTH - 1 - last)) <= 4, "${words.joinToString("") { it.text }}: drawn from $first to $last of ${SplashArt.WIDTH}")
                assertTrue(first >= 32 && last <= SplashArt.WIDTH - 32, "inside the margins: $first to $last")
            }
    }

    @Test
    fun aScreenBlendIsNeverDarkerThanEitherLayerAndLeavesWhiteWhite() {
        val under = listOf(0xFF1A1624.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())
        val icon = 0xFFBDB2F1.toInt()
        under.forEach { u ->
            val out = SplashArt.screen(u, icon)
            listOf(16, 8, 0).forEach { shift ->
                val (b, s, o) = Triple((u ushr shift) and 0xFF, (icon ushr shift) and 0xFF, (out ushr shift) and 0xFF)
                assertTrue(o >= maxOf(b, s) - 1, "channel $shift: $o from $b and $s")
            }
        }
        assertEquals(0xFFFFFFFF.toInt(), SplashArt.screen(0xFFFFFFFF.toInt(), icon), "white stays white")
        assertEquals(0xFF1A1624.toInt(), SplashArt.screen(0xFF1A1624.toInt(), 0x00BDB2F1), "a clear icon pixel changes nothing")
    }

    @Test
    fun theIconOverTheNamesEndIsBlendedOntoItAndTheTwoAreCentred() {
        val over = SplashArt(listOf(SplashWord("dittoCAD", Color.White)), colors, 12f, SplashLayout.OverNameEnd(across = -0.54f, up = 0.72f))
        assertEquals(480 to 175, over.width to over.height)
        val icon = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).apply { (0 until 8).forEach { x -> (0 until 8).forEach { y -> setRGB(x, y, 0xFF404040.toInt()) } } }
        val plain = over.image(null, "", 0f)
        val blended = over.image(icon, "", 0f)
        val changed = (0 until over.width).flatMap { x -> (0 until over.height).map { y -> x to y } }.filter { (x, y) -> plain.getRGB(x, y) != blended.getRGB(x, y) }
        assertTrue(changed.isNotEmpty(), "the icon is drawn")
        changed.forEach { (x, y) ->
            val (b, o) = plain.getRGB(x, y) to blended.getRGB(x, y)
            assertTrue(listOf(16, 8, 0).all { ((o ushr it) and 0xFF) >= ((b ushr it) and 0xFF) }, "screen only lightens, at $x, $y")
        }
        val white = (0 until over.width).flatMap { x -> (0 until over.height).map { y -> x to y } }.filter { (x, y) -> plain.getRGB(x, y) == 0xFFFFFFFF.toInt() }
        assertTrue(white.isNotEmpty(), "the name is drawn")
        val iconLeft = changed.minOf { it.first }
        val textLeft = white.minOf { it.first }
        val right = maxOf(changed.maxOf { it.first }, white.maxOf { it.first })
        assertTrue(iconLeft < white.maxOf { it.first }, "the icon starts over the name")
        assertTrue(kotlin.math.abs(textLeft - (over.width - 1 - right)) <= 4, "centred together: $textLeft to $right")
    }

    @Test
    fun theBarIsFilledOnlyAsFarAsItsProgressSays() {
        val accent = 0xC9B1F1
        val bar = { progress: Float -> art.image(null, "Opening", progress).let { image -> (32 until SplashArt.WIDTH - 32).count { x -> (image.getRGB(x, SplashArt(emptyList(), colors, 12f).height - 22) and 0xFFFFFF) == accent } } }
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
