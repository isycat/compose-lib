package com.isycat.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // A browser has no title bar of its own in the page, so its bar shows the app's title, but no window buttons.
    @Test
    fun withoutAWindowTheTitleShowsWhenAsked() = runComposeUiTest {
        setContent { MaterialTheme { TabbedTitleBar(tabs, 0, {}, title = "cardSTUDIO", showAppTitle = true) } }

        onNodeWithTag(TitleBarTags.TITLE).assertTextEquals("cardSTUDIO")
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

    private val longTabs =
        (1..5).map { i -> TitleBarTab("Special Illustration Rare card number $i.cscard", key = "t$i", onClose = {}) }

    /** At phone width (360 dp), tabs share the bar with their titles cut short, and New tab stays on screen. */
    @Test
    fun narrowTabsShrinkAndNewTabStaysOnScreen() = runComposeUiTest {
        var added = 0
        setContent {
            MaterialTheme {
                Box(Modifier.width(PHONE)) { TabbedTitleBar(longTabs, 0, {}, onNewTab = { added++ }) }
            }
        }
        val plus = onNodeWithTag(TitleBarTags.NEW_TAB).getUnclippedBoundsInRoot()
        assertTrue(plus.right <= PHONE, "New tab runs past the bar: ${plus.right}")
        onNodeWithTag(TitleBarTags.NEW_TAB).performClick()
        assertEquals(1, added)
        longTabs.forEach { tab ->
            assertTrue(isCut(tab.title), "${tab.title} isn't cut short")
        }
        // The first tabs are whole and before the button: they share the room rather than run under it.
        val first = onNodeWithText(longTabs.first().title).getUnclippedBoundsInRoot()
        assertTrue(first.right <= plus.left, "The first tab runs under New tab")
    }

    /** At phone width, the tab in front's close button closes that tab only, once, and the bar stays. */
    @Test
    fun narrowCloseClosesOnlyTheTabInFront() = runComposeUiTest {
        val closed = mutableListOf<String>()
        val tabs = longTabs.take(4).map { TitleBarTab(it.title, key = it.key, onClose = { closed += it.title }) }
        val window = FakeWindow()
        setContent {
            MaterialTheme {
                Box(Modifier.width(PHONE)) { TabbedTitleBar(tabs, 2, {}, window = window, menu = { _, _ -> }, onNewTab = {}) }
            }
        }
        val close = onNodeWithContentDescription("Close ${tabs[2].title}")
        val bounds = close.getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= 0.dp && bounds.right <= PHONE, "The close button isn't whole on screen: $bounds")
        close.performClick()
        assertEquals(listOf(tabs[2].title), closed)
        assertEquals(0, window.closed, "The window was closed")
        onNodeWithTag(TitleBarTags.NEW_TAB).assertExists()
    }

    // Long tabs (four by default) at phone width, the last one in front, so the first ones are scrolled out of sight.
    private fun ComposeUiTest.phoneBar(
        moves: MutableList<Pair<Int, Int>>,
        count: Int = 4,
    ) {
        val tabs = longTabs.take(count)
        setContent {
            MaterialTheme {
                Box(Modifier.width(PHONE)) {
                    TabbedTitleBar(tabs, tabs.lastIndex, {}, onNewTab = {}, onMoveTab = { from, to -> moves += from to to })
                }
            }
        }
    }

    private fun ComposeUiTest.leftOf(title: String) = onNodeWithText(title).getUnclippedBoundsInRoot().left

    /** Web gate 2026-11-11: with 4 tabs at 360 dp a mouse wheel didn't reach the first ones. It scrolls them back now. */
    @Test
    fun aMouseWheelScrollsHiddenTabsIntoView() = runComposeUiTest {
        phoneBar(mutableListOf())
        val first = longTabs.first().title
        val before = leftOf(first)
        assertTrue(before < 0.dp, "The first tab isn't scrolled out of sight to start with: $before")
        // Over the tab in front, which is in view; the strip's own corner is scrolled out of it.
        onNodeWithText(longTabs[3].title).performMouseInput {
            moveTo(center)
            scroll(-20f)
        }
        waitForIdle()
        assertTrue(leftOf(first) >= 0.dp, "The wheel didn't bring the first tab back: ${leftOf(first)}")
    }

    /** A touch swipe scrolls the tabs rather than dragging one of them to a new place. */
    @Test
    fun aTouchSwipeScrollsTheTabsWithoutMovingOne() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        phoneBar(moves)
        val first = longTabs.first().title
        val before = leftOf(first)
        onNodeWithTag(TitleBarTags.TABS).performTouchInput { swipeRight(startX = 10f, endX = width.toFloat() - 10f) }
        waitForIdle()
        assertTrue(leftOf(first) > before, "The swipe didn't scroll the tabs")
        assertEquals(emptyList(), moves, "The swipe moved a tab")
    }

    /** Web gate 2026-11-11: one notch jumped the whole strip, then moved only a quarter of a tab. One notch moves one tab. */
    @Test
    fun oneWheelNotchStepsAboutOneTab() = runComposeUiTest {
        // Five tabs, so the strip is scrolled more than one tab from its start and the notch isn't stopped there.
        phoneBar(mutableListOf(), count = 5)
        val first = longTabs.first().title
        val before = leftOf(first)
        assertTrue(before < -MinTabWidth, "The strip isn't scrolled more than one tab from its start: $before")
        onNodeWithText(longTabs[4].title).performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        waitForIdle()
        assertEquals(MinTabWidth.value, (leftOf(first) - before).value, 0.5f, "One notch didn't move the tabs one tab")
    }

    /** Web gate 2026-11-11: a tab touched in a swipe kept its hover look and close button after the finger lifted. */
    @Test
    fun aTouchLeavesNoTabLookingHovered() = runComposeUiTest {
        phoneBar(mutableListOf())
        val other = longTabs[2].title
        onNodeWithText(other).performTouchInput { swipeRight(startX = 10f, endX = 50f) }
        waitForIdle()
        onNodeWithContentDescription("Close $other").assertDoesNotExist()
    }

    /** Tabs that don't fit: the widest are cut to one width, narrow ones keep theirs, and none goes below the least. */
    @Test
    fun theWidestTabsGiveWayFirst() {
        assertEquals(listOf(100, 200), tabWidths(listOf(100, 200), room = 400, minWidth = 50))
        assertEquals(listOf(60, 120, 120), tabWidths(listOf(60, 300, 200), room = 300, minWidth = 50))
        assertEquals(listOf(40, 96, 96), tabWidths(listOf(40, 300, 200), room = 100, minWidth = 96))
    }

    /**
     * Less room than the least width (a 360 dp window): tabs are cut to the room, so the one in front is whole when it's
     * scrolled into view. Its close button was half cut off at 548 px (2026-11-10 check).
     */
    @Test
    fun noTabIsWiderThanTheRoom() {
        assertEquals(listOf(40, 70, 70), tabWidths(listOf(40, 300, 200), room = 70, minWidth = 96))
    }

    /** With room to spare, tabs keep their own widths and New tab follows the last one. */
    @Test
    fun wideTabsKeepTheirWidths() = runComposeUiTest {
        val two = longTabs.take(2)
        setContent { MaterialTheme { Box(Modifier.width(1600.dp)) { TabbedTitleBar(two, 0, {}, onNewTab = {}) } } }
        two.forEach { tab ->
            assertFalse(isCut(tab.title), "${tab.title} is cut short with room to spare")
        }
        val last = onNodeWithText(two.last().title).getUnclippedBoundsInRoot()
        val plus = onNodeWithTag(TitleBarTags.NEW_TAB).getUnclippedBoundsInRoot()
        assertTrue(plus.left - last.right < 80.dp, "New tab isn't beside the last tab")
    }

    /**
     * The bar at phone width (360 dp, drawn at 2x) with a window, its title, menu, undo and redo and four long tabs,
     * saved to build/screenshots/title-bar-360dp.png for review. The + must be whole inside the bar.
     */
    @Test
    fun narrowBarScreenshot() = runSkikoComposeUiTest(Size(720f, 80f), Density(2f)) {
        val window = FakeWindow()
        setContent {
            MaterialTheme {
                TabbedTitleBar(
                    longTabs.take(4),
                    1,
                    {},
                    title = "cardSTUDIO",
                    window = window,
                    menu = { _, _ -> },
                    undoRedo = TitleBarUndoRedo(canUndo = true, canRedo = false, onUndo = {}, onRedo = {}),
                    onNewTab = {},
                )
            }
        }
        val plus = onNodeWithTag(TitleBarTags.NEW_TAB).getUnclippedBoundsInRoot()
        assertTrue(plus.left >= 0.dp && plus.right <= PHONE, "New tab isn't whole on screen: $plus")
        val file = File("build/screenshots/title-bar-360dp.png")
        file.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
    }

    // Whether [text] is drawn narrower than it is, so cut short with an ellipsis.
    private fun ComposeUiTest.isCut(text: String): Boolean = layoutOf(text).let { it.multiParagraph.maxIntrinsicWidth > it.size.width }

    // How [text] was laid out where it's drawn.
    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        val PHONE = 360.dp
    }
}
