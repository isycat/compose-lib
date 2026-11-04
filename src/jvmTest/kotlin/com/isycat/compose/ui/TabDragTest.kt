package com.isycat.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

    /**
     * After a drag a tab shows whether the pointer is over it now: one released away from it isn't lit and shows its
     * unsaved dot, one released over it shows its close button. The window's caption, the bar beside the tabs, isn't the
     * content's, so the pointer reaching it leaves the content (an exit) while the tab is held, and no exit came after.
     */
    @Test
    fun `after a drag a tab is hovered only if the pointer is over it`() = runComposeUiTest {
        var titles by mutableStateOf(listOf("Alpha", "Bravo", "Charlie"))
        setContent {
            MaterialTheme {
                TabbedTitleBar(
                    tabs = titles.map { TitleBarTab(it, dirty = it == "Alpha", onClose = {}) },
                    selectedTabIndex = titles.indexOf("Charlie"),
                    onTabSelected = {},
                    onMoveTab = { from, to -> titles = titles.toMutableList().apply { add(to, removeAt(from)) } },
                )
            }
        }
        fun dot() = onAllNodesWithContentDescription("Unsaved changes").fetchSemanticsNodes().size
        fun close() = onAllNodesWithContentDescription("Close Alpha").fetchSemanticsNodes().size

        // Alpha dragged right past the others and released beyond its new place.
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(60) { moveBy(Offset(10f, 0f)) }
            release()
        }
        waitForIdle()
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), titles)
        assertEquals(1, dot(), "released away from it, the tab shows its unsaved dot")
        assertEquals(0, close(), "and no close button")

        // Dragged back left to the bar's edge, left of the tabs, where it leaves the content, and released there.
        val left = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.left
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            val edge = Offset(2f - left, centerY)
            repeat(20) { moveTo(center + (edge - center) * ((it + 1) / 20f)) }
            exit()
            release()
        }
        waitForIdle()
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titles)
        assertEquals(1, dot(), "released outside the content, the tab shows its unsaved dot")
        assertEquals(0, close(), "and no close button")

        // A short drag that ends over the tab.
        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(3) { moveBy(Offset(4f, 0f)) }
            release()
        }
        waitForIdle()
        assertEquals(0, dot(), "released over it, the tab is hovered")
        assertEquals(1, close())
    }
}
