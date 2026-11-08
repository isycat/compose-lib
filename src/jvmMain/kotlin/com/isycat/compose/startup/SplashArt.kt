package com.isycat.compose.startup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.isycat.compose.ui.AwtFonts
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** One word of an app's name on its card, in its colour: cardSTUDIO is "card" in the text colour and "STUDIO" in the accent. */
class SplashWord(val text: String, val color: Color)

/** A card's colours, from the app's theme: a step above its background, its outline, track, accent and quiet text. */
class SplashColors(val background: Color, val border: Color, val track: Color, val accent: Color, val muted: Color)

/**
 * The face of an app's opening card ([OpeningCard]), laid out as cardSTUDIO's splash and moonshine's opening card: the
 * icon and the name centred together, and along the foot the step it is on over a thin bar of how far it has come. Drawn with Java2D,
 * so it shows before Compose is made; text is the bundled Roboto, so it looks the same on every machine; the corners
 * are [cornerRadius] round and clear outside.
 */
class SplashArt(private val name: List<SplashWord>, private val colors: SplashColors, private val cornerRadius: Float) {
    /** The whole face on [g], with [icon] where it is made yet, saying [status], its bar [progress] (0 to 1) of the way. */
    fun paint(g: Graphics2D, icon: Image?, status: String, progress: Float) {
        smooth(g)
        val arc = cornerRadius * 2
        g.color = colors.background.awt
        g.fill(RoundRectangle2D.Float(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), arc, arc))
        g.color = colors.border.awt
        // Half a pixel in, so the line's whole width is inside the outline.
        g.draw(RoundRectangle2D.Float(HALF_PIXEL, HALF_PIXEL, WIDTH - 1f, HEIGHT - 1f, arc - 1, arc - 1))
        // The icon and the name side by side, centred together; a name too long for the room is drawn smaller.
        val room = WIDTH - 2 * MARGIN - ICON_SIZE - GAP
        val full = AwtFonts.roboto(NAME_SIZE)
        val wide = name.sumOf { g.getFontMetrics(full).stringWidth(it.text) }
        g.font = if (wide <= room) full else full.deriveFont(NAME_SIZE * room / wide)
        val nameWidth = name.sumOf { g.fontMetrics.stringWidth(it.text) }
        val left = (WIDTH - ICON_SIZE - GAP - nameWidth) / 2
        val top = (HEIGHT - STATUS_HEIGHT - ICON_SIZE) / 2
        icon?.let { g.drawImage(it, left, top, ICON_SIZE, ICON_SIZE, null) }
        val baseline = top + (ICON_SIZE + g.fontMetrics.ascent - g.fontMetrics.descent) / 2
        name.fold(left + ICON_SIZE + GAP) { left, word ->
            g.color = word.color.awt
            g.drawString(word.text, left, baseline)
            left + g.fontMetrics.stringWidth(word.text)
        }
        val strip = HEIGHT - STATUS_HEIGHT
        g.font = AwtFonts.roboto(STATUS_SIZE)
        g.color = colors.muted.awt
        g.drawString(status, MARGIN, strip + STATUS_BASELINE)
        val bar = RoundRectangle2D.Float(MARGIN.toFloat(), (strip + BAR_TOP).toFloat(), (WIDTH - 2 * MARGIN).toFloat(), BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT)
        g.color = colors.track.awt
        g.fill(bar)
        if (progress > 0f) {
            g.color = colors.accent.awt
            g.fill(RoundRectangle2D.Float(bar.x, bar.y, maxOf(BAR_HEIGHT, bar.width * progress.coerceAtMost(1f)), bar.height, bar.height, bar.height))
        }
    }

    /** The face as an image, as the card shows it, for checks. */
    fun image(icon: Image?, status: String, progress: Float): BufferedImage = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB).also { image ->
        val g = image.createGraphics()
        try {
            paint(g, icon, status, progress)
        } finally {
            g.dispose()
        }
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

        /** How large the icon is drawn, in the card's pixels. */
        const val ICON_SIZE = 96
        private const val MARGIN = 32
        private const val GAP = 20
        private const val NAME_SIZE = 44f
        private const val STATUS_HEIGHT = 64
        private const val STATUS_SIZE = 14f
        private const val STATUS_BASELINE = 26
        private const val BAR_TOP = 40
        private const val BAR_HEIGHT = 4f
        private const val HALF_PIXEL = 0.5f
    }
}
