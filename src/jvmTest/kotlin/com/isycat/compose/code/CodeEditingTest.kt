package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A code editor's keys as moonshine's editor has them, written with `|` for the caret and `[` `]` round a selection. */
class CodeEditingTest {
    private val editing = CodeEditing()

    /** [marked] as text and selection: `|` a caret, or `[` and `]` a selection from the first to the second. */
    private fun at(marked: String): TextFieldValue {
        val caret = marked.indexOf('|')
        if (caret >= 0) return TextFieldValue(marked.removeRange(caret, caret + 1), TextRange(caret))
        val start = marked.indexOf('[')
        val end = marked.indexOf(']') - 1
        return TextFieldValue(marked.replace("[", "").replace("]", ""), TextRange(start, end))
    }

    private fun shown(value: TextFieldValue?): String? = value?.let {
        if (it.selection.collapsed) {
            it.text.substring(0, it.selection.start) + "|" + it.text.substring(it.selection.start)
        } else {
            val (start, end) = it.selection.min to it.selection.max
            it.text.substring(0, start) + "[" + it.text.substring(start, end) + "]" + it.text.substring(end)
        }
    }

    @Test
    fun enterKeepsTheIndentAndStepsInAfterAnOpeningBracket() {
        assertEquals("    val a = 1\n    |", shown(editing.newLine(at("    val a = 1|"))))
        assertEquals("    if (a) {\n        |", shown(editing.newLine(at("    if (a) {|"))))
        assertEquals("box(\n    |\n)", shown(editing.newLine(at("box(|)"))))
        assertEquals("f {\n    |\n}", shown(editing.newLine(at("f {|}"))))
        assertEquals("    a\n    |b", shown(editing.newLine(at("    a|   b"))), "what follows loses its spaces")
        assertEquals("  \n  |x", shown(editing.newLine(at("  |  x"))), "a caret in the indent keeps the indent before it")
        assertEquals("a\n|", shown(editing.newLine(at("a[ b]"))), "the selection goes")
    }

    @Test
    fun aClosingBraceTypedOnABlankLineLinesUpWithItsOpening() {
        assertEquals("if (a) {\n    b\n}|", shown(editing.type(at("if (a) {\n    b\n    |"), '}')))
        assertEquals("  f(\n    x\n  )|", shown(editing.type(at("  f(\n    x\n      |"), ')')))
        assertNull(editing.type(at("    x |"), '}'), "after text it is typed as it is")
        assertEquals("{ \"}\"\n}|", shown(editing.type(at("{ \"}\"\n    |"), '}', Literals(strings = listOf(2..4)))), "a brace in a string is not counted")
    }

    @Test
    fun bracketsAndQuotesPairTypeOverAndWrap() {
        assertEquals("box(|)", shown(editing.type(at("box|"), '(')))
        assertEquals("(|)", shown(editing.type(at("|"), '(')))
        assertNull(editing.type(at("|x"), '('), "before a name it goes in alone")
        assertEquals("box()|", shown(editing.type(at("box(|)"), ')')))
        assertEquals("([a b])", shown(editing.type(at("[a b]"), '(')))
        assertEquals("f(\"|\")", shown(editing.type(at("f(|)"), '"')))
        assertNull(editing.type(at("ab|"), '"'), "after a name a quote goes in alone")
        val string = Literals(strings = listOf(0..3))
        assertEquals("\"ab\"|", shown(editing.type(at("\"ab|\""), '"', string)), "the quote that ends a string is typed over")
        assertNull(editing.type(at("\"a | b\""), '"', Literals(strings = listOf(0..5))), "inside a string a quote goes in alone")
    }

    @Test
    fun backspaceStepsBackAnIndentAndTakesAPair() {
        assertEquals("    |x", shown(editing.backspace(at("        |x"))))
        assertEquals("    |", shown(editing.backspace(at("      |"))), "to the stop before")
        assertEquals("f|", shown(editing.backspace(at("f(|)"))))
        assertEquals("|", shown(editing.backspace(at("\"|\""))))
        assertNull(editing.backspace(at("ab|")), "anything else the field deletes")
        assertNull(editing.backspace(at("[ab]")))
    }

    @Test
    fun tabAndShiftTabIndentAndOutdentWithSpaces() {
        assertEquals("    |x", shown(editing.indent(at("|x"))))
        assertEquals("ab  |", shown(editing.indent(at("ab|"))), "to the next stop")
        assertEquals("    [a\n    b]\n\nc", shown(editing.indent(at("[a\nb]\n\nc"))))
        assertEquals("    [a\n\n    b]", shown(editing.indent(at("[a\n\nb]"))), "blank lines stay blank")
        assertEquals("    [a\n]b", shown(editing.indent(at("[a\n]b"))), "a selection ending at a line's start leaves that line")
        assertEquals("|x", shown(editing.outdent(at("    |x"))))
        assertEquals("[a\n  b]", shown(editing.outdent(at("    [a\n      b]"))))
        assertEquals("x|", shown(editing.outdent(at("  x|"))))
        assertNull(editing.outdent(at("x|")))
    }

    @Test
    fun ctrlSlashCommentsLinesOutAndIn() {
        assertEquals("    // val |a", shown(editing.toggleComment(at("    val |a"))))
        assertEquals("    val |a", shown(editing.toggleComment(at("    // val |a"))))
        assertEquals("  // [a\n  //   b\n\n  // c]", shown(editing.toggleComment(at("  [a\n    b\n\n  c]"))))
        assertEquals("[a\n  b]", shown(editing.toggleComment(at("[//a\n  // b]"))))
        assertNull(editing.toggleComment(at("|")))
    }

    @Test
    fun linesDuplicateGoAndMove() {
        assertEquals("a\nb\nb|\nc", shown(editing.duplicate(at("a\nb|\nc"))))
        assertEquals("ab[b]", shown(editing.duplicate(at("a[b]"))))
        assertEquals("a\nb\n[a\nb]", shown(editing.duplicate(at("[a\nb]"))))
        assertEquals("a\nc|", shown(editing.deleteLines(at("a\nb|\nc"))))
        assertEquals("a|", shown(editing.deleteLines(at("a\nb|"))))
        assertEquals("b\na|\nc", shown(editing.moveLines(at("a|\nb\nc"), down = true)))
        assertEquals("a\nc|\nb", shown(editing.moveLines(at("a\nb\nc|"), down = false)))
        assertNull(editing.moveLines(at("a|\nb"), down = false))
        assertNull(editing.moveLines(at("a\nb|"), down = true))
    }

    @Test
    fun homeGoesToTheFirstCharacterThenTheLinesStart() {
        assertEquals("    |val a", shown(editing.home(at("    val |a"), extend = false)))
        assertEquals("|    val a", shown(editing.home(at("    |val a"), extend = false)))
        assertEquals("x\n    [val ]a", shown(editing.home(at("x\n    val |a"), extend = true)))
    }
}
