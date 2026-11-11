package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A code editor's undo and redo: one step an edit, one a run of typing, the selection back as it was. */
class CodeHistoryTest {
    private fun value(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun aRunOfTypingIsOneStepAndAnEditAnother() {
        val history = CodeHistory()
        val steps = listOf(value(""), value("v"), value("va"), value("val"), value("val\n    "), value("val\n    x"))
        steps.zipWithNext().forEach { (before, after) -> history.record(before, after) }
        assertEquals(value("val\n    "), history.undo(steps.last()))
        assertEquals(value("val"), history.undo(value("val\n    ")))
        assertEquals(value(""), history.undo(value("val")))
        assertNull(history.undo(value("")))
        assertEquals(value("val"), history.redo(value("")))
    }

    @Test
    fun undoPutsTheSelectionBack() {
        val history = CodeHistory()
        val before = TextFieldValue("a\nb", TextRange(0, 3))
        history.record(before, value("    a\n    b"))
        assertEquals(before, history.undo(value("    a\n    b")))
    }

    @Test
    fun aMoveOfTheCaretAloneIsNoStepButEndsTheRun() {
        val history = CodeHistory()
        history.record(value(""), value("a"))
        history.record(value("a", 0), value("ba", 1))
        assertEquals(value("a", 0), history.undo(value("ba", 1)))
    }

    @Test
    fun aTextChangedElsewhereForgetsTheSteps() {
        val history = CodeHistory()
        history.record(value(""), value("a"))
        assertNull(history.undo(value("something else")))
        assertNull(history.undo(value("a")))
    }

    @Test
    fun onlyTheLastStepsAreKept() {
        val history = CodeHistory(limit = 2)
        listOf("a\n", "a\nb\n", "a\nb\nc\n").fold(value("")) { before, text -> value(text).also { history.record(before, it) } }
        assertEquals(value("a\nb\n"), history.undo(value("a\nb\nc\n")))
        assertEquals(value("a\n"), history.undo(value("a\nb\n")))
        assertNull(history.undo(value("a\n")))
    }
}
