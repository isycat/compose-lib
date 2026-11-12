package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/** Several carets, as moonshine's editor and JetBrains IDEs keep them. */
class CodeCaretsTest {
    private val editing = CodeEditing()
    private val text = "val size = 1\nval sizes = size + size\n"

    private fun caretAt(offset: Int) = Carets(text, listOf(TextRange(offset)))

    @Test
    fun altJSelectsTheWordThenEachNextPlaceItStands() {
        val word = CodeCarets.selectNext(caretAt(6), wholeWords = false)
        assertEquals(listOf(TextRange(4, 8)), word.selections, "the word at the caret")
        val next = CodeCarets.selectNext(word, wholeWords = true)
        assertEquals(TextRange(25, 29), next.newest, "the next place it stands as a whole word, past sizes")
        val third = CodeCarets.selectNext(next, wholeWords = true)
        assertEquals(TextRange(32, 36), third.newest)
        assertEquals(third, CodeCarets.selectNext(third, wholeWords = true), "every place is taken")
        assertEquals(TextRange(17, 21), CodeCarets.selectNext(word, wholeWords = false).newest, "any place, where it began from a selection")
        assertEquals(next, CodeCarets.unselectLast(third), "Alt+Shift+J takes the last one away")
    }

    @Test
    fun ctrlAltShiftJSelectsEveryPlace() {
        val all = CodeCarets.selectAll(caretAt(6))
        assertEquals(listOf(TextRange(25, 29), TextRange(32, 36), TextRange(4, 8)), all.selections, "the newest stays last")
    }

    @Test
    fun anEditIsMadeAtEveryCaret() {
        val all = CodeCarets.selectAll(caretAt(6))
        val typed = CodeCarets.editEach(all) { CodeEditing.replaced(it.text, it.selection.min, it.selection.max, "width") }
        assertEquals("val width = 1\nval sizes = width + width\n", typed.text)
        assertEquals(listOf(TextRange(31), TextRange(39), TextRange(9)), typed.selections, "each caret after what it typed")
        val paired = CodeCarets.editEach(typed) { editing.type(it, '(') }
        assertEquals("val width() = 1\nval sizes = width() + width()\n", paired.text)
        val lines = CodeCarets.editEach(Carets("a\nb", listOf(TextRange(1), TextRange(3)))) { editing.newLine(it) }
        assertEquals("a\n\nb\n", lines.text)
        assertEquals(listOf(TextRange(2), TextRange(5)), lines.selections)
    }

    @Test
    fun copyingTakesEachSelectionAndPastesAPieceAtEach() {
        val two = Carets("ab\ncd\nef", listOf(TextRange(5, 4), TextRange(0, 1)))
        assertEquals(listOf("a", "d"), CodeCarets.copied(two), "in the text's order")
        assertEquals(listOf("ab", "ef"), CodeCarets.copied(Carets("ab\ncd\nef", listOf(TextRange(7), TextRange(1)))), "a caret's line")
        assertEquals("b\nc\nef", CodeCarets.cut(two).text)
        assertEquals("cd\n", CodeCarets.cut(Carets("ab\ncd\nef", listOf(TextRange(7), TextRange(1)))).text, "a caret's line and its break")
        val pasted = CodeCarets.pasteEach(two, listOf("X", "Y"))
        assertEquals("Xb\ncY\nef", pasted?.text)
        assertEquals(null, CodeCarets.pasteEach(two, listOf("X")))
    }
}
