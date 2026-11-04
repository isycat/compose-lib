package com.isycat.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HoverTooltipTest {
    // A pointer that comes in with no move after it, in the middle of a 40 x 40 box: the tooltip shows below it and stays.
    @Test
    fun `a tooltip shows below a pointer that only entered`() = runSkikoComposeUiTest(Size(300f, 300f), Density(1f)) {
        setContent {
            Box(Modifier.size(300.dp)) {
                HoverTooltip({ Text("Tip") }) { Box(Modifier.size(40.dp).testTag("target")) }
            }
        }
        onNodeWithTag("target").performMouseInput {
            moveTo(Offset(100f, 100f))
            moveTo(center)
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(2, onAllNodes(isRoot()).fetchSemanticsNodes().size, "the tooltip is open")
        val tip = onNodeWithText("Tip").fetchSemanticsNode().boundsInWindow
        assertEquals(20f + 18f, tip.top, "the tooltip is 18 px below the pointer")
    }

    // A click on the content, as on a title bar tab: no tooltip while the pointer stays, so it can't take the next click.
    @Test
    fun `a press on the content keeps the tooltip shut until the pointer leaves and comes back`() =
        runSkikoComposeUiTest(Size(300f, 300f), Density(1f)) {
            var clicks = 0
            setContent {
                Box(Modifier.size(300.dp)) {
                    HoverTooltip({ Text("Tip") }) { Box(Modifier.size(40.dp).testTag("target").clickable { clicks++ }) }
                }
            }
            val target = onNodeWithTag("target")
            target.performMouseInput {
                moveTo(Offset(100f, 100f))
                moveTo(center)
                click(center)
            }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertEquals(1, clicks, "the content's own click still works")
            assertEquals(1, popups(), "no tooltip after the press, while the pointer stays")

            target.performMouseInput {
                moveTo(Offset(100f, 100f))
                moveTo(center)
            }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertEquals(2, popups(), "it shows again after the pointer left and came back")

            target.performMouseInput { click(center) }
            waitForIdle()
            assertEquals(1, popups(), "a press closes an open tooltip")
            assertEquals(2, clicks)
        }

    private fun androidx.compose.ui.test.SkikoComposeUiTest.popups() = onAllNodes(isRoot()).fetchSemanticsNodes().size
}
