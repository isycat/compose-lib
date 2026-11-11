package com.isycat.compose.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A code editor's undo and redo, as code editors keep them: each edit one step, the characters typed in a run on one
 * line one step, the selection put back as it was before the step undone. Keeps the last [limit] steps. Where the text
 * was changed elsewhere since, its steps no longer apply and are forgotten.
 */
class CodeHistory(private val limit: Int = LIMIT) {
    private class Step(val before: TextFieldValue, val after: TextFieldValue, val typing: Boolean)

    private val undos = ArrayDeque<Step>()
    private val redos = ArrayDeque<Step>()

    /** The edit from [before] to [after]; a change of selection alone is none. */
    fun record(before: TextFieldValue, after: TextFieldValue) {
        if (before.text == after.text) return
        redos.clear()
        val typing = typed(before, after)
        val last = undos.lastOrNull()
        if (typing && last != null && last.typing && last.after == before) {
            undos[undos.lastIndex] = Step(last.before, after, typing = true)
            return
        }
        undos.addLast(Step(before, after, typing))
        if (undos.size > limit) undos.removeFirst()
    }

    /** The text as it was before the last step, from [current]; null where there is none to undo. */
    fun undo(current: TextFieldValue): TextFieldValue? = step(current, undos, redos)?.before

    /** The text as it was after the step last undone, from [current]; null where there is none to redo. */
    fun redo(current: TextFieldValue): TextFieldValue? = step(current, redos, undos)?.after

    private fun step(current: TextFieldValue, from: ArrayDeque<Step>, to: ArrayDeque<Step>): Step? {
        val step = from.lastOrNull() ?: return null
        val at = if (from === undos) step.after else step.before
        if (at.text != current.text) {
            undos.clear()
            redos.clear()
            return null
        }
        from.removeLast()
        to.addLast(step)
        return step
    }

    /** Whether [after] is [before] with one character typed at its caret, on the same line. */
    private fun typed(before: TextFieldValue, after: TextFieldValue): Boolean {
        val caret = before.selection
        if (!caret.collapsed || after.selection != TextRange(caret.start + 1) || after.text.length != before.text.length + 1) return false
        return after.text[caret.start] != '\n' &&
            after.text.startsWith(before.text.substring(0, caret.start)) && after.text.endsWith(before.text.substring(caret.start))
    }

    private companion object {
        const val LIMIT = 1000
    }
}
