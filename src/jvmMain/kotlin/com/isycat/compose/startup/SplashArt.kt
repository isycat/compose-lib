package com.isycat.compose.startup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.isycat.compose.ui.AwtFonts
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** One word of an app's name on its splash, in its colour: cardSTUDIO is "card" in the text colour and "STUDIO" in the accent. */
class SplashWord(val text: String, val color: Color)

/** A splash's colours, from the app's theme: a step above its background, its outline, track, accent and quiet text. */
class SplashColors(val background: Color, val border: Color, val track: Color, val accent: Color, val muted: Color)

/**
 * An app's splash screen, as cardSTUDIO's: its icon and name over a strip that says what is happening, with a bar that
 * fills as it goes. It is drawn with Java2D so it can be both written out as the image the JVM shows the moment it
 * starts (`-splash:`, before any of the app's code runs, see [write]) and drawn over at runtime ([Startup]). Text is
 * the bundled Roboto, so it looks the same whichever machine built it; the corners are [cornerRadius] round and
 * transparent outside.
 */
class SplashArt(
    private val name: List<SplashWord>,
    private val icon: BufferedImage?,
    private val colors: SplashColors,
    private val cornerRadius: Float,
) {
    /** The strip along the bottom where what is happening is written, redrawn as it changes. */
    val statusStrip = Rectangle(0, HEIGHT - STATUS_HEIGHT, WIDTH, STATUS_HEIGHT)

    private val outline get() = RoundRectangle2D.Float(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), cornerRadius * 2, cornerRadius * 2)

    /** The whole splash, saying [status], on a transparent [g] that is left untouched outside the round corners. */
    fun paint(g: Graphics2D, status: String) {
        smooth(g)
        g.color = colors.background.awt
        g.fill(outline)
        g.color = colors.border.awt
        // Half a pixel in, so the line's whole width is inside the outline.
        val arc = cornerRadius * 2 - 1
        g.draw(RoundRectangle2D.Float(HALF_PIXEL, HALF_PIXEL, WIDTH - 1f, HEIGHT - 1f, arc, arc))
        val top = (HEIGHT - STATUS_HEIGHT - ICON_SIZE) / 2
        icon?.let { g.drawImage(it, MARGIN, top, ICON_SIZE, ICON_SIZE, null) }
        g.font = AwtFonts.roboto(NAME_SIZE)
        val baseline = top + (ICON_SIZE + g.fontMetrics.ascent - g.fontMetrics.descent) / 2
        name.fold(MARGIN + ICON_SIZE + GAP) { left, word ->
            g.color = word.color.awt
            g.drawString(word.text, left, baseline)
            left + g.fontMetrics.stringWidth(word.text)
        }
        paintStatus(g, status, progress = null)
    }

    /**
     * Just the status strip: [status], and how far along it is when [progress] (0 to 1) is known. Its background stays
     * inside the round corners and the border, so it never squares them off.
     */
    fun paintStatus(g: Graphics2D, status: String, progress: Float?) {
        smooth(g)
        val strip = statusStrip
        g.color = colors.background.awt
        val inside = Area(RoundRectangle2D.Float(1f, 1f, WIDTH - 2f, HEIGHT - 2f, cornerRadius * 2 - 2, cornerRadius * 2 - 2))
        inside.intersect(Area(Rectangle(strip.x, strip.y, strip.width, strip.height)))
        g.fill(inside)
        g.font = AwtFonts.roboto(STATUS_SIZE)
        g.color = colors.muted.awt
        g.drawString(status, MARGIN, strip.y + STATUS_BASELINE)
        val bar = RoundRectangle2D.Float(MARGIN.toFloat(), (strip.y + BAR_TOP).toFloat(), (WIDTH - 2 * MARGIN).toFloat(), BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT)
        g.color = colors.track.awt
        g.fill(bar)
        if (progress != null) {
            g.color = colors.accent.awt
            g.fill(RoundRectangle2D.Float(bar.x, bar.y, bar.width * progress.coerceIn(0f, 1f), bar.height, bar.height, bar.height))
        }
    }

    /** The splash as an image, saying [status]. */
    fun image(status: String): BufferedImage = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB).also { image ->
        val g = image.createGraphics()
        try {
            paint(g, status)
        } finally {
            g.dispose()
        }
    }

    /** Writes the splash, saying [status], as the PNG the JVM shows with `-splash:` the moment it starts. */
    fun write(file: File, status: String = Startup.FIRST_STEP) {
        file.parentFile?.mkdirs()
        check(ImageIO.write(image(status), "png", file)) { "No PNG writer" }
    }

    private fun smooth(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    private val Color.awt get() = java.awt.Color(toArgb(), true)

    companion object {
        const val WIDTH = 480
        const val HEIGHT = 260
        private const val MARGIN = 32
        private const val GAP = 20
        private const val ICON_SIZE = 96
        private const val NAME_SIZE = 44f
        private const val STATUS_HEIGHT = 64
        private const val STATUS_SIZE = 14f
        private const val STATUS_BASELINE = 26
        private const val BAR_TOP = 40
        private const val BAR_HEIGHT = 4f
        private const val HALF_PIXEL = 0.5f
    }
}
