package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Where a code text's strings and comments are, as its language's tokens tell, each the offsets of its characters. */
class Literals(val strings: List<IntRange> = emptyList(), val comments: List<IntRange> = emptyList()) {
    /** Whether a caret at [offset] stands inside a string or comment, past its first character. */
    fun surrounds(offset: Int): Boolean = (strings + comments).any { offset > it.first && offset <= it.last }

    /** Whether the character at [offset] is in a string or comment. */
    fun holds(offset: Int): Boolean = (strings + comments).any { offset in it }

    /** Whether the character at [offset] is the quote that ends a string. */
    fun endsString(offset: Int): Boolean = strings.any { it.last == offset && it.first != offset }

    companion object {
        val NONE = Literals()
    }
}

/**
 * What a code editor's keys do to its text and selection, as moonshine's code editor does them: typing pairs brackets
 * and quotes, types over the closing one and wraps a selection in brackets; a closing bracket typed on a line of
 * spaces lines up with the line its opening one is on; Enter keeps the line's indent and steps in after an opening
 * bracket; Backspace in a line's indent steps back to the indent stop before, and between a pair takes both; Tab and
 * Shift+Tab indent and outdent the lines a selection touches, Tab with no lines selected steps on to the next stop;
 * Ctrl+/ comments lines out and in; lines duplicate, go and move up and down; Home goes to the first character that is
 * not a space, then to the line's start. Each returns the text and selection after, or null where it does nothing and
 * the key is left to the text field.
 */
