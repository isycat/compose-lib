package com.isycat.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
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
}
