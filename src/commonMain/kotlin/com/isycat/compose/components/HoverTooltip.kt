package com.isycat.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * A desktop hover tooltip that follows the cursor and adapts: it shows BELOW the cursor by default,
 * and flips ABOVE with a larger gap when there isn't room below (so it never sits under the cursor).
 *
 * Replaces `TooltipArea`'s placement, whose flipped-above case left too small a gap above the cursor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoverTooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Long = 350,
    content: @Composable () -> Unit
) {
    var hovering by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val gapBelow = with(density) { 18.dp.roundToPx() }
    val gapAbove = with(density) { 28.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { hovering = true }
            .onPointerEvent(PointerEventType.Move) { pointer = it.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) { hovering = false; show = false }
    ) {
        content()

        LaunchedEffect(hovering) {
            if (hovering) {
                delay(delayMillis)
                if (hovering) show = true
            }
        }

        if (show) {
            val provider = remember(pointer, gapBelow, gapAbove, margin) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val cx = anchorBounds.left + pointer.x.toInt()
                        val cy = anchorBounds.top + pointer.y.toInt()
                        var x = cx
                        if (x + popupContentSize.width > windowSize.width - margin) {
                            x = windowSize.width - margin - popupContentSize.width
                        }
                        if (x < margin) x = margin
                        val below = cy + gapBelow
                        val y = if (below + popupContentSize.height <= windowSize.height - margin) {
                            below
                        } else {
                            (cy - gapAbove - popupContentSize.height).coerceAtLeast(margin)
                        }
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
                tooltip()
            }
        }
    }
}
