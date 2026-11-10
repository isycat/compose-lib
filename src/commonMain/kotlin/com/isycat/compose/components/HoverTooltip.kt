package com.isycat.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A desktop hover tooltip that follows the cursor and adapts: it shows BELOW the cursor by default,
 * and flips ABOVE with a larger gap when there isn't room below (so it never sits under the cursor).
 *
 * It closes when its content leaves the screen ([LocalOnScreen]). A press on the content closes it, or stops it opening,
 * until the pointer leaves and comes back, as native tooltips do: one left open after a click would take the next click
 * below it.
 *
 * The first tooltip waits [delayMillis]; while one is open, or for [WARM_MILLIS] after it closed as the pointer left, the
 * next shows at once, as native toolbars' do, so running along a row of buttons names each as it is reached.
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
    var pressed by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    // Content taken off screen gets no pointer exit, so its tooltip is closed here instead.
    val onScreen = LocalOnScreen.current
    LaunchedEffect(onScreen) {
        if (!onScreen) {
            hovering = false
            show = false
        }
    }

    val density = LocalDensity.current
    val gapBelow = with(density) { 18.dp.roundToPx() }
    val gapAbove = with(density) { 28.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }

    Box(
        modifier = modifier
            // The pointer is taken from the enter too: it can come in with no move after it, and a tooltip placed from a
            // stale position may cover it, which closes the tooltip.
            .onPointerEvent(PointerEventType.Enter) {
                pointer = it.changes.first().position
                hovering = true
            }
            .onPointerEvent(PointerEventType.Move) { pointer = it.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) {
                if (show) Warmth.cooledAt = TimeSource.Monotonic.markNow()
                hovering = false
                show = false
                pressed = false
            }
            // Seen on the way down, before the content, and not consumed: the content's own click still works.
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) {
                pressed = true
                show = false
            }
    ) {
        content()

        LaunchedEffect(hovering, pressed) {
            if (hovering && !pressed) {
                if (!Warmth.warm) delay(delayMillis)
                show = true
            }
        }

        if (show && onScreen) {
            DisposableEffect(Unit) {
                Warmth.open++
                onDispose { Warmth.open-- }
            }
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

/** How long after a tooltip closed, its pointer gone on, the next shows at once. */
const val WARM_MILLIS = 600L

// Whether a tooltip is open now or closed a moment ago: one pointer, so one for the whole app.
private object Warmth {
    var open = 0
    var cooledAt: TimeMark? = null
    val warm: Boolean get() = open > 0 || cooledAt?.let { it.elapsedNow() < WARM_MILLIS.milliseconds } == true
}
