package com.isycat.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Page Up, Page Down, Home and End for whichever scrolling area the pointer is over, as desktop apps behave. Make one
 * for the window and provide it with [LocalScrollKeys], mark each scrolling area with [scrollKeys], and hand the
 * window's key events to [onKeyEvent] after everything else has had them (`Window(onKeyEvent = …)`), so a focused text
 * field keeps Home and End for its cursor. An app whose keys can be rebound calls [scroll] from its own shortcuts
 * instead.
 */
class ScrollKeys {
    private var hovered: Hovered? = null

    /** How a key moves a scrolling area. */
    enum class Move { PAGE_UP, PAGE_DOWN, START, END }

    /** Scrolls the area under the pointer for a Page Up, Page Down, Home or End press. Returns whether it did. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
        val move =
            when (event.key) {
                Key.PageDown -> Move.PAGE_DOWN
                Key.PageUp -> Move.PAGE_UP
                Key.MoveHome -> Move.START
                Key.MoveEnd -> Move.END
                else -> return false
            }
        return scroll(move)
    }

    /** Moves the area under the pointer by [move]. Returns whether there was one to move. */
    fun scroll(move: Move): Boolean {
        val area = hovered ?: return false
        val target = area.target
        area.scope.launch {
            when (move) {
                Move.PAGE_DOWN -> target.scrollBy(target.page())
                Move.PAGE_UP -> target.scrollBy(-target.page())
                Move.START -> target.toStart()
                Move.END -> target.toEnd()
            }
        }
        return true
    }

    internal fun enter(area: Hovered) {
        hovered = area
    }

    internal fun exit(area: Hovered) {
        if (hovered === area) hovered = null
    }

    internal class Hovered(val target: ScrollKeyTarget, val scope: CoroutineScope)
}

/** The window's [ScrollKeys]; without one, [scrollKeys] does nothing. */
val LocalScrollKeys = staticCompositionLocalOf<ScrollKeys?> { null }

/**
 * A scrolling area as [ScrollKeys] moves it: [page] is how far Page Up and Page Down go, [scrollBy] scrolls by that
 * much (forward when positive), and [toStart] and [toEnd] are Home and End.
 */
class ScrollKeyTarget(
    val page: () -> Float,
    val scrollBy: suspend (Float) -> Unit,
    val toStart: suspend () -> Unit,
    val toEnd: suspend () -> Unit,
)

/** Makes this the area the window's [ScrollKeys] move while the pointer is over it. */
@Composable
fun Modifier.scrollKeys(target: ScrollKeyTarget): Modifier {
    val keys = LocalScrollKeys.current ?: return this
    val scope = rememberCoroutineScope()
    val area = remember(target, scope) { ScrollKeys.Hovered(target, scope) }
    DisposableEffect(keys, area) { onDispose { keys.exit(area) } }
    return pointerInput(keys, area) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move -> keys.enter(area)
                    PointerEventType.Exit -> keys.exit(area)
                }
            }
        }
    }
}

/** [scrollKeys] for a `verticalScroll` column: a page is most of its height, so a line or two stays in view. */
@Composable
fun Modifier.scrollKeys(state: ScrollState): Modifier = scrollKeys(
    remember(state) {
        ScrollKeyTarget(
            page = { state.viewportSize * PAGE_SHARE },
            scrollBy = { state.animateScrollBy(it) },
            toStart = { state.animateScrollTo(0) },
            toEnd = { state.animateScrollTo(state.maxValue) },
        )
    },
)

/** [scrollKeys] for a `LazyColumn`. */
@Composable
fun Modifier.scrollKeys(state: LazyListState): Modifier = scrollKeys(
    remember(state) {
        ScrollKeyTarget(
            page = { (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset) * PAGE_SHARE },
            scrollBy = { state.animateScrollBy(it) },
            toStart = { state.animateScrollToItem(0) },
            toEnd = { state.layoutInfo.totalItemsCount.takeIf { it > 0 }?.let { state.animateScrollToItem(it - 1) } },
        )
    },
)

/** The share of a scrolling area's height that Page Up and Page Down move, keeping the rest in view for context. */
private const val PAGE_SHARE = 0.9f
