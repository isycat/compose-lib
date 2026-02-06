package com.isycat.compose.input

import java.awt.event.KeyEvent

data class TabCycleShortcutMatch(
    val delta: Int?,
)

fun awtTabCycleMatchOrNull(event: java.awt.AWTEvent): TabCycleShortcutMatch? {
    val keyEvent = event as? KeyEvent ?: return null

    val shouldCycle = keyEvent.id == KeyEvent.KEY_PRESSED
    val shouldConsume = keyEvent.id == KeyEvent.KEY_PRESSED || keyEvent.id == KeyEvent.KEY_RELEASED
    if (!shouldConsume) return null

    val hasCtrlOrMeta = keyEvent.isControlDown || keyEvent.isMetaDown
    val hasCtrl = keyEvent.isControlDown
    val hasAlt = keyEvent.isAltDown
    val hasShift = keyEvent.isShiftDown

    val delta = when {
        // Ctrl/Cmd + Tab (and Shift variant)
        hasCtrlOrMeta && keyEvent.keyCode == KeyEvent.VK_TAB -> if (hasShift) -1 else 1

        // Ctrl + Alt + Left/Right
        hasCtrl && hasAlt && !hasShift && keyEvent.keyCode == KeyEvent.VK_LEFT -> -1
        hasCtrl && hasAlt && !hasShift && keyEvent.keyCode == KeyEvent.VK_RIGHT -> 1

        // Ctrl + Alt + L/R (vim-ish)
        hasCtrl && hasAlt && !hasShift && keyEvent.keyCode == KeyEvent.VK_L -> -1
        hasCtrl && hasAlt && !hasShift && keyEvent.keyCode == KeyEvent.VK_R -> 1

        else -> null
    }

    return delta
        ?.let { TabCycleShortcutMatch(delta = if (shouldCycle) it else null) }
}
