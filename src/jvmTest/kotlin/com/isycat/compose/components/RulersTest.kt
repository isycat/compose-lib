package com.isycat.compose.components

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The square where the rulers meet takes each ruler's colour along the edge it shares with it. */
class RulersTest {
    // Opaque, with no edges or numbers, so only the fills show.
    private val colors =
        RulerColors(
            background = Color(0xFF101010),
            extent = Color(0xFF909090),
            edge = Color.Transparent,
            marker = Color.Transparent,
            label = Color.Transparent,
        )
    private val plain = colors.background
    private val banded = colors.extent

    /** The rulers at density 1, so the square is [THICKNESS] pixels, 0,0 at [origin] and a 100 by 100 extent. */
    private fun render(origin: Offset): PixelMap {
        val scene =
            ImageComposeScene(SIZE, SIZE, Density(1f)) {
                Rulers(origin, pxPerUnit = 1f, extent = Size(EXTENT, EXTENT), colors = colors)
            }
        return try {
            scene.render().toComposeImageBitmap().toPixelMap()
        } finally {
            scene.close()
        }
    }

    private fun assertColor(expected: Color, actual: Color, where: String) {
        val close =
            listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
                .all { (a, b) -> abs(a - b) <= TOLERANCE }
        assertTrue(close, "$where: expected $expected, was $actual")
    }

    /** The top ruler's start and the square's right edge, then the left ruler's start and the square's bottom edge. */
    private fun assertEdges(pixels: PixelMap, top: Color, left: Color) {
        assertColor(top, pixels[THICKNESS + 1, 2], "top ruler")
        assertColor(top, pixels[THICKNESS - 1, 2], "square's right edge")
        assertColor(left, pixels[2, THICKNESS + 1], "left ruler")
        assertColor(left, pixels[2, THICKNESS - 1], "square's bottom edge")
    }

    @Test
    fun bothRulersBelowZero() {
        assertEdges(render(Offset(60f, 60f)), top = plain, left = plain)
    }

    @Test
    fun bothRulersOverTheExtent() {
        assertEdges(render(Offset(-10f, -10f)), top = banded, left = banded)
    }

    @Test
    fun oneOfEach() {
        val pixels = render(Offset(-10f, 60f))
        assertEdges(pixels, top = banded, left = plain)
        assertEdges(render(Offset(60f, -10f)), top = plain, left = banded)

        // It blends between them on the way round.
        val middle = pixels[THICKNESS / 2, THICKNESS / 2].red
        assertTrue(middle > plain.red + TOLERANCE && middle < banded.red - TOLERANCE, "square's middle: $middle")
    }

    @Test
    fun numbersKeepAtMostTwoDecimals() {
        assertEquals(listOf("5", "2.5", "-0.25", "0", "1.33"), listOf(5.0, 2.5, -0.25, -0.001, 1.333).map(RulerDefaults::number))
    }

    private companion object {
        const val SIZE = 120
        const val THICKNESS = 18
        const val EXTENT = 100f
        const val TOLERANCE = 0.03f
    }
}