class CodeEditing(
    /** One indent's spaces. */
    val indent: String = "    ",
    /** The brackets that pair, opening then closing. */
    val brackets: String = "()[]{}",
    /** The quotes that pair. */
    val quotes: String = "\"'",
    val lineComment: String = "//",
) {
    private val indentSize get() = indent.length

    /** [typed] typed over the selection; null where it goes in as it is. */
    fun type(value: TextFieldValue, typed: Char, literals: Literals = Literals.NONE): TextFieldValue? {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val collapsed = value.selection.collapsed
        val closer = closerOf(typed)
        return when {
            collapsed && text.getOrNull(start) == typed && (closes(typed) || typed in quotes && literals.endsString(start)) ->
                TextFieldValue(text, TextRange(start + 1))
            closer != null && !collapsed -> {
                val inner = text.substring(start, end)
                replaced(text, start, end, "$typed$inner$closer", caretAt = 1 + inner.length, selectFrom = 1)
            }
            closer != null && closesNicely(text, end) -> replaced(text, start, end, "$typed$closer", caretAt = 1)
            typed in quotes && collapsed && quotePairs(text, start, typed, literals) -> replaced(text, start, end, "$typed$typed", caretAt = 1)
            closes(typed) && collapsed && text.substring(lineStart(text, start), start).isBlank() -> {
                val from = lineStart(text, start)
                replaced(text, from, start, indentOfOpening(text, from, typed, literals) + typed)
            }
            else -> null
        }
    }

    /**
     * Enter: a new line indented as the one it breaks, one indent further after an opening bracket; between a pair, the
     * closing one goes down onto a line of its own, the caret on the indented line between.
     */
    fun newLine(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val from = lineStart(text, start)
        val before = text.substring(from, start)
        val lead = text.substring(from, lineEnd(text, start)).takeWhile { it == ' ' || it == '\t' }.take(start - from)
        val after = text.substring(end, lineEnd(text, end))
        val rest = after.trimStart(' ', '\t')
        val to = end + after.length - rest.length
        if (!indentsAfter(before)) return replaced(text, start, to, "\n$lead")
        val inner = "\n$lead$indent"
        val closer = before.trimEnd().lastOrNull()?.let(::closerOf)
        return if (closer != null && rest.firstOrNull() == closer) {
            replaced(text, start, to, "$inner\n$lead", caretAt = inner.length)
        } else {
            replaced(text, start, to, inner)
        }
    }

    /**
     * Backspace in a line's indent steps back to the indent stop before; between an opening bracket or quote and its
     * closing one, both go. Null for any other, which the field deletes as it does.
     */
    fun backspace(value: TextFieldValue): TextFieldValue? {
        if (!value.selection.collapsed) return null
        val text = value.text
        val caret = value.selection.start
        if (caret == 0) return null
        val from = lineStart(text, caret)
        val before = text.substring(from, caret)
        if (before.length > 1 && before.all { it == ' ' }) return replaced(text, from + (before.length - 1) / indentSize * indentSize, caret, "")
        val previous = text[caret - 1]
        val paired = closerOf(previous) ?: previous.takeIf { it in quotes }
        return if (paired != null && text.getOrNull(caret) == paired) replaced(text, caret - 1, caret + 1, "") else null
    }

    /**
     * Tab: within a line, spaces in place of the selection up to the next indent stop; a selection over lines indents
     * each line it touches that is not blank.
     */
    fun indent(value: TextFieldValue): TextFieldValue? {
        val text = value.text
        val start = value.selection.min
        if ('\n' !in text.substring(start, value.selection.max)) {
            val column = visualColumn(text.substring(lineStart(text, start), start))
            return replaced(text, start, value.selection.max, " ".repeat(indentSize - column % indentSize))
        }
        return eachLine(value) { line -> if (line.isBlank()) line else indent + line }
    }

    /** Shift+Tab: each line the selection or caret touches steps back one indent, where it has one. */
    fun outdent(value: TextFieldValue): TextFieldValue? = eachLine(value) { line ->
        if (line.startsWith('\t')) line.substring(1) else line.substring(minOf(line.takeWhile { it == ' ' }.length, indentSize))
    }

    /**
     * Ctrl+/: comments out the lines the selection touches, at the shallowest indent among them; where every one is a
     * comment already, comments them in. Blank lines are left as they are.
     */
    fun toggleComment(value: TextFieldValue): TextFieldValue? {
        val lines = touchedLines(value).map { lineText(value.text, it) }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val commented = lines.all { it.trimStart().startsWith(lineComment) }
        val column = lines.minOf { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
        return eachLine(value) { line ->
            when {
                line.isBlank() -> line
                commented -> {
                    val at = line.indexOf(lineComment)
                    val spaced = if (line.startsWith("$lineComment ", at)) 1 else 0
                    line.substring(0, at) + line.substring(at + lineComment.length + spaced)
                }
                else -> line.substring(0, column) + "$lineComment " + line.substring(column)
            }
        }
    }

    /**
     * Ctrl+D: a selection within a line again after itself, the copy selected; else the lines the selection or caret
     * touches again below themselves, the selection going with the copy.
     */
    fun duplicate(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        if (start != end && '\n' !in text.substring(start, end)) return replaced(text, end, end, text.substring(start, end), selectFrom = 0)
        val lines = touchedLines(value)
        val from = lineStartOf(text, lines.first)
        val to = lineEnd(text, lineStartOf(text, lines.last))
        val block = "\n" + text.substring(from, to)
        val shift = block.length
        return TextFieldValue(text.substring(0, to) + block + text.substring(to), TextRange(value.selection.start + shift, value.selection.end + shift))
    }

    /** Ctrl+Shift+K: the lines the selection or caret touches go, the caret where it was on the line that comes up. */
    fun deleteLines(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val lines = touchedLines(value)
        val column = value.selection.end - lineStart(text, value.selection.end)
        val from = lineStartOf(text, lines.first)
        val to = lineEnd(text, lineStartOf(text, lines.last))
        val (cutFrom, cutTo) = when {
            to < text.length -> from to to + 1
            from > 0 -> from - 1 to to
            else -> from to to
        }
        val left = text.removeRange(cutFrom, cutTo)
        val at = lineStart(left, cutFrom.coerceAtMost(left.length))
        return TextFieldValue(left, TextRange(at + column.coerceAtMost(lineEnd(left, at) - at)))
    }

    /** Alt+Up and Alt+Down: the lines the selection or caret touches move one line up or down, the selection with them. */
    fun moveLines(value: TextFieldValue, down: Boolean): TextFieldValue? {
        val text = value.text
        val lines = touchedLines(value)
        val from = lineStartOf(text, lines.first)
        val to = lineEnd(text, lineStartOf(text, lines.last))
        val block = text.substring(from, to)
        return if (down) {
            if (to >= text.length) return null
            val next = text.substring(to + 1, lineEnd(text, to + 1))
            val shift = next.length + 1
            TextFieldValue(text.replaceRange(from, to + 1 + next.length, "$next\n$block"), TextRange(value.selection.start + shift, value.selection.end + shift))
        } else {
            if (from == 0) return null
            val previousStart = lineStart(text, from - 1)
            val previous = text.substring(previousStart, from - 1)
            val shift = previous.length + 1
            TextFieldValue(text.replaceRange(previousStart, to, "$block\n$previous"), TextRange(value.selection.start - shift, value.selection.end - shift))
        }
    }

    /** Home: to the line's first character that is not a space, or from there to the line's start; [extend] selects. */
    fun home(value: TextFieldValue, extend: Boolean): TextFieldValue {
        val text = value.text
        val caret = value.selection.end
        val from = lineStart(text, caret)
        val first = from + text.substring(from, lineEnd(text, caret)).takeWhile { it == ' ' || it == '\t' }.length
        val to = if (caret == first) from else first
        return value.copy(selection = TextRange(if (extend) value.selection.start else to, to))
    }

    private fun indentsAfter(before: String): Boolean = before.trimEnd().lastOrNull()?.let { closerOf(it) != null } == true

    /** The indent of the line the bracket a [closing] one typed at [lineFrom] closes is on; one indent less where none is. */
    private fun indentOfOpening(text: String, lineFrom: Int, closing: Char, literals: Literals): String {
        var depth = 0
        for (at in lineFrom - 1 downTo 0) {
            val c = text[at]
            if (literals.holds(at)) continue
            when {
                closes(c) -> depth++
                closerOf(c) != null && depth > 0 -> depth--
                closerOf(c) == closing -> return leadOf(text, lineStart(text, at))
                closerOf(c) != null -> break
            }
        }
        return leadOf(text, lineFrom).dropLast(indentSize)
    }

    private fun leadOf(text: String, lineFrom: Int) = text.substring(lineFrom, lineEnd(text, lineFrom)).takeWhile { it == ' ' || it == '\t' }

    private fun closerOf(c: Char): Char? = brackets.chunked(2).firstOrNull { it[0] == c }?.get(1)

    private fun closes(c: Char): Boolean = brackets.chunked(2).any { it[1] == c }

    /** Whether an opening bracket or quote typed at [offset] gets its closing one: only before a space, the line's end or a closer. */
    private fun closesNicely(text: String, offset: Int): Boolean {
        val next = text.getOrNull(offset) ?: return true
        return next.isWhitespace() || closes(next) || next == ',' || next == ';'
    }

    /** Whether a quote typed at [offset] gets its closing one: not after a name, a quote or an escape, nor inside a string or comment. */
    private fun quotePairs(text: String, offset: Int, quote: Char, literals: Literals): Boolean {
        val previous = text.getOrNull(offset - 1)
        if (previous != null && (previous.isLetterOrDigit() || previous == '_' || previous == quote || previous == '\\')) return false
        return closesNicely(text, offset) && !literals.surrounds(offset)
    }

    /** The lines the selection touches, by index: one ending at a line's start leaves that line out. */
    private fun touchedLines(value: TextFieldValue): IntRange {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val first = lineIndex(text, start)
        val last = lineIndex(text, end).let { if (end > start && end == lineStart(text, end)) it - 1 else it }
        return first..last
    }

    /**
     * Each line the selection touches, changed by [change]; null where none changes. The selection's ends keep to their
     * text, moving with what goes in or out before them on their lines.
     */
    private fun eachLine(value: TextFieldValue, change: (String) -> String): TextFieldValue? {
        val old = value.text.split('\n')
        val touched = touchedLines(value)
        val new = old.mapIndexed { index, line -> if (index in touched) change(line) else line }
        if (new == old) return null
        val oldStarts = old.runningFold(0) { at, line -> at + line.length + 1 }
        val newStarts = new.runningFold(0) { at, line -> at + line.length + 1 }
        fun moved(offset: Int): Int {
            val line = oldStarts.indexOfLast { it <= offset }.coerceAtMost(old.lastIndex)
            val column = offset - oldStarts[line]
            val prefix = old[line].commonPrefixWith(new[line]).length
            val delta = new[line].length - old[line].length
            val to = when {
                column < prefix -> column
                delta < 0 && column < prefix - delta -> prefix
                else -> column + delta
            }
            return newStarts[line] + to.coerceIn(0, new[line].length)
        }
        return TextFieldValue(new.joinToString("\n"), TextRange(moved(value.selection.start), moved(value.selection.end)))
    }

    companion object {
        const val TAB_SIZE = 4

        /** [text] with [from] until [to] replaced by [insert], the caret [caretAt] into it, selecting from [selectFrom] where given. */
        fun replaced(text: String, from: Int, to: Int, insert: String, caretAt: Int = insert.length, selectFrom: Int? = null): TextFieldValue =
            TextFieldValue(text.replaceRange(from, to, insert), TextRange(from + (selectFrom ?: caretAt), from + caretAt))

        fun lineStart(text: String, offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

        fun lineEnd(text: String, offset: Int): Int = text.indexOf('\n', offset).let { if (it < 0) text.length else it }

        private fun lineIndex(text: String, offset: Int): Int = text.substring(0, offset).count { it == '\n' }

        private fun lineStartOf(text: String, line: Int): Int =
            if (line == 0) 0 else generateSequence(text.indexOf('\n')) { text.indexOf('\n', it + 1) }.elementAt(line - 1) + 1

        private fun lineText(text: String, line: Int): String = lineStartOf(text, line).let { text.substring(it, lineEnd(text, it)) }

        /** How far into a line [before] reaches, in character cells: tabs reach their next stop. */
        fun visualColumn(before: String): Int = before.fold(0) { cells, c -> if (c == '\t') (cells / TAB_SIZE + 1) * TAB_SIZE else cells + 1 }
    }
}
