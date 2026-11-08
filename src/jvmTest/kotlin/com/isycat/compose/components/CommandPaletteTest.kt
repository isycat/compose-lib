package com.isycat.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CommandPaletteTest {
    private val ran = mutableListOf<String>()
    private val items = listOf("New part", "Close tab", "Frame all", "Undo").map { title ->
        PaletteItem(title, detail = "File") { ran += title }
    } + PaletteItem("Redo", enabled = false) { ran += "Redo" }

    @Test
    fun aRunOfTheQueryRanksAboveScatteredLettersAndAtTheStartAboveLaterOn() {
        assertEquals(items.map { it.title }, paletteSearch(items, " ").map { it.title }, "with nothing typed, everything in its own order")
        assertEquals("Frame all", paletteSearch(items, "fra").first().title)
        assertEquals("Close tab", paletteSearch(items, "ct").first().title, "scattered letters: c(lose) t(ab)")
        assertEquals(listOf(0, 1, 2), matchPositions("fra", "Frame all"))
        assertEquals(null, matchScore("zz", "Frame all"))
    }

    @Test
    fun everyLetterThePaletteWritesItselfIsInTheBundledFont() {
        // The browser has no fonts but the ones bundled: a glyph missing from Roboto draws as an empty box there.
        val font = com.isycat.compose.ui.AwtFonts.roboto(12f)
        val inline = '�'
        val written = keysHint("runs", "Type to find more").text + "Nothing matches “x”"
        val missing = written.filter { it != inline && !font.canDisplay(it) }.toSet()
        assertTrue(missing.isEmpty(), "not in Roboto: ${missing.joinToString { "U+" + it.code.toString(16).uppercase() }}")
    }

    @Test
    fun theFieldHasTheFocusTypingFiltersArrowsChooseAndEnterRunsTheChoice() = runSkikoComposeUiTest {
        var open by mutableStateOf(true)
        setContent { MaterialTheme { if (open) CommandPalette(items, onDismiss = { open = false }) } }
        onNodeWithTag(COMMAND_PALETTE_FIELD).assertIsFocused()
        onNodeWithTag(COMMAND_PALETTE_FIELD).performTextInput("a")
        onNodeWithTag(COMMAND_PALETTE_FIELD).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(1, ran.size, "Enter ran one command: $ran")
        assertTrue(!open, "running a command closes the palette")
    }

    @Test
    fun escapeClosesItAndADisabledCommandDoesNotRun() = runSkikoComposeUiTest {
        var open by mutableStateOf(true)
        setContent { MaterialTheme { if (open) CommandPalette(items, onDismiss = { open = false }) } }
        onNodeWithTag(COMMAND_PALETTE_FIELD).performTextInput("redo")
        onNodeWithTag(COMMAND_PALETTE_FIELD).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(ran.isEmpty() && open, "a disabled command neither runs nor closes the palette")
        onNodeWithText("Redo").assertExists()
        onNodeWithTag(COMMAND_PALETTE_FIELD).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(!open, "Escape closes the palette")
    }
}
