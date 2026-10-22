package com.isycat.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScrollKeysTest {
    private val scroll = ScrollState(0)
    private val keys = ScrollKeys()

    /** 30 rows of 40dp in a 200dp viewport, in a focusable "window" that hands its keys to [keys]. */
    private fun ComposeUiTest.showList() {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalScrollKeys provides keys) {
                    Box(Modifier.size(300.dp, 200.dp).onKeyEvent { keys.onKeyEvent(it) }.focusable().testTag("window")) {
                        ScrollHintColumn(Modifier.size(300.dp, 200.dp).testTag("list"), state = scroll, middleClick = MiddleClickScroll.Drag) {
                            repeat(30) { Text("Row $it", Modifier.fillMaxWidth().height(40.dp)) }
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.hit(key: Key) {
        onNodeWithTag("window").performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun pageKeysScrollTheAreaUnderThePointer() = runComposeUiTest {
        showList()
        onNodeWithTag("window").requestFocus()
        hit(Key.PageDown)
        assertEquals(0, scroll.value, "nothing scrolls until the pointer is over the list")

        onNodeWithTag("list").performMouseInput { moveTo(center) }
        hit(Key.PageDown)
        assertEquals((scroll.viewportSize * 0.9f).toInt(), scroll.value)
        hit(Key.MoveEnd)
        assertEquals(scroll.maxValue, scroll.value)
        hit(Key.PageUp)
        assertTrue(scroll.value < scroll.maxValue)
        hit(Key.MoveHome)
        assertEquals(0, scroll.value)
    }

    @Test
    fun scrollMovesTheAreaUnderThePointerForAnAppsOwnKeys() = runComposeUiTest {
        showList()
        assertEquals(false, keys.scroll(ScrollKeys.Move.END), "nothing to move until the pointer is over the list")

        onNodeWithTag("list").performMouseInput { moveTo(center) }
        assertTrue(keys.scroll(ScrollKeys.Move.END))
        waitForIdle()
        assertEquals(scroll.maxValue, scroll.value)
        keys.scroll(ScrollKeys.Move.START)
        waitForIdle()
        assertEquals(0, scroll.value)
    }

    @Test
    fun aMiddleDragMovesTheContentWithThePointer() = runComposeUiTest {
        showList()
        onNodeWithTag("list").performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
            moveBy(Offset(0f, -100f))
            release(MouseButton.Tertiary)
        }
        waitForIdle()
        assertEquals(100, scroll.value)
    }
}
