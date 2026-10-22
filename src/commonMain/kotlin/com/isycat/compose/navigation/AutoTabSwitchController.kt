package com.isycat.compose.navigation

class AutoTabSwitchController<S, T>(
    private val determineTab: (S) -> T?,
    private val isNewState: (old: S?, new: S) -> Boolean = { old, new -> old != new }
) {
    private var prevState: S? = null

    fun getTabToSwitchTo(currentState: S): T? {
        if (!isNewState(prevState, currentState)) {
            prevState = currentState
            return null
        }

        val tab = determineTab(currentState)
        prevState = currentState
        return tab
    }

    fun reset() {
        prevState = null
    }
}
