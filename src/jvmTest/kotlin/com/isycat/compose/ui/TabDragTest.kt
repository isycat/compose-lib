package com.isycat.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Isabella, 2026-11-03: "I can't drag to re-order tabs". */
@OptIn(ExperimentalTestApi::class)
class TabDragTest {
    @Test
    fun `dragging a tab past its neighbours moves it, and a click doesn't`() = runComposeUiTest {
        var titles by mutableStateOf(listOf("Alpha", "Bravo", "Charlie"))
        var selected by mutableStateOf("Alpha")
        setContent {
            MaterialTheme {
                TabbedTitleBar(
                    tabs = titles.map { TitleBarTab(it) },
                    selectedTabIndex = titles.indexOf(selected),
                    onTabSelected = { selected = titles[it] },
                    onMoveTab = { from, to -> titles = titles.toMutableList().apply { add(to, removeAt(from)) } },
                )
            }
        }

        // Alpha dragged right, past Bravo and Charlie.
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(40) { moveBy(Offset(10f, 0f)) }
            release()
        }
        waitForIdle()
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), titles)
        assertEquals("Alpha", selected, "the dragged tab is the one in front")

        // A click without moving only selects.
        onNodeWithText("Bravo").performMouseInput {
            moveTo(center)
            press()
            release()
        }
        waitForIdle()
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), titles)
        assertEquals("Bravo", selected)
    }
}
