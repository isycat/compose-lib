package com.isycat.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The colours of [Rulers]. Each is drawn over whatever the rulers lie on, so they can be see-through. */
@Immutable
data class RulerColors(
    /** The rulers themselves. */
    val background: Color,
    /** The band over the rulers' extent, from 0 to its end, over [background]. */
    val extent: Color,
    /** The rulers' inner edges. */
    val edge: Color,
    /** The pointer's mark. */
    val marker: Color,
    /** The ticks and numbers; below zero they're fainter. */
    val label: Color,
)

object RulerDefaults {
    /** How thick the rulers are. */
    val Thickness: Dp = 18.dp

    /** Spacings to choose from between numbered ticks, in units: the first that leaves room for a number is used. */
    val LabelSteps: List<Double> = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)

    /** Dark and see-through over what they measure, from the theme. */
    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.background.copy(alpha = SEE_THROUGH),
        extent: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = SEE_THROUGH),
        edge: Color = MaterialTheme.colorScheme.outlineVariant,
        marker: Color = MaterialTheme.colorScheme.primary,
        label: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ) = RulerColors(background, extent, edge, marker, label)

    /** The numbers' text, coloured with [RulerColors.label]. */
    val LabelStyle: TextStyle = TextStyle(fontSize = 9.sp)

    /** [value] to two decimal places at most, without trailing zeros: 5, 2.5, -0.25. */
    fun number(value: Double): String {
        val hundredths = (value * HUNDRED).roundToLong()
        val sign = if (hundredths < 0) "-" else ""
        val whole = abs(hundredths) / HUNDRED.toLong()
        val fraction = abs(hundredths) % HUNDRED.toLong()
        return when {
            fraction == 0L -> "$sign$whole"
            fraction % TEN == 0L -> "$sign$whole.${fraction / TEN}"
            else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    private const val SEE_THROUGH = 0.75f
    private const val HUNDRED = 100.0
    private const val TEN = 10L
}

/**
 * Rulers along the top and left edges of their bounds, as in a design tool, measuring in units of [pxPerUnit] pixels
 * from 0,0 at [origin] (in these bounds' pixels), so they follow whatever they measure as it's panned and zoomed. The
 * span from 0 to [extent] (in units: a page's size, say) is a lighter band, and [pointer], in these bounds' pixels, is
 * marked on both. Below zero the numbers are fainter.
 *
 * The square where the two meet blends from one ruler's colour to the other's, so each of its edges matches the ruler
 * it touches, banded or not. [label] writes the numbers.
 */
@Composable
fun Rulers(
    origin: Offset,
    pxPerUnit: Float,
    modifier: Modifier = Modifier,
    extent: Size = Size.Zero,
    pointer: Offset? = null,
    thickness: Dp = RulerDefaults.Thickness,
    colors: RulerColors = RulerDefaults.colors(),
    textStyle: TextStyle = RulerDefaults.LabelStyle,
    labelSteps: List<Double> = RulerDefaults.LabelSteps,
    label: (Double) -> String = RulerDefaults::number,
) {
    val measurer = rememberTextMeasurer()
    val scale = Scale(origin, pxPerUnit, extent)
    val text = textStyle.copy(color = colors.label)
    Box(modifier.fillMaxSize()) {
        // Clipped, so a number near the start doesn't spill into the corner.
        Canvas(Modifier.fillMaxWidth().height(thickness).padding(start = thickness).clipToBounds()) {
            Ruler(this, scale, horizontal = true, colors, measurer, text, labelSteps, label).draw(pointer)
        }
        Canvas(Modifier.fillMaxHeight().width(thickness).padding(top = thickness).clipToBounds()) {
            Ruler(this, scale, horizontal = false, colors, measurer, text, labelSteps, label).draw(pointer)
        }
        Canvas(Modifier.size(thickness).align(Alignment.TopStart)) {
            drawCorner(scale, colors)
        }
    }
}

/** Where the rulers' 0,0 is, how many pixels a unit is, and the banded span from 0, in units. */
private class Scale(val origin: Offset, val pxPerUnit: Float, val extent: Size) {
    /** Whether [px], a position across (or with [horizontal] false, down) the bounds, is within the band. */
    fun banded(px: Float, horizontal: Boolean): Boolean {
        val start = if (horizontal) origin.x else origin.y
        val length = (if (horizontal) extent.width else extent.height) * pxPerUnit
        return length > 0f && px >= start && px <= start + length
    }
}

/**
 * The square where the rulers meet: a sweep around its inner corner from the left ruler's colour where it starts (along
 * the square's bottom edge) to the top ruler's (along its right edge), so each edge matches the ruler beside it.
 */
private fun DrawScope.drawCorner(scale: Scale, colors: RulerColors) {
    // Each ruler's colour where it starts, beside the square.
    fun startColor(start: Float, horizontal: Boolean) =
        if (scale.banded(start, horizontal)) colors.extent.compositeOver(colors.background) else colors.background
    val top = startColor(size.width, horizontal = true)
    val left = startColor(size.height, horizontal = false)
    // A sweep starts pointing right and turns clockwise: half a turn points left, along the bottom edge, and three
    // quarters points up, along the right edge.
    drawRect(
        Brush.sweepGradient(0f to left, LEFT_TURN to left, UP_TURN to top, 1f to top, center = Offset(size.width, size.height)),
    )
    drawLine(colors.edge, Offset(size.width - HALF_PIXEL, 0f), Offset(size.width - HALF_PIXEL, size.height))
    drawLine(colors.edge, Offset(0f, size.height - HALF_PIXEL), Offset(size.width, size.height - HALF_PIXEL))
}

/**
 * One ruler, drawn into [scope]. It works in the bounds' coordinates shifted by the ruler's own offset (the other
 * ruler's thickness), so a mark sits exactly level with the point it measures.
 */
private class Ruler(
    private val scope: DrawScope,
    private val scale: Scale,
    private val horizontal: Boolean,
    private val colors: RulerColors,
    private val measurer: TextMeasurer,
    private val text: TextStyle,
    private val labelSteps: List<Double>,
    private val label: (Double) -> String,
) {
    private val inset = if (horizontal) scope.size.height else scope.size.width
    private val length = if (horizontal) scope.size.width else scope.size.height
    private val depth = inset
    private val perUnit = scale.pxPerUnit
    private val zero = (if (horizontal) scale.origin.x else scale.origin.y) - inset

    /** Position along this ruler of [value] units from 0. */
    private fun at(value: Double) = zero + (value * perUnit).toFloat()

    private fun line(along: Float, from: Float, to: Float, color: Color) {
        if (horizontal) {
            scope.drawLine(color, Offset(along, from), Offset(along, to))
        } else {
            scope.drawLine(color, Offset(from, along), Offset(to, along))
        }
    }

    private fun band(start: Float, end: Float, color: Color) {
        val from = start.coerceIn(0f, length)
        val to = end.coerceIn(0f, length)
        if (to <= from) return
        if (horizontal) {
            scope.drawRect(color, Offset(from, 0f), Size(to - from, depth))
        } else {
            scope.drawRect(color, Offset(0f, from), Size(depth, to - from))
        }
    }

    /** The ruler with [pointer], in the bounds' pixels, marked on it. */
    fun draw(pointer: Offset?) {
        scope.drawRect(colors.background)
        val extent = (if (horizontal) scale.extent.width else scale.extent.height).toDouble()
        if (extent > 0.0) band(at(0.0), at(extent), colors.extent)
        // The ruler's edge against what it measures.
        if (horizontal) {
            scope.drawLine(colors.edge, Offset(0f, depth - HALF_PIXEL), Offset(length, depth - HALF_PIXEL))
        } else {
            scope.drawLine(colors.edge, Offset(depth - HALF_PIXEL, 0f), Offset(depth - HALF_PIXEL, length))
        }
        if (perUnit > 0f) ticks()
        pointer?.let {
            val pos = (if (horizontal) it.x else it.y) - inset
            if (pos in 0f..length) line(pos, 0f, depth, colors.marker)
        }
    }

    /** Numbered ticks as close as their numbers allow, with finer ticks between them where there's room. */
    private fun ticks() {
        val minLabelGap = with(scope) { MinLabelGap.toPx() }
        val minTickGap = with(scope) { MinTickGap.toPx() }
        val labelStep = labelSteps.firstOrNull { it * perUnit >= minLabelGap } ?: labelSteps.last()
        val tickStep = TickDivisions.map { labelStep / it }.firstOrNull { it * perUnit >= minTickGap } ?: labelStep
        val first = floor(-zero / perUnit / tickStep).toLong()
        val last = ceil((length - zero) / perUnit / tickStep).toLong()
        for (i in first..last) {
            val value = i * tickStep
            val pos = at(value)
            val major = isMultiple(value, labelStep)
            val half = !major && isMultiple(value, labelStep / 2)
            val ink = text.color.let { if (value < 0) it.copy(alpha = it.alpha * NEGATIVE_ALPHA) else it }
            val tick =
                when {
                    major -> MAJOR_TICK
                    half -> HALF_TICK
                    else -> MINOR_TICK
                } * depth
            line(pos, depth - tick, depth, ink.copy(alpha = ink.alpha * if (major) 1f else MINOR_ALPHA))
            if (major) label(value, pos, ink)
        }
    }

    private fun label(value: Double, pos: Float, color: Color) {
        val layout = measurer.measure(label(value), text.copy(color = color))
        val pad = with(scope) { 2.dp.toPx() }
        if (horizontal) {
            scope.drawText(layout, topLeft = Offset(pos + pad, pad / 2))
        } else {
            // Turned to read bottom to top, ending just above the tick, like most design tools.
            val anchor = Offset(pad / 2, pos - pad)
            scope.rotate(UPRIGHT_DEGREES, pivot = anchor) { drawText(layout, topLeft = anchor) }
        }
    }
}

private fun isMultiple(value: Double, step: Double): Boolean {
    val ratio = value / step
    return abs(ratio - ratio.roundToLong()) < MULTIPLE_TOLERANCE
}

private val MinLabelGap = 44.dp
private val MinTickGap = 5.dp

// Finer ticks between numbers: tenths, fifths or halves of the number step, whichever leaves room.
private val TickDivisions = listOf(10, 5, 2)

// Tick lengths as shares of the ruler's thickness, and how faint the unnumbered ones are.
private const val MAJOR_TICK = 0.55f
private const val HALF_TICK = 0.35f
private const val MINOR_TICK = 0.2f
private const val MINOR_ALPHA = 0.7f
private const val UPRIGHT_DEGREES = -90f
private const val NEGATIVE_ALPHA = 0.4f
private const val MULTIPLE_TOLERANCE = 1e-6
private const val LEFT_TURN = 0.5f
private const val UP_TURN = 0.75f

// Hairlines sit on pixel centres so they stay one pixel wide.
private const val HALF_PIXEL = 0.5f
