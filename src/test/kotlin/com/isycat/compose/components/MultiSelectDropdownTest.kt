package com.isycat.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MultiSelectDropdownTest {

    /** Left-click includes, right-click excludes, and one click never does both — a right-click that also
     *  fired the item's onClick would cross and immediately un-cross the option. */
    @Test
    fun leftClickIncludesRightClickExcludes() = runComposeUiTest {
        var selected by mutableStateOf(emptySet<String>())
        var excluded by mutableStateOf(emptySet<String>())
        setContent {
            MaterialTheme {
                MultiSelectDropdown(
                    options = listOf(DropdownOption("a", "M-A"), DropdownOption("b", "M-B")),
                    selected = selected,
                    excluded = excluded,
                    onChange = { s, e -> selected = s; excluded = e },
                    allLabel = "All formats"
                )
            }
        }

        onNodeWithText("All formats").performClick() // open the menu

        onNodeWithText("M-B").performMouseInput { rightClick() }
        assertEquals(emptySet(), selected, "right-click must not also tick")
        assertEquals(setOf("b"), excluded)
        onNodeWithText("All formats except M-B").assertExists()

        onNodeWithText("M-A").performClick()
        assertEquals(setOf("a"), selected)
        assertEquals(setOf("b"), excluded)
        onNodeWithText("M-A · not M-B").assertExists()

        // Ticking a crossed option moves it to included…
        onNodeWithText("M-B").performClick()
        assertEquals(setOf("a", "b"), selected)
        assertEquals(emptySet(), excluded)

        // …and crossing a ticked one moves it to excluded.
        onNodeWithText("M-A").performMouseInput { rightClick() }
        assertEquals(setOf("b"), selected)
        assertEquals(setOf("a"), excluded)
        onNodeWithText("M-B · not M-A").assertExists()

        // A second right-click clears the cross.
        onNodeWithText("M-A").performMouseInput { rightClick() }
        assertEquals(setOf("b"), selected)
        assertEquals(emptySet(), excluded)
    }
}
