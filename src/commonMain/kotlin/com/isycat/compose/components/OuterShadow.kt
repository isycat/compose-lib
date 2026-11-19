package com.isycat.compose.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.DropShadowPainter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset

/**
 * The shadow of something raised [elevation] above what is behind it, in [shape], drawn only outside that shape: never
 * under it, so a surface that is partly see-through shows what is behind it evenly to its very edge, with no shadow
 * showing through it as a lighter rim and a darker middle (owner, 2026-11-19, "the difference between edge and BG is not
 * right ... all over the UI"). Every raised surface takes its shadow from here, see-through or not, so all have one edge.
 */
fun Modifier.outerShadow(elevation: Dp, shape: Shape): Modifier = composed {
    val painter = remember(elevation, shape) {
        DropShadowPainter(shape, Shadow(radius = elevation * 2, color = SHADOW, offset = DpOffset(Dp(0f), elevation / 2)))
    }
    drawWithCache {
        val outside = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawBehind {
            clipPath(outside, ClipOp.Difference) { with(painter) { draw(size) } }
        }
    }
}

private val SHADOW = Color.Black.copy(alpha = 0.45f)
