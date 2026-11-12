package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A code text with several selections, as JetBrains IDEs keep them: each from where it began to its caret (a
 * [TextRange]'s start to its end; collapsed, a caret alone), in the order they were made, the last the newest, which
 * the view keeps in sight.
 */
data class Carets(val text: String, val selections: List<TextRange>) {
    init {
        require(selections.isNotEmpty()) { "a text has a caret" }
    }

    /** The selection made last. */
    val newest: TextRange get() = selections.last()

    /** The text with its newest selection, as a one-caret editor holds it. */
    val value: TextFieldValue get() = TextFieldValue(text, newest)

    companion object {
        fun of(value: TextFieldValue) = Carets(value.text, listOf(value.selection))
    }
}

/**
 * What a code editor's keys do with several carets, as moonshine's editor and JetBrains IDEs do them: Alt+J selects the
 * word at the caret, then each next place the selection's text stands; Alt+Shift+J takes the last one away;
 * Ctrl+Alt+Shift+J selects every place; an edit is made at every caret; copying takes each selection's text, which
 * pastes back a piece at each.
 */
object CodeCarets {
    /**
     * Alt+J: where the newest selection is a caret, the word at it, and the presses after it match whole words
     * ([wholeWords] true); else the next place after it that its text stands and no selection holds, from the top once
     * past the end, its caret at the same end as the newest's. Unchanged where there is no such place.
     */
    fun selectNext(carets: Carets, wholeWords: Boolean): Carets {
        val newest = carets.newest
        if (newest.collapsed) {
            val word = wordAt(carets.text, newest.start) ?: return carets
            return carets.copy(selections = carets.selections.dropLast(1) + word)
        }
        val wanted = carets.text.substring(newest.min, newest.max)
        val found = places(carets.text, wanted, wholeWords).let { all ->
            all.firstOrNull { it >= newest.max && free(carets, it, wanted.length) } ?: all.firstOrNull { free(carets, it, wanted.length) }
        } ?: return carets
        val added = if (newest.start <= newest.end) TextRange(found, found + wanted.length) else TextRange(found + wanted.length, found)
        return carets.copy(selections = carets.selections + added)
    }

    /** Alt+Shift+J: the selection made last goes, where there is another. */
    fun unselectLast(carets: Carets): Carets = if (carets.selections.size < 2) carets else carets.copy(selections = carets.selections.dropLast(1))

    /**
     * Ctrl+Alt+Shift+J: every place the newest selection's text stands selected, the newest still last; for a caret, every
     * place the word at it stands, as a whole word.
     */
    fun selectAll(carets: Carets): Carets {
        val newest = carets.newest
        val word = if (newest.collapsed) wordAt(carets.text, newest.start) ?: return carets else newest
        val wanted = carets.text.substring(word.min, word.max)
        val others = places(carets.text, wanted, wholeWords = newest.collapsed).filter { it != word.min }.map { TextRange(it, it + wanted.length) }
        return carets.copy(selections = others + word)
    }

    /**
     * [edit] made at each selection, from the last in the text to the first, so each is made where its own text still
     * stands; each selection after it moves with what it put in or took out. Where [edit] is null for a selection, it is
     * left as it is. Selections that come to cover the same text become one.
     */
    fun editEach(carets: Carets, edit: (TextFieldValue) -> TextFieldValue?): Carets = editEachIndexed(carets) { _, value -> edit(value) }

    /**
     * Copying with several selections: what each takes, in the text's order: its text, or a caret's whole line, its line
     * break left out. They go to the clipboard a line each.
     */
    fun copied(carets: Carets): List<String> = carets.selections.sortedBy { it.min }.map { selection ->
        val range = if (selection.collapsed) lineRange(carets.text, selection.start, withBreak = false) else selection
        carets.text.substring(range.min, range.max)
    }

    /** Cutting with several selections: each one's text, or a caret's whole line with its break, taken out. */
    fun cut(carets: Carets): Carets = editEach(carets) { value ->
        val range = if (value.selection.collapsed) lineRange(value.text, value.selection.start, withBreak = true) else value.selection
        CodeEditing.replaced(value.text, range.min, range.max, "")
    }

    /**
     * Pasting what several selections copied ([copied]) at as many: each its own piece, in the text's order; null where
     * the counts differ, and the whole goes in at each.
     */
    fun pasteEach(carets: Carets, pieces: List<String>): Carets? {
        if (pieces.size != carets.selections.size) return null
        val rank = carets.selections.indices.sortedBy { carets.selections[it].min }
        return editEachIndexed(carets) { index, value ->
            CodeEditing.replaced(value.text, value.selection.min, value.selection.max, pieces[rank.indexOf(index)])
        }
    }

    private fun lineRange(text: String, offset: Int, withBreak: Boolean): TextRange {
        val end = CodeEditing.lineEnd(text, offset)
        return TextRange(CodeEditing.lineStart(text, offset), if (withBreak) (end + 1).coerceAtMost(text.length) else end)
    }

    private fun editEachIndexed(carets: Carets, edit: (Int, TextFieldValue) -> TextFieldValue?): Carets {
        val order = carets.selections.indices.sortedByDescending { carets.selections[it].min }
        var text = carets.text
        val after = carets.selections.toMutableList()
        for (index in order) {
            val made = edit(index, TextFieldValue(text, after[index])) ?: continue
            val change = TextChange.between(text, made.text)
            for (later in after.indices) {
                if (later != index && carets.selections[later].min >= carets.selections[index].max) after[later] = change.map(after[later])
            }
            after[index] = made.selection
            text = made.text
        }
        return Carets(text, after.distinct())
    }

    /** The word at [offset], a name's letters, digits and underscores, the caret at its end; null where none touches it. */
    fun wordAt(text: String, offset: Int): TextRange? {
        var start = offset
        var end = offset
        while (start > 0 && isWord(text[start - 1])) start--
        while (end < text.length && isWord(text[end])) end++
        return if (start == end) null else TextRange(start, end)
    }

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    /** Where [wanted] stands in [text], first to last, overlapping none before; as a whole word only where [wholeWords]. */
    private fun places(text: String, wanted: String, wholeWords: Boolean): List<Int> = buildList {
        if (wanted.isEmpty()) return@buildList
        var at = text.indexOf(wanted)
        while (at >= 0) {
            val whole = (at == 0 || !isWord(text[at - 1])) && (at + wanted.length == text.length || !isWord(text[at + wanted.length]))
            if (!wholeWords || whole) add(at)
            at = text.indexOf(wanted, at + wanted.length)
        }
    }

    private fun free(carets: Carets, at: Int, length: Int) = carets.selections.none { it.min < at + length && at < it.max || it.min == at }
}

/** [insert] in place of the text from [start] until [end]: the one change that turns one text into another. */
data class TextChange(val start: Int, val end: Int, val insert: String) {
    /** Where [offset] in the text before stands after; one within what was replaced goes to its end. */
    fun map(offset: Int): Int = when {
        offset <= start -> offset
        offset >= end -> offset - end + start + insert.length
        else -> start + insert.length
    }

    fun map(range: TextRange) = TextRange(map(range.start), map(range.end))

    companion object {
        fun between(old: String, new: String): TextChange {
            val prefix = old.commonPrefixWith(new).length
            val suffix = old.commonSuffixWith(new).length.coerceAtMost(minOf(old.length, new.length) - prefix)
            return TextChange(prefix, old.length - suffix, new.substring(prefix, new.length - suffix))
        }
    }
}
