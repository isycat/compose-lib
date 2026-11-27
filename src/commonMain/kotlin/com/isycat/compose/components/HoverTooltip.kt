package com.isycat.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
@Composable
fun HoverTooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Long = 350,
    content: @Composable () -> Unit
) {
    // What the pointer does is followed by one node of the content's box, never by state the composition holds: a
    // tooltip goes round every icon button of every row, and each composed its own states, effects and four pointer
    // handlers, a fifth of what a row list made as a jump showed new rows (2026-11-27).
    val tip = remember { TipState() }
    // Content taken off screen gets no pointer exit, so its tooltip is closed here instead.
    val onScreen = LocalOnScreen.current
    if (!onScreen) SideEffect { tip.leave(cooled = false) }

    Box(modifier = modifier.then(HoverTipElement(tip, delayMillis))) {
        content()

        if (tip.show && onScreen) {
            DisposableEffect(Unit) {
                Warmth.open++
                onDispose { Warmth.open-- }
            }
            val density = LocalDensity.current
            val gapBelow = with(density) { 18.dp.roundToPx() }
            val gapAbove = with(density) { 28.dp.roundToPx() }
            val margin = with(density) { 8.dp.roundToPx() }
            val pointer = tip.shownAt
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

/**
 * One tooltip's state: whether it shows and where the pointer is over its content, the one read while it shows. The
 * pointer is taken from the enter too: it can come in with no move after it, and a tooltip placed from a stale position
 * may cover it, which closes the tooltip.
 */
private class TipState {
    var show by mutableStateOf(false)
        private set

    // Where the pointer is, as the tooltip is placed: state only while it shows, so a pointer passing over content with
    // no tooltip open changes nothing a composition reads.
    var shownAt by mutableStateOf(Offset.Zero)
        private set
    private var pointer = Offset.Zero
    private var hovering = false
    private var pressed = false
    private var waiting: Job? = null

    fun enter(at: Offset, scope: CoroutineScope, delayMillis: Long) {
        pointer = at
        hovering = true
        wait(scope, delayMillis)
    }

    fun move(at: Offset) {
        pointer = at
        if (show) shownAt = at
    }

    fun press() {
        pressed = true
        waiting?.cancel()
        show = false
    }

    fun leave(cooled: Boolean = true) {
        if (show && cooled) Warmth.cooledAt = TimeSource.Monotonic.markNow()
        waiting?.cancel()
        hovering = false
        show = false
        pressed = false
    }

    // The first tooltip waits; while one is open, or a moment after, the next shows at once.
    private fun wait(scope: CoroutineScope, delayMillis: Long) {
        waiting?.cancel()
        if (!hovering || pressed) return
        waiting = scope.launch {
            if (!Warmth.warm) delay(delayMillis)
            shownAt = pointer
            show = true
        }
    }
}

private data class HoverTipElement(val tip: TipState, val delayMillis: Long) : ModifierNodeElement<HoverTipNode>() {
    override fun create() = HoverTipNode(tip, delayMillis)

    override fun update(node: HoverTipNode) {
        node.tip = tip
        node.delayMillis = delayMillis
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "hoverTip"
    }
}

/**
 * Follows the pointer over a tooltip's content: in, about and out of it, and a press, seen on the way down, before the
 * content, and not consumed, so the content's own click still works.
 */
private class HoverTipNode(var tip: TipState, var delayMillis: Long) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        when {
            pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press -> tip.press()
            pass != PointerEventPass.Main -> Unit
            pointerEvent.type == PointerEventType.Enter -> tip.enter(pointerEvent.changes.first().position, coroutineScope, delayMillis)
            pointerEvent.type == PointerEventType.Move -> tip.move(pointerEvent.changes.first().position)
            pointerEvent.type == PointerEventType.Exit -> tip.leave()
        }
    }

    override fun onCancelPointerInput() = Unit

    override fun onDetach() = tip.leave(cooled = false)
}

/** How long after a tooltip closed, its pointer gone on, the next shows at once. */
const val WARM_MILLIS = 600L

// Whether a tooltip is open now or closed a moment ago: one pointer, so one for the whole app.
private object Warmth {
    var open = 0
    var cooledAt: TimeMark? = null
    val warm: Boolean get() = open > 0 || cooledAt?.let { it.elapsedNow() < WARM_MILLIS.milliseconds } == true
}
