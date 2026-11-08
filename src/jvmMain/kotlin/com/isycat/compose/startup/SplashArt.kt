package com.isycat.compose.startup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.isycat.compose.ui.AwtFonts
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One word of an app's name on its card, in its colour: cardSTUDIO is "card" in the text colour and "STUDIO" in the accent. */
class SplashWord(val text: String, val color: Color)

/** A card's colours, from the app's theme: a step above its background, its outline, track, accent and quiet text. */
class SplashColors(val background: Color, val border: Color, val track: Color, val accent: Color, val muted: Color)

/** Where an opening card puts the app's icon against its name, and how tall that makes the card. */
sealed interface SplashLayout {
    val height: Int

    /** cardSTUDIO's and moonshine's: the icon beside the name, the two centred together. */
    data object Beside : SplashLayout {
        override val height = 260
    }

    /**
     * The icon laid over the end of the name and blended onto it as a screen blends, so the letters it covers show
     * lighter through it: its box starts [across] icon sizes from the name's end (negative is back over the name), and
     * its top stands [up] icon sizes above the name's baseline. The name and the icon are centred together.
     */
    class OverNameEnd(val across: Float, val up: Float) : SplashLayout {
        override val height = 175
    }
}

/**
 * The face of an app's opening card ([OpeningCard]): the icon and the name as [layout] places them, and along the foot
 * the step it is on over a thin bar of how far it has come. Drawn with Java2D, so it shows before Compose is made; text
 * is the bundled Roboto, so it looks the same on every machine; the corners are [cornerRadius] round and clear outside.
 */
