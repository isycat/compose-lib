package com.isycat.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScrollHintsTest {
    private val scroll = ScrollState(0)

    /** 30 rows of 40dp in a 200dp viewport at density 1: 1000 px of content can scroll. */
    private fun androidx.compose.ui.test.ComposeUiTest.showList(step: androidx.compose.ui.unit.Dp = ScrollHintDefaults.Step) {
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    ScrollHintColumn(Modifier.size(300.dp, 200.dp), state = scroll, step = step) {
                        repeat(30) { Text("Row $it", Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
    }

    @Test
    fun onlyTheDownHintShowsAtTheTop() = runComposeUiTest {
        showList()
        onNodeWithContentDescription("Scroll down").assertExists()
        onNodeWithContentDescription("Scroll up").assertDoesNotExist()
    }

    /** The default step is more than this viewport can take, so a click scrolls the viewport less both edges. */
    @Test
    fun clickingTheDownHintScrollsWithoutSkippingAnything() = runComposeUiTest {
        showList()
        val density = density.density
        onNodeWithContentDescription("Scroll down").performClick()
        waitForIdle()
        assertEquals(((200 - 2 * 40) * density).toInt(), scroll.value)
        onNodeWithContentDescription("Scroll up").assertExists()
    }

    @Test
    fun aSmallStepIsUsedAsIs() = runComposeUiTest {
        showList(step = 50.dp)
        onNodeWithContentDescription("Scroll down").performClick()
        waitForIdle()
        assertEquals((50 * density.density).toInt(), scroll.value)
    }

    @Test
    fun theDownHintGoesAtTheEnd() = runComposeUiTest {
        showList()
        runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
        waitForIdle()
        onNodeWithContentDescription("Scroll down").assertDoesNotExist()
        onNodeWithContentDescription("Scroll up").assertExists()
    }
}
