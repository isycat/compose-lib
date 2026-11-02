package com.isycat.compose.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TabbedTitleBarTest {
    /** A window that only counts what the bar asks of it. */
    private class FakeWindow : TitleBarWindow {
        override var isMaximized by mutableStateOf(false)
        var minimized = 0
        var closed = 0
        var moved = 0

        override fun minimize() {
            minimized++
        }

        override fun toggleMaximized() {
            isMaximized = !isMaximized
        }

        override fun close() {
            closed++
        }

        override fun startMove() {
            moved++
        }
    }

    private val tabs =
        listOf(
            TitleBarTab("Tree.msplant", tooltip = "D:\\plants\\Tree.msplant", dirty = true),
            TitleBarTab("Bush.msplant"),
        )

    @Test
    fun aClickedTabIsSelected() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent { MaterialTheme { TabbedTitleBar(tabs, selected, { selected = it }) } }

        onNodeWithText("Bush.msplant").performClick()
        assertEquals(1, selected)
        onNodeWithText("Tree.msplant").performClick()
        assertEquals(0, selected)
    }

    /** Isabella, 2026-11-02: "the tabs change size when clicked so they all shift". */
    @Test
    fun aTabKeepsItsPlaceAndWidthWhetherOrNotItsInFront() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent { MaterialTheme { TabbedTitleBar(tabs, selected, { selected = it }) } }

        val behind = onNodeWithText("Bush.msplant").getUnclippedBoundsInRoot()
        onNodeWithText("Bush.msplant").performClick()
        assertEquals(1, selected)
        assertEquals(behind, onNodeWithText("Bush.msplant").getUnclippedBoundsInRoot(), "neither tab changed width")
    }

    /** Isabella, 2026-11-02: "middle click still doesnt close tabs???". */
    @Test
    fun aMiddleClickClosesATabWithoutSelectingIt() = runComposeUiTest {
        var selected by mutableStateOf(0)
        val closed = mutableListOf<String>()
        val closable = tabs.map { tab -> TitleBarTab(tab.title, onClose = { closed += tab.title }) }
        setContent { MaterialTheme { TabbedTitleBar(closable, selected, { selected = it }) } }

        onNodeWithText("Bush.msplant").middleClick()
        assertEquals(listOf("Bush.msplant"), closed)
        assertEquals(0, selected)
        // On the tab's own close button too, closing it once.
        onNodeWithContentDescription("Close Tree.msplant").middleClick()
        assertEquals(listOf("Bush.msplant", "Tree.msplant"), closed)
        // Released off the tab, it closes nothing, as a click let go elsewhere doesn't click.
        onNodeWithText("Bush.msplant").performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
            moveTo(Offset(right + 100f, centerY))
            release(MouseButton.Tertiary)
        }
        assertEquals(2, closed.size)
        // A primary click still selects it.
        onNodeWithText("Bush.msplant").performClick()
        assertEquals(1, selected)
        assertEquals(2, closed.size)
    }

    @Test
    fun aMiddleClickOnATabThatCantCloseDoesNothing() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent { MaterialTheme { TabbedTitleBar(tabs, selected, { selected = it }) } }

        onNodeWithText("Bush.msplant").middleClick()
        assertEquals(0, selected)
        onNodeWithText("Bush.msplant").assertExists()
    }

    private fun SemanticsNodeInteraction.middleClick() =
        performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
            release(MouseButton.Tertiary)
        }

    @Test
    fun anUnsavedTabWearsADot() = runComposeUiTest {
        setContent { MaterialTheme { TabbedTitleBar(tabs, 1, {}) } }

        onAllNodesWithContentDescription("Unsaved changes").assertCountEquals(1)
    }

    @Test
    fun theTitleStaysTheAppsUnlessItFollowsTheTab() = runComposeUiTest {
        var follows by mutableStateOf(false)
        setContent { MaterialTheme { TabbedTitleBar(tabs, 0, {}, title = "moonshine plants", titleFollowsTab = follows, window = FakeWindow()) } }

        onNodeWithTag(TitleBarTags.TITLE).assertTextEquals("moonshine plants")
        follows = true
        onNodeWithTag(TitleBarTags.TITLE).assertTextEquals("Tree.msplant • — moonshine plants")
    }

    @Test
    fun withoutAWindowThereIsNoTitleNorWindowButtons() = runComposeUiTest {
        setContent { MaterialTheme { TabbedTitleBar(tabs, 0, {}, title = "cardSTUDIO") } }

        onNodeWithTag(TitleBarTags.TITLE).assertDoesNotExist()
        onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun theWindowsButtonsWorkTheWindow() = runComposeUiTest {
        val window = FakeWindow()
        setContent { MaterialTheme { TabbedTitleBar(tabs, 0, {}, title = "cardSTUDIO", window = window) } }

        onNodeWithContentDescription("Minimise").performClick()
        assertEquals(1, window.minimized)
        onNodeWithContentDescription("Maximise").performClick()
        assertEquals(true, window.isMaximized)
        onNodeWithContentDescription("Restore down").performClick()
        assertEquals(false, window.isMaximized)
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, window.closed)
        // The buttons took their presses; none moved the window.
        assertEquals(0, window.moved)
    }

    @Test
    fun theWindowsButtonsAreCompactOrSpacedAsWindowsSpacesThem() = runComposeUiTest {
        var style by mutableStateOf(TitleBarWindowButtons.Compact)
        setContent { MaterialTheme { TabbedTitleBar(tabs, 0, {}, window = FakeWindow(), windowButtons = style) } }

        onNodeWithContentDescription("Close").assertWidthIsEqualTo(34.dp)
        style = TitleBarWindowButtons.Windows
        onNodeWithContentDescription("Close").assertWidthIsEqualTo(46.dp)
    }

    @Test
    fun theHamburgerOpensTheMenu() = runComposeUiTest {
        var picked = false
        setContent {
            MaterialTheme {
                TabbedTitleBar(
                    tabs,
                    0,
                    {},
                    menu = { expanded, dismiss ->
                        DropdownMenu(expanded, dismiss) {
                            DropdownMenuItem(text = { Text("Open project…") }, onClick = {
                                picked = true
                                dismiss()
                            })
                        }
                    },
                )
            }
        }

        onNodeWithText("Open project…").assertDoesNotExist()
        onNodeWithTag(TitleBarTags.MENU).performClick()
        onNodeWithText("Open project…").performClick()
        assertEquals(true, picked)
        onNodeWithText("Open project…").assertDoesNotExist()
    }

    @Test
    fun undoAndRedoFollowTheirHistory() = runComposeUiTest {
        var undone = 0
        setContent {
            MaterialTheme {
                TabbedTitleBar(tabs, 0, {}, undoRedo = TitleBarUndoRedo(canUndo = true, canRedo = false, onUndo = { undone++ }, onRedo = {}))
            }
        }

        onNodeWithContentDescription("Undo").performClick()
        assertEquals(1, undone)
        onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun theSearchBoxOpensTheSearch() = runComposeUiTest {
        var searched = 0
        setContent {
            MaterialTheme { TabbedTitleBar(tabs, 0, {}, search = TitleBarSearch(onClick = { searched++ }, shortcut = "Ctrl+Space")) }
        }

        onNodeWithText("Ctrl+Space").assertExists()
        onNodeWithTag(TitleBarTags.SEARCH).performClick()
        assertEquals(1, searched)
    }
}
