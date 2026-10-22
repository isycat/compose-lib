package com.isycat.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object ScrollHintDefaults {
    /** How far one click on a hint scrolls. Capped so a click never skips past what was hidden under the hint. */
    val Step: Dp = 240.dp

    /** Height of the faded edge. */
    val FadeHeight: Dp = 40.dp
}

/**
 * A vertically scrolling column that shows when there's more to see: while content continues below, the bottom edge
 * fades into [fadeColor] under a down arrow, and likewise at the top once it's scrolled down. Clicking a faded edge
 * scrolls by [step]; the wheel and the middle button ([middleClick]) work as usual, over the edges too, and so do Page
 * Up, Page Down, Home and End when the window has [ScrollKeys].
 *
 * [fadeColor] should be the colour behind the column, so the content looks like it runs out of view.
 */
@Composable
fun ScrollHintColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    step: Dp = ScrollHintDefaults.Step,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    middleClick: MiddleClickScroll = MiddleClickScroll.AutoScroll,
    content: @Composable ColumnScope.() -> Unit
) {
    // A middle-drag is taken by the box, so it works over the faded edges as well; autoscroll stays on the column.
    val drag = if (middleClick == MiddleClickScroll.Drag) Modifier.middleDragScroll(state) else Modifier
    val autoScroll = if (middleClick == MiddleClickScroll.AutoScroll) Modifier.middleClickAutoScroll(state) else Modifier
    Box(modifier.scrollKeys(state).then(drag)) {
        Column(
            Modifier.matchParentSize().verticalScroll(state).then(autoScroll),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
        ScrollHints(state, fadeColor = fadeColor, step = step)
    }
}

/**
 * The faded edges of [ScrollHintColumn], for any vertical scrollable (a `LazyListState` as well as a `ScrollState`):
 * place it after the scrolling content in the same [Box], whose size should be the scrolling viewport's.
 *
 * Each edge appears only while there's more in its direction. A click scrolls by [step], but never by more than the
 * viewport minus both edges, so in a short viewport nothing slides past unseen.
 */
@Composable
fun BoxScope.ScrollHints(
    state: ScrollableState,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    step: Dp = ScrollHintDefaults.Step,
    showTop: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val stepPx = with(density) {
        val cap = viewport - 2 * ScrollHintDefaults.FadeHeight.toPx()
        if (cap > 0f) minOf(step.toPx(), cap) else step.toPx()
    }
    Spacer(Modifier.matchParentSize().onSizeChanged { viewport = it.height })
    if (showTop) {
        ScrollHintEdge(
            visible = state.canScrollBackward,
            down = false,
            state = state,
            fadeColor = fadeColor,
            modifier = modifier.align(Alignment.TopCenter)
        ) { scope.launch { state.animateScrollBy(-stepPx) } }
    }
    ScrollHintEdge(
        visible = state.canScrollForward,
        down = true,
        state = state,
        fadeColor = fadeColor,
        modifier = modifier.align(Alignment.BottomCenter)
    ) { scope.launch { state.animateScrollBy(stepPx) } }
}

@Composable
private fun ScrollHintEdge(
    visible: Boolean,
    down: Boolean,
    state: ScrollableState,
    fadeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AnimatedVisibility(visible, modifier.fillMaxWidth(), enter = fadeIn(), exit = fadeOut()) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        // Fully covered at the very edge, so the content reads as running on out of view.
        val fade = listOf(fadeColor.copy(alpha = 0f), fadeColor.copy(alpha = 0.85f), fadeColor)
        Box(
            Modifier
                .fillMaxWidth()
                .height(ScrollHintDefaults.FadeHeight)
                .background(Brush.verticalGradient(if (down) fade else fade.reversed()))
                // The edge sits over the content, so the wheel has to work here too; same direction as verticalScroll.
                .scrollable(state, Orientation.Vertical, reverseDirection = true)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = if (down) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Icon(
                if (down) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (down) "Scroll down" else "Scroll up",
                tint = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp).size(24.dp)
            )
        }
    }
}
