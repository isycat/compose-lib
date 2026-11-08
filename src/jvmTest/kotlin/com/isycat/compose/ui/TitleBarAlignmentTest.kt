package com.isycat.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where the bar's parts sit up and down, measured on the pixels drawn at the screen scales Windows offers. */
class TitleBarAlignmentTest {
    private val bar = 0x1F2026

    private object StillWindow : TitleBarWindow {
        override val isMaximized = false

        override fun minimize() = Unit

        override fun toggleMaximized() = Unit

        override fun close() = Unit
    }

    private fun render(width: Int, height: Int, scale: Float): BufferedImage {
        val scene = ImageComposeScene(width, height, Density(scale)) {
            MaterialTheme {
                TabbedTitleBar(
                    tabs = emptyList(),
                    selectedTabIndex = -1,
                    onTabSelected = {},
                    // Upright capitals only, whose ink is spread evenly over their height.
                    title = "IHI",
                    appIcon = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    window = StillWindow,
                    menu = { _, _ -> },
                )
            }
        }
        try {
            val png = checkNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)).bytes
            return ImageIO.read(png.inputStream())
        } finally {
            scene.close()
        }
    }

    /** How far [rgb] is from the bar's colour: 0 for the bar, more the more a pixel is covered. */
    private fun cover(rgb: Int): Int = listOf(16, 8, 0).sumOf { abs(((rgb ushr it) and 0xFF) - ((bar ushr it) and 0xFF)) }

    private fun ink(rgb: Int): Boolean = cover(rgb) > INK

    @Test
    fun theIconTheTitlesCapitalsAndTheMenuShareTheBarsMiddleAtEveryScale() {
        val misplaced = listOf(1f, 1.25f, 1.5f, 1.75f, 2f).flatMap { scale -> misplaced(scale) }
        assertTrue(misplaced.isEmpty(), misplaced.joinToString("\n"))
    }

    /** What is off the bar's middle at [scale], said as a line each. */
    private fun misplaced(scale: Float): List<String> {
        val width = 600
        val height = (TitleBarDefaults.Height.value * scale).toInt()
        val image = render(width, height, scale)
        val middle = height / 2.0
        // Each part's columns, left to right: the red icon, then the title's letters, then the menu's lines, apart by gaps.
        val columns = (0 until width / 2).filter { x -> (0 until height).any { y -> ink(image.getRGB(x, y)) } }
        val groups = columns.fold(mutableListOf<MutableList<Int>>()) { found, x ->
            found.apply { if (isEmpty() || x - last().last() > GAP) add(mutableListOf(x)) else last().add(x) }
        }
        // Each part's middle is the centre of its ink, weighted by how much each pixel is covered, edges included.
        val centres = groups.take(3).map { group ->
            val weights = (0 until height).map { y -> group.sumOf { x -> cover(image.getRGB(x, y)) }.toDouble() }
            weights.withIndex().sumOf { (y, weight) -> (y + 0.5) * weight } / weights.sum()
        }
        return listOf("icon", "title's capitals", "menu").zip(centres).filter { (_, centre) -> abs(centre - middle) > HALF_PIXEL }
            .map { (part, centre) -> "at ${scale * 100} %, the $part's middle is at ${"%.2f".format(centre)} px, the bar's at $middle" }
    }

    private companion object {
        const val INK = 120
        const val GAP = 8
        const val HALF_PIXEL = 0.5
    }
}