class SplashArt(
    private val name: List<SplashWord>,
    private val colors: SplashColors,
    private val cornerRadius: Float,
    private val layout: SplashLayout = SplashLayout.Beside,
) {
    val width = WIDTH
    val height = layout.height

    // The icon blended over the name, drawn once per icon and screen scale: the card redraws it on every tick.
    private var blended: Triple<Image, Double, BufferedImage>? = null

    /** The whole face on [g], with [icon] where it is made yet, saying [status], its bar [progress] (0 to 1) of the way. */
    fun paint(g: Graphics2D, icon: Image?, status: String, progress: Float) {
        smooth(g)
        when (layout) {
            SplashLayout.Beside -> {
                paintCard(g)
                paintBeside(g, icon)
            }
            is SplashLayout.OverNameEnd -> if (icon == null) {
                paintCard(g)
                paintNameOnly(g, layout)
            } else {
                g.drawImage(blendedLayer(icon, g.transform.scaleX, layout), 0, 0, width, height, null)
            }
        }
        paintStatus(g, status, progress)
    }

    /** The face as an image, as the card shows it, for checks. */
    fun image(icon: Image?, status: String, progress: Float): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
        val g = image.createGraphics()
        try {
            paint(g, icon, status, progress)
        } finally {
            g.dispose()
        }
    }

    private fun paintCard(g: Graphics2D) {
        val arc = cornerRadius * 2
        g.color = colors.background.awt
        g.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
        g.color = colors.border.awt
        // Half a pixel in, so the line's whole width is inside the outline.
        g.draw(RoundRectangle2D.Float(HALF_PIXEL, HALF_PIXEL, width - 1f, height - 1f, arc - 1, arc - 1))
    }

    /** The icon and the name side by side, centred together; a name too long for the room is drawn smaller. */
    private fun paintBeside(g: Graphics2D, icon: Image?) {
        val nameWidth = fitName(g, WIDTH - 2 * MARGIN - ICON_SIZE - GAP)
        val left = (WIDTH - ICON_SIZE - GAP - nameWidth) / 2
        val top = (height - STATUS_HEIGHT - ICON_SIZE) / 2
        icon?.let { g.drawImage(it, left, top, ICON_SIZE, ICON_SIZE, null) }
        drawName(g, left + ICON_SIZE + GAP, top + (ICON_SIZE + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    }

    /** Where the name starts and its baseline, and where the icon's box stands, for [layout]. */
    private class Placement(val nameLeft: Int, val baseline: Int, val iconLeft: Int, val iconTop: Int)

    private fun place(g: Graphics2D, layout: SplashLayout.OverNameEnd): Placement {
        val beyond = ((layout.across + 1) * ICON_SIZE).roundToInt().coerceAtLeast(0)
        val nameWidth = fitName(g, WIDTH - 2 * MARGIN - beyond)
        val nameLeft = (WIDTH - nameWidth - beyond) / 2
        // The icon's box is centred between the card's top and the top of the status line.
        val iconTop = (height - STATUS_HEIGHT + STATUS_BASELINE - STATUS_CAP - ICON_SIZE) / 2
        val baseline = iconTop + (layout.up * ICON_SIZE).roundToInt()
        return Placement(nameLeft, baseline, nameLeft + nameWidth + (layout.across * ICON_SIZE).roundToInt(), iconTop)
    }

    private fun paintNameOnly(g: Graphics2D, layout: SplashLayout.OverNameEnd) {
        val placement = place(g, layout)
        drawName(g, placement.nameLeft, placement.baseline)
    }

    /**
     * The card with its name, and [icon] blended over it as a screen blends, at the screen's [scale] so it is as sharp
     * as the screen: each channel is 1 − (1 − under)(1 − icon), weighted by the icon's alpha.
     */
    private fun blendedLayer(icon: Image, scale: Double, layout: SplashLayout.OverNameEnd): BufferedImage {
        blended?.takeIf { (drawn, at) -> drawn === icon && at == scale }?.let { return it.third }
        val layer = BufferedImage(ceil(width * scale).toInt(), ceil(height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = layer.createGraphics()
        val placement = try {
            g.scale(scale, scale)
            smooth(g)
            paintCard(g)
            place(g, layout).also { drawName(g, it.nameLeft, it.baseline) }
        } finally {
            g.dispose()
        }
        val size = (ICON_SIZE * scale).roundToInt()
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { image ->
            val ig = image.createGraphics()
            try {
                smooth(ig)
                ig.drawImage(icon, 0, 0, size, size, null)
            } finally {
                ig.dispose()
            }
        }
        val left = (placement.iconLeft * scale).roundToInt()
        val top = (placement.iconTop * scale).roundToInt()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val (lx, ly) = left + x to top + y
                if (lx !in 0 until layer.width || ly !in 0 until layer.height) continue
                layer.setRGB(lx, ly, screen(layer.getRGB(lx, ly), scaled.getRGB(x, y)))
            }
        }
        blended = Triple(icon, scale, layer)
        return layer
    }

    private fun fitName(g: Graphics2D, room: Int): Int {
        val full = AwtFonts.roboto(NAME_SIZE)
        val wide = name.sumOf { g.getFontMetrics(full).stringWidth(it.text) }
        g.font = if (wide <= room) full else full.deriveFont(NAME_SIZE * room / wide)
        return name.sumOf { g.fontMetrics.stringWidth(it.text) }
    }

    private fun drawName(g: Graphics2D, left: Int, baseline: Int) {
        name.fold(left) { at, word ->
            g.color = word.color.awt
            g.drawString(word.text, at, baseline)
            at + g.fontMetrics.stringWidth(word.text)
        }
    }

    private fun paintStatus(g: Graphics2D, status: String, progress: Float) {
        val strip = height - STATUS_HEIGHT
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

        /** How large the icon is drawn, in the card's pixels. */
        const val ICON_SIZE = 96
        private const val MARGIN = 32
        private const val GAP = 20
        private const val NAME_SIZE = 44f
        private const val STATUS_HEIGHT = 64
        private const val STATUS_SIZE = 14f
        private const val STATUS_BASELINE = 26

        // The status line's capital height in Roboto at its size, so space is measured to the letters' tops.
        private const val STATUS_CAP = 12
        private const val BAR_TOP = 40
        private const val BAR_HEIGHT = 4f
        private const val HALF_PIXEL = 0.5f

        /** [icon] (ARGB) over [under] (ARGB) as a screen blends, weighted by the icon's alpha; [under]'s alpha is kept. */
        fun screen(under: Int, icon: Int): Int {
            val alpha = (icon ushr 24) / 255.0
            if (alpha == 0.0) return under
            fun channel(shift: Int): Int {
                val b = (under ushr shift) and 0xFF
                val s = (icon ushr shift) and 0xFF
                val screened = 255 - (255 - b) * (255 - s) / 255.0
                return (b + (screened - b) * alpha).roundToInt().coerceIn(0, 255)
            }
            return (under and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
